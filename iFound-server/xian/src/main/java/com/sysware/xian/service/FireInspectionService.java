package com.sysware.xian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.xian.domain.DispatchTask;
import com.sysware.xian.domain.FireInspectionRecord;
import com.sysware.xian.swcloud.domain.AjaxResultVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消防巡查异常监控服务
 * <p>
 * 每5分钟调用上游 /digitalStation/getData2(type=xfxc) 拉取当日消防巡查数据，
 * 按设备(ID)取最新一条记录定性（RESULT=1正常，其他异常）：
 * 1. 最新为异常：无任务则生成 DispatchTask(taskType=hint)；已有任务且出现更新的异常记录则向problemDesc累加异常说明；
 * 2. 最新为正常：完结该设备的未完成巡查任务（恢复闭环）。
 * <p>
 * 已生成任务的异常设备永久登记于 Redis Hash xfxc_abnormal（设备可能一周才巡检一次，登记需跨周存活，
 * 条目随任务生命周期增删：任务生成/复用时写入，设备恢复或人工完结后恢复正常时删除），
 * value为 {taskDispatchTaskId, createTime, appendCount}；
 * DB按 businessDomain+decisionData+resultExecuteStatus=0 查重兜底，缓存丢失或跨周时复用未完成任务，不会重复生成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FireInspectionService {

    private static final String BUSINESS_DOMAIN = "消防巡查";
    /** Redis缓存key：异常设备任务登记表，永久key；条目随任务生命周期增删，无有效任务时由恢复检查清除 */
    private static final String CACHE_KEY = "xfxc_abnormal";
    /** problemDesc最多累加的异常记录条数，防止持续异常刷爆问题描述 */
    private static final int MAX_PROBLEM_DESC_APPENDS = 30;

    /** 异常设备处理结果：新建任务 */
    private static final int HANDLE_CREATED = 0;
    /** 异常设备处理结果：更新已有任务 */
    private static final int HANDLE_UPDATED = 1;
    /** 异常设备处理结果：无变化跳过 */
    private static final int HANDLE_SKIPPED = 2;
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final DispatchTaskService dispatchTaskService;

    @Value("${ai.problem-url}")
    private String problemBaseUrl;

    /**
     * 已生成任务设备的缓存对象（Redis Hash value，JSON序列化）
     */
    @Data
    public static class TaskedDevice {
        /** 生成的DispatchTask的id */
        private String taskDispatchTaskId;
        /** 最近一次已处理的异常记录CREATE_TIME（ISO格式），用于判断是否有更新的异常数据 */
        private String createTime;
        /** problemDesc已累加的异常条数 */
        private int appendCount;
    }

    /**
     * 定时入口：每5分钟执行一次消防巡查异常检查（由ScheduledService调度）
     */
    public void scheduledCheckFireInspection() {
        log.info("开始定时检查消防巡查异常...");
        try {
            checkFireInspection();
        } catch (Exception e) {
            log.error("定时检查消防巡查异常失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 消防巡查异常检查核心逻辑
     */
    public void checkFireInspection() {
        String today = LocalDate.now().toString();
        List<FireInspectionRecord> records = fetchRecords(today);
        if (records == null) {
            // 上游查询失败，跳过本轮，保持现有任务状态
            return;
        }

        // 按设备聚合，取最新一条记录作为设备当前状态
        Map<String, FireInspectionRecord> latestByDevice = new LinkedHashMap<>();
        for (FireInspectionRecord record : records) {
            if (record.getId() == null || record.getId().isBlank()) {
                continue;
            }
            FireInspectionRecord current = latestByDevice.get(record.getId());
            if (current == null || sortTime(record).isAfter(sortTime(current))) {
                latestByDevice.put(record.getId(), record);
            }
        }
        log.info("消防巡查当日数据{}条，涉及设备{}台", records.size(), latestByDevice.size());

        // 1. 最新状态为异常的设备：生成或更新任务
        int created = 0;
        int updated = 0;
        for (FireInspectionRecord latest : latestByDevice.values()) {
            if (latest.isNormal()) {
                continue;
            }
            try {
                int handled = handleAbnormalDevice(latest);
                if (handled == HANDLE_CREATED) {
                    created++;
                } else if (handled == HANDLE_UPDATED) {
                    updated++;
                }
            } catch (Exception e) {
                log.error("消防巡查异常设备处理失败: deviceId={}, name={}", latest.getId(), latest.getName(), e);
            }
        }

        // 2. 恢复闭环：以DB未完成任务为准检查设备是否已恢复正常（覆盖跨周任务与缓存丢失场景）
        int recovered = recoverDevices(latestByDevice);

        log.info("消防巡查检查完成: 新建任务{}条, 更新任务{}条, 恢复完结{}条", created, updated, recovered);
    }

    /**
     * 拉取当日消防巡查数据
     *
     * @return 记录列表；上游查询失败返回null（区别于"当天无数据"的空列表）
     */
    private List<FireInspectionRecord> fetchRecords(String dateStr) {
        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
        formParams.add("dateStr", dateStr);
        formParams.add("type", "xfxc");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formParams, headers);

        String url = problemBaseUrl + "/digitalStation/getData2";
        try {
            ResponseEntity<AjaxResultVO> response = restTemplate.exchange(url, HttpMethod.POST, entity, AjaxResultVO.class);
            AjaxResultVO body = response.getBody();
            if (body == null || !body.isOk()) {
                log.warn("查询消防巡查数据失败: dateStr={}, msg={}", dateStr, body != null ? body.getMsg() : "无响应");
                return null;
            }
            Object dataObj = body.get("data");
            if (!(dataObj instanceof List<?> list)) {
                return new ArrayList<>();
            }
            List<FireInspectionRecord> records = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> raw) {
                    @SuppressWarnings("unchecked")
                    FireInspectionRecord record = FireInspectionRecord.from((Map<String, Object>) raw);
                    if (record.getId() != null) {
                        records.add(record);
                    }
                }
            }
            return records;
        } catch (Exception e) {
            log.error("查询消防巡查数据异常: dateStr={}", dateStr, e);
            return null;
        }
    }

    /**
     * 处理最新状态为异常的设备
     *
     * @return 处理结果：HANDLE_CREATED-新建任务，HANDLE_UPDATED-更新已有任务，HANDLE_SKIPPED-无变化
     */
    private int handleAbnormalDevice(FireInspectionRecord latest) {
        String deviceId = latest.getId();
        TaskedDevice cached = readCache(redisTemplate.opsForHash().get(CACHE_KEY, deviceId));

        if (cached != null) {
            // 已有登记：仅当出现比上次已处理的更新异常记录时才处理
            LocalDateTime latestTime = latest.createTimeValue();
            LocalDateTime cachedTime = parseQuiet(cached.getCreateTime());
            if (latestTime == null || (cachedTime != null && !latestTime.isAfter(cachedTime))) {
                return HANDLE_SKIPPED;
            }
            String taskId = cached.getTaskDispatchTaskId();
            DispatchTask existing = taskId == null ? null : dispatchTaskService.getById(taskId);
            if (existing != null
                    && (existing.getResultExecuteStatus() == null || existing.getResultExecuteStatus() == 0)) {
                // 任务未完结：累加异常说明
                DispatchTask update = new DispatchTask();
                update.setId(taskId);
                update.setDataSnapshot(toJson(latest));
                if (cached.getAppendCount() < MAX_PROBLEM_DESC_APPENDS) {
                    String baseDesc = existing.getProblemDesc() == null ? buildProblemDesc(latest) : existing.getProblemDesc();
                    update.setProblemDesc(baseDesc + appendSegment(latest));
                    cached.setAppendCount(cached.getAppendCount() + 1);
                } else {
                    // 达到累加上限后仅刷新数据快照，防止problemDesc无限增长
                    log.info("消防巡查设备[{}]异常描述已达上限{}条，仅更新快照", latest.getName(), MAX_PROBLEM_DESC_APPENDS);
                }
                dispatchTaskService.updateById(update);
                writeCache(deviceId, cacheOf(taskId, latest, cached.getAppendCount()));
                log.info("消防巡查设备[{}]异常持续，累加任务{}描述: {}", latest.getName(), taskId, latest.getRemark());
                return HANDLE_UPDATED;
            }
            // 任务已完结（人工处理过）但设备仍异常：清除登记并按这条新异常记录重建任务。
            // 已处理过的旧记录因createTime闸门不会重复触发，重建只发生在新巡查数据到达时
            log.info("消防巡查设备[{}]任务{}已完结但设备仍异常，清除登记并重建任务", latest.getName(), taskId);
            redisTemplate.opsForHash().delete(CACHE_KEY, deviceId);
        }

        // 无登记：DB查重兜底（缓存丢失/跨周未完结），有未完成任务则复用
        DispatchTask openTask = findOpenTask(deviceId);
        if (openTask != null) {
            DispatchTask update = new DispatchTask();
            update.setId(openTask.getId());
            String baseDesc = openTask.getProblemDesc() == null ? buildProblemDesc(latest) : openTask.getProblemDesc();
            update.setProblemDesc(baseDesc + appendSegment(latest));
            update.setDataSnapshot(toJson(latest));
            dispatchTaskService.updateById(update);
            writeCache(deviceId, cacheOf(openTask.getId(), latest, 0));
            log.info("消防巡查设备[{}]存在未完成任务，复用并累加异常: taskId={}", latest.getName(), openTask.getId());
            return HANDLE_UPDATED;
        }
        // 新建任务
        DispatchTask task = new DispatchTask();
        task.setSubareaCid("0.001.001.001-0");
        task.setCreatorAgentId("commander");
        task.setCreatorAgentName("系统Agent");
        task.setBusinessDomain(BUSINESS_DOMAIN);
        task.setLevel("低");
        task.setProblemDesc(buildProblemDesc(latest));
        task.setDataSnapshot(toJson(latest));
        task.setDecisionContent("及时补漏/更换");
        task.setTargetAgentId("commander");
        task.setTargetAgentName("系统Agent");
        task.setTaskType("hint");
        task.setDecisionData(deviceId);
        task.setResultExecuteStatus(0);
        DispatchTask saved = dispatchTaskService.createTask(task);
        writeCache(deviceId, cacheOf(saved.getId(), latest, 0));
        log.info("消防巡查设备[{}]发现异常，生成任务{}: {}", latest.getName(), saved.getId(), latest.getRemark());
        return HANDLE_CREATED;
    }

    /**
     * 恢复闭环：以DB未完成任务为准，其设备当日最新记录为正常时完结任务；
     * 并清理"任务已完结（如人工处理）但登记仍在"的残留缓存，保证永久key下缓存不无限累积。
     * <p>
     * 以DB未完成任务为准而非仅依赖缓存，与设备故障恢复检查(checkDeviceFaultRecovery)模式一致，
     * 可覆盖跨周任务与缓存丢失场景。当日无数据的设备保持任务打开，待后续巡查数据确认。
     *
     * @return 完结的任务数
     */
    private int recoverDevices(Map<String, FireInspectionRecord> latestByDevice) {
        int recovered = 0;
        // 1. 未完成任务：设备当日最新记录为正常 → 完结并清除登记
        List<DispatchTask> openTasks = dispatchTaskService.list(new LambdaQueryWrapper<DispatchTask>()
                .eq(DispatchTask::getBusinessDomain, BUSINESS_DOMAIN)
                .eq(DispatchTask::getResultExecuteStatus, "0"));
        for (DispatchTask task : openTasks) {
            String deviceId = task.getDecisionData() == null ? null : task.getDecisionData().trim();
            if (deviceId == null || deviceId.isBlank()) {
                continue;
            }
            FireInspectionRecord latest = latestByDevice.get(deviceId);
            if (latest == null || !latest.isNormal()) {
                // 当日无巡查数据或仍异常，任务保持打开
                continue;
            }
            try {
                DispatchTask update = new DispatchTask();
                update.setId(task.getId());
                update.setResultExecuteStatus(1);
                update.setResultExecuteUser("系统Agent");
                update.setResultData(toJson(latest));
                update.setResultDesc("消防巡查后已恢复该设备");
                update.setCompleteTime(LocalDateTime.now());
                dispatchTaskService.updateById(update);
                redisTemplate.opsForHash().delete(CACHE_KEY, deviceId);
                recovered++;
                log.info("消防巡查设备[{}]已恢复正常，完结任务{}", latest.getName(), task.getId());
            } catch (Exception e) {
                log.error("消防巡查恢复完结任务失败: taskId={}, deviceId={}", task.getId(), deviceId, e);
            }
        }

        // 2. 残留登记清理：设备已确认恢复正常、但登记仍指向已完结任务的，直接清除
        try {
            for (Map.Entry<Object, Object> entry : redisTemplate.opsForHash().entries(CACHE_KEY).entrySet()) {
                String deviceId = String.valueOf(entry.getKey());
                FireInspectionRecord latest = latestByDevice.get(deviceId);
                if (latest == null || !latest.isNormal()) {
                    continue;
                }
                redisTemplate.opsForHash().delete(CACHE_KEY, deviceId);
                log.info("消防巡查设备[{}]已恢复正常，清除残留登记", latest.getName());
            }
        } catch (Exception e) {
            log.error("消防巡查缓存残留清理失败", e);
        }
        return recovered;
    }

    /**
     * 查询设备的未完成巡查任务（DB兜底查重，result_execute_status列为varchar，按字符串"0"匹配）
     */
    private DispatchTask findOpenTask(String deviceId) {
        return dispatchTaskService.getOne(new LambdaQueryWrapper<DispatchTask>()
                .eq(DispatchTask::getBusinessDomain, BUSINESS_DOMAIN)
                .eq(DispatchTask::getDecisionData, deviceId)
                .eq(DispatchTask::getResultExecuteStatus, "0")
                .last("LIMIT 1"), false);
    }

    /** 任务问题描述初始格式 */
    private String buildProblemDesc(FireInspectionRecord record) {
        return "消防巡查：" + nvl(record.getName())+ "，"
                 + nvl(record.getRemark())+ " ，"
                + "位置：" + nvl(record.getLocation())+ " 。" ;
    }

    /** problemDesc累加段：异常说明+巡查时间 */
    private String appendSegment(FireInspectionRecord record) {
        return "；" + nvl(record.getRemark()) + "（" + displayTime(record.getCreateTime()) + "）";
    }

    private TaskedDevice cacheOf(String taskId, FireInspectionRecord record, int appendCount) {
        TaskedDevice cached = new TaskedDevice();
        cached.setTaskDispatchTaskId(taskId);
        cached.setCreateTime(record.getCreateTime());
        cached.setAppendCount(appendCount);
        return cached;
    }

    private TaskedDevice readCache(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value.toString(), TaskedDevice.class);
        } catch (Exception e) {
            log.warn("消防巡查缓存解析失败，按无缓存处理: {}", value);
            return null;
        }
    }

    private void writeCache(String deviceId, TaskedDevice cached) {
        try {
            redisTemplate.opsForHash().put(CACHE_KEY, deviceId, objectMapper.writeValueAsString(cached));
        } catch (Exception e) {
            log.error("消防巡查缓存写入失败: deviceId={}", deviceId, e);
        }
    }

    /** 上游原始数据对象JSON，用于dataSnapshot/resultData存档 */
    private String toJson(FireInspectionRecord record) {
        try {
            return objectMapper.writeValueAsString(record.getRaw() != null ? record.getRaw() : record);
        } catch (Exception e) {
            log.warn("消防巡查数据序列化失败: deviceId={}", record.getId());
            return null;
        }
    }

    /** 排序用时间，解析失败按最早处理（保底取到一条记录） */
    private LocalDateTime sortTime(FireInspectionRecord record) {
        LocalDateTime time = record.createTimeValue();
        return time != null ? time : LocalDateTime.MIN;
    }

    private LocalDateTime parseQuiet(String isoTime) {
        if (isoTime == null || isoTime.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(isoTime.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** ISO时间转展示格式（截断到秒），解析失败返回原文 */
    private String displayTime(String isoTime) {
        LocalDateTime time = parseQuiet(isoTime);
        return time != null ? time.format(DISPLAY_TIME) : String.valueOf(isoTime);
    }

    private String nvl(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
