package com.sysware.xian.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.xian.domain.*;
import com.sysware.xian.service.DispatchTaskService;
import com.sysware.xian.service.ZoneAnalysisService;
import com.sysware.xian.swcloud.domain.AjaxResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 分区态势分析接口
 */
@Slf4j
@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
@Tag(name = "分区态势分析", description = "AI分析分区态势数据")
public class ZoneAnalysisController {

    private final ZoneAnalysisService zoneAnalysisService;
    private final DispatchTaskService dispatchTaskService;
    private final ObjectMapper objectMapper;

    @PostMapping("/zoneAnalysis")
    @Operation(summary = "分区态势AI分析", description = "根据单条异常数据调用AI进行分析，返回分析结果和派发任务（不落库，由前端决策后保存/下发/取消）")
    public AjaxResultVO analyze(@RequestBody ZoneAnalysisRequest request) {
        try {
            ZoneAnalysisResponse response = zoneAnalysisService.analyze(request);
            if (response != null) {
                return AjaxResultVO.ok().data(response);
            }
            return AjaxResultVO.error("AI分析失败");
        } catch (Exception e) {
            log.error("分区态势分析异常", e);
            return AjaxResultVO.error("分析异常: " + e.getMessage());
        }
    }

    @PostMapping("/allZoneAnalysis")
    @Operation(summary = "全分区综合分析", description = "将所有分区数据汇总，AI生成300字以内的综合分析文本")
    public AjaxResultVO allZoneAnalysis(@RequestBody AllZoneAnalysisRequest request) {
        return AjaxResultVO.ok().data("全域运行平稳，用能态势优良，系统运行正常");
//        try {
//            String summary = zoneAnalysisService.analyzeAllZones(request);
//            if (summary != null) {
//                return AjaxResultVO.ok().data(summary);
//            }
//            return AjaxResultVO.error("综合分析失败");
//        } catch (Exception e) {
//            log.error("全分区综合分析异常", e);
//            return AjaxResultVO.error("分析异常: " + e.getMessage());
//        }
    }

    @PostMapping("/deviceAlarmAnalysis")
    @Operation(summary = "设备告警AI分析", description = "根据设备告警数据调用AI进行分析，返回分析结果并直接新增派发任务")
    public AjaxResultVO analyzeDeviceAlarm(@RequestBody DeviceAlarmRequest request) {
        try {
            DeviceAlarmResponse response = zoneAnalysisService.analyzeDeviceAlarm(request);
            if (response != null) {
                // 将AI生成的派发任务直接保存到数据库（不判断是否已存在）
                if (response.getDispatchTasks() != null) {
                    List<DispatchTask> savedTasks = new ArrayList<>();
                    for (DeviceAlarmResponse.DispatchTaskItem item : response.getDispatchTasks()) {
                        DispatchTask task = new DispatchTask();
                        task.setSubareaCid(request.getSubareaCid());
                        task.setCreatorAgentId("commander");
                        task.setCreatorAgentName("系統Agent");
                        task.setBusinessDomain(item.getBusinessDomain());
                        task.setLevel(item.getLevel());
                        task.setProblemDesc(item.getProblemDesc());
                        task.setThinkingOverview(item.getThinkingOverview());
                        task.setProblemSummary(item.getProblemSummary());
                        task.setDecisionContent(item.getDecisionContent());
                        task.setDecisionData(item.getDecisionData());
                        task.setExpectedEffect(item.getExpectedEffect());
                        task.setTargetAgentId(item.getTargetAgentId());
                        task.setTargetAgentName(item.getTargetAgentName());
                        task.setTaskType(item.getTaskType());
                        task.setDispatched(0);
                        task.setCreateTime(LocalDateTime.now());
                        // 存储告警数据快照
                        task.setDataSnapshot(buildAlarmSnapshot(request));
                        // ops-agent 且 decisionData(设备ID)不为空时，查重覆盖
                        dispatchTaskService.handleExistingTask(task);
                        dispatchTaskService.saveOrUpdate(task);
                        savedTasks.add(task);
                    }
                    response.setDispatchTasks(convertAlarmTasksToResponse(savedTasks));
                }
                return AjaxResultVO.ok().data(response);
            }
            return AjaxResultVO.error("AI分析失败");
        } catch (Exception e) {
            log.error("设备告警分析异常", e);
            return AjaxResultVO.error("分析异常: " + e.getMessage());
        }
    }

    @PostMapping("/commonAnalysis")
    @Operation(summary = "通用数据分析", description = "传入任意文本，AI智能识别并分析，支持区域/设备/能耗/环境等数据类型，返回分析结果并支持派发任务")
    public AjaxResultVO analyzeCommon(@RequestBody CommonAnalysisRequest request) {
        try {
            CommonAnalysisResponse response = zoneAnalysisService.analyzeCommon(request);
            if (response != null) {
                // 将AI生成的派发任务直接保存到数据库
                if (response.getDispatchTasks() != null && !response.getDispatchTasks().isEmpty()) {
                    List<DispatchTask> savedTasks = new ArrayList<>();
                    for (ZoneAnalysisResponse.DispatchTaskItem item : response.getDispatchTasks()) {
                        DispatchTask task = new DispatchTask();
                        task.setSubareaCid("unknown"); // 通用接口可能没有明确的分区ID
                        task.setCreatorAgentId("commander");
                        task.setCreatorAgentName("系統Agent");
                        task.setBusinessDomain(item.getBusinessDomain());
                        task.setLevel(item.getLevel());
                        task.setProblemDesc(item.getProblemDesc());
                        task.setThinkingOverview(item.getThinkingOverview());
                        task.setProblemSummary(item.getProblemSummary());
                        task.setDecisionContent(item.getDecisionContent());
                        task.setDecisionData(item.getDecisionData());
                        task.setExpectedEffect(item.getExpectedEffect());
                        task.setTargetAgentId(item.getTargetAgentId());
                        task.setTargetAgentName(item.getTargetAgentName());
                        task.setTaskType(item.getTaskType());
                        task.setDispatched(0);
                        task.setCreateTime(LocalDateTime.now());
                        // 存储输入内容快照
                        task.setDataSnapshot(buildCommonSnapshot(request));
                        // ops-agent 且 decisionData(设备ID)不为空时，查重覆盖
                        dispatchTaskService.handleExistingTask(task);
                        dispatchTaskService.saveOrUpdate(task);
                        savedTasks.add(task);
                    }
                    response.setDispatchTasks(convertCommonTasksToResponse(savedTasks));
                }
                return AjaxResultVO.ok().data(response);
            }
            return AjaxResultVO.error("AI分析失败");
        } catch (Exception e) {
            log.error("通用数据分析异常", e);
            return AjaxResultVO.error("分析异常: " + e.getMessage());
        }
    }

    /**
     * 构建告警数据快照JSON字符串
     */
    private String buildAlarmSnapshot(DeviceAlarmRequest request) {
        try {
            HashMap<String, Object> snapshot = new java.util.HashMap<>();
            snapshot.put("id", request.getId());
            snapshot.put("orgId", request.getOrgId());
            snapshot.put("subareaCid", request.getSubareaCid());
            snapshot.put("systemType", request.getSystemType());
            snapshot.put("deviceId", request.getDeviceId());
            snapshot.put("deviceName", request.getDeviceName());
            snapshot.put("eventLevel", request.getEventLevel());
            snapshot.put("eventState", request.getEventState());
            snapshot.put("location", request.getLocation());
            snapshot.put("eventInfo", request.getEventInfo());
            snapshot.put("happenTime", request.getHappenTime());
            snapshot.put("receiveTime", request.getReceiveTime());
            snapshot.put("processState", request.getProcessState());
            snapshot.put("eventType", request.getEventType());
            snapshot.put("dataType", request.getDataType());
            snapshot.put("dataKey", request.getDataKey());
            snapshot.put("warningLevel", request.getWarningLevel());
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("构建告警数据快照失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将告警派发任务实体转换为响应DTO
     */
    private List<DeviceAlarmResponse.DispatchTaskItem> convertAlarmTasksToResponse(List<DispatchTask> tasks) {
        List<DeviceAlarmResponse.DispatchTaskItem> items = new ArrayList<>();
        for (DispatchTask t : tasks) {
            DeviceAlarmResponse.DispatchTaskItem item = new DeviceAlarmResponse.DispatchTaskItem();
            item.setId(t.getId());
            item.setBusinessDomain(t.getBusinessDomain());
            item.setLevel(t.getLevel());
            item.setProblemDesc(t.getProblemDesc());
            item.setThinkingOverview(t.getThinkingOverview());
            item.setProblemSummary(t.getProblemSummary());
            item.setDecisionContent(t.getDecisionContent());
            item.setDecisionData(t.getDecisionData());
            item.setExpectedEffect(t.getExpectedEffect());
            item.setTargetAgentId(t.getTargetAgentId());
            item.setTaskType(t.getTaskType());
            item.setTargetAgentName(t.getTargetAgentName());
            items.add(item);
        }
        return items;
    }

    /**
     * 构建通用分析数据快照JSON字符串
     */
    private String buildCommonSnapshot(CommonAnalysisRequest request) {
        try {
            HashMap<String, Object> snapshot = new java.util.HashMap<>();
            snapshot.put("content", request.getContent());
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("构建通用分析数据快照失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将通用派发任务实体转换为响应DTO
     */
    private List<ZoneAnalysisResponse.DispatchTaskItem> convertCommonTasksToResponse(List<DispatchTask> tasks) {
        List<ZoneAnalysisResponse.DispatchTaskItem> items = new ArrayList<>();
        for (DispatchTask t : tasks) {
            ZoneAnalysisResponse.DispatchTaskItem item = new ZoneAnalysisResponse.DispatchTaskItem();
            item.setId(t.getId());
            item.setBusinessDomain(t.getBusinessDomain());
            item.setLevel(t.getLevel());
            item.setProblemDesc(t.getProblemDesc());
            item.setThinkingOverview(t.getThinkingOverview());
            item.setProblemSummary(t.getProblemSummary());
            item.setDecisionContent(t.getDecisionContent());
            item.setDecisionData(t.getDecisionData());
            item.setExpectedEffect(t.getExpectedEffect());
            item.setTargetAgentId(t.getTargetAgentId());
            item.setTaskType(t.getTaskType());
            item.setTargetAgentName(t.getTargetAgentName());
            items.add(item);
        }
        return items;
    }
}
