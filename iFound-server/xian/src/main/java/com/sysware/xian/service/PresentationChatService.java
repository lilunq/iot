package com.sysware.xian.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sysware.swcloud.common.core.vo.Res;
import com.sysware.xian.domain.StreamChatResponseV2;
import com.sysware.xian.domain.StreamChatV2Request;
import com.sysware.xian.domain.videoAnalytics.PassengerAreaVO;
import com.sysware.xian.swcloud.cache.PassengerAreasCache;
import com.sysware.xian.swcloud.cons.PresentationIntent;
import com.sysware.xian.swcloud.cons.SubareaCon;
import com.sysware.xian.swcloud.domain.AjaxResultVO;
import com.sysware.xian.swcloud.domain.ScadaPageQueryDto;
import com.sysware.xian.swcloud.domain.ScadaYkSendCommand;
import com.sysware.xian.swcloud.domain.issues.RepairOrderCreateCommand;
import com.sysware.xian.swcloud.fegin.MCPXClient;
import com.sysware.xian.util.MarkdownTableConverter;
import com.sysware.xian.util.MyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sysware.xian.swcloud.cons.HermesEventCons.MESSAGE_COMPLETE;
import static com.sysware.xian.swcloud.cons.HermesEventCons.MESSAGE_DELTA;

/**
 * 用于演示模式的对话接口业务类
 *
 * @Author: LiYanRu
 * @date: 2026-08-05
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class PresentationChatService {
    /**
     * 下游工单接口要求的发现时间格式。
     */
    private static final DateTimeFormatter WORK_ORDER_FOUND_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 环境监测查询的区域cid范围（候车大厅至各候车室）
     */
    private static final String ENV_MONITOR_SUBAREA_CID = "0.001.001.001-0.004";

    /**
     * 环境监测展示的数值索引（按列顺序）：0温度、1湿度、4颗粒、5CO2；
     * 噪声(2)、照度(3)与CO(6)、PM10(7)、氧气(8)暂不展示
     */
    private static final int[] ENV_MONITOR_VALUE_INDEXES = {0, 1, 4, 5};

    /**
     * 全站概述内容的 Redis 缓存 key（文字概述版 handleStationOverview2 使用）
     */
    private static final String OVERVIEW_CACHE_KEY = "presentation:station:overview";

    private final ChatClient.Builder chatClientBuilder;
    private final MCPXClient mcpxClient;
    private final PassengerAreasCache passengerAreasCache;
    private final ReportService reportService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    private final Environment environment;

    /**
     * 数字化站房平台接口基础地址（环境监测等），与问题库/消防巡查共用
     */
    @Value("${ai.problem-url}")
    private String problemBaseUrl;
    /**
     * 用于缓存查询出的设备信息，当查询多条只存储第一条，每个pcid的内容隔离 pcid->DeviceInfo(id,device_name)
     */
    private final ConcurrentHashMap<String, DeviceInfo> cacheDeviceInfo = new ConcurrentHashMap<>();

    /**
     * 设备信息
     *
     * @param id         id
     * @param deviceName 设备名称
     */
    private record DeviceInfo(String id, String deviceName) {
    }

    /**
     * 摄像头关键词映射条目：所有关键词都命中时才算匹配
     *
     * @param keywords    关键词列表（小写），消息中需全部包含才命中
     * @param cameraId    摄像头ID
     * @param displayName 摄像头展示名称
     */
    private record CameraEntry(List<String> keywords, String cameraId, String displayName) {
    }

    /**
     * 摄像头关键词 → 摄像头ID 映射（按匹配优先级排序，匹配第一个命中的）
     */
    private static final List<CameraEntry> CAMERA_ENTRIES = List.of(
            // 公司实验室
            new CameraEntry(List.of("实验室"), "1285182895756939264", "实验室"),
            // 检票口（关键词：数字+字母 + "检票"）
            new CameraEntry(List.of("1a", "检票"), "1252577774263078912", "1A检票口"),
            new CameraEntry(List.of("2a", "检票"), "1252941058745044992", "2A检票口"),
            new CameraEntry(List.of("3a", "检票"), "1252941058745044992", "3A检票口"),
            new CameraEntry(List.of("4a", "检票"), "1252577773617156096", "4A检票口"),
            new CameraEntry(List.of("5a", "检票"), "1252577773617156096", "5A检票口"),
            new CameraEntry(List.of("1b", "检票"), "1252941058409500672", "1B检票口"),
            new CameraEntry(List.of("2b", "检票"), "1252941058216562688", "2B检票口"),
            new CameraEntry(List.of("3b", "检票"), "1252941058216562688", "3B检票口"),
            new CameraEntry(List.of("4b", "检票"), "1252577771075407872", "4B检票口"),
            new CameraEntry(List.of("5b", "检票"), "1252577771075407872", "5B检票口"),
            // 出站口（关键词："出站" + 数字）
            new CameraEntry(List.of("出站", "1"), "1252941061186129920", "出站口1"),
            new CameraEntry(List.of("出站", "2"), "1252577778369302528", "出站口2"),
            new CameraEntry(List.of("出站", "3"), "1252577778272833536", "出站口3"),
            new CameraEntry(List.of("出站", "4"), "1252577779203969024", "出站口4"),
            new CameraEntry(List.of("出站", "5"), "1252577778847453184", "出站口5"),
            new CameraEntry(List.of("出站", "6"), "1252577778608377856", "出站口6"),
            // 进站口（关键词：方向 + "进站"）
            new CameraEntry(List.of("西", "进站"), "1252577766344232960", "西进站口"),
            new CameraEntry(List.of("北", "进站"), "1252577732911435776", "北进站口"),
            new CameraEntry(List.of("南", "进站"), "1252577774938361856", "南进站口"),
            new CameraEntry(List.of("东", "进站"), "1252577733557358592", "东进站口"),
            // 候车大厅
            new CameraEntry(List.of("候车"), "1285182895756939264", "候车大厅")
    );

    /**
     * 默认摄像头（候车大厅），未匹配到任何关键词时使用
     */
    private static final String DEFAULT_CAMERA_ID = "1285182895756939264";
    private static final String DEFAULT_CAMERA_NAME = "实验室";

    /**
     * 工单模板：故障原因、处理建议、处理措施
     */
    private record WorkOrderTemplate(String faultCause, String suggestion, String measures) {
    }

    /**
     * 到发人数时段聚合项
     *
     * @param timerange 时段文本（如 "22-24点"）
     * @param startHour 起始小时，用于排序
     * @param arrive    到达人数（下车）累计
     * @param depart    发送人数（上车）累计
     */
    private record ArriveDepartSlot(String timerange, int startHour, int arrive, int depart) {
    }

    /**
     * 全站概述双版本内容
     *
     * @param payload      展示用内容（含设备/环境明细与echarts图表）
     * @param speakPayload 语音播报用内容（概述句，不含echarts图表，拥堵区域为数量概述）
     */
    private record StationOverviewContent(String payload, String speakPayload) {
    }

    /**
     * 预置工单模板（主要针对排风机和送风机），随机选择一套
     */
    private static final List<WorkOrderTemplate> WORK_ORDER_TEMPLATES = List.of(
            new WorkOrderTemplate(
                    "风机电机过热或轴承磨损导致运行异常",
                    "排风机/送风机电机过热，需排查轴承、供电及运行电流",
                    "1. 前往现场确认风机运行状态，检查是否有异响或振动<br>2. 使用红外测温仪测量电机外壳温度，正常应≤70℃<br>3. 检查风机轴承是否缺油或磨损，手动盘车感受阻力"
            ),
            new WorkOrderTemplate(
                    "风机控制柜接触器故障或变频器报警",
                    "风机控制回路异常，需排查接触器、变频器及控制信号",
                    "1. 前往风机控制柜，检查面板指示灯和报警代码<br>2. 检查接触器触点是否烧蚀或粘连<br>3. 查看变频器故障代码，确认是否过流/过压/过载<br>4. 测量控制回路电压，检查PLC输出信号是否正常"
            ),
            new WorkOrderTemplate(
                    "风机皮带断裂或松动导致风量不足",
                    "风机传动系统故障，需检查皮带、皮带轮及张紧装置",
                    "1. 前往现场确认风机是否运行但风量明显不足<br>2. 断电后打开风机检修口，检查皮带是否断裂或松弛<br>3. 检查皮带轮是否对中，有无磨损痕迹<br>4. 若皮带断裂，更换同型号皮带并调整张紧度"
            ),
            new WorkOrderTemplate(
                    "风机振动超标，疑似叶轮不平衡或地脚松动",
                    "风机机械振动异常，需排查叶轮、地脚螺栓及减震器",
                    "1. 前往现场感受风机振动幅度，确认是否超标<br>2. 检查地脚螺栓是否松动，使用扳手逐一紧固<br>3. 检查减震器是否老化或断裂<br>4. 断电后打开检修口，检查叶轮是否积灰或破损"
            ),
            new WorkOrderTemplate(
                    "传感器硬件故障或通信链路中断",
                    "环境传感器故障，需现场排查硬件、供电及通信链路",
                    "1. 前往现场进行安全确认，使用手持式检测仪在同一位置进行对比测量<br>2. 检查传感器供电是否正常（通常为DC24V）<br>3. 检查传感器与RTU/网关的通信线连接是否牢固，确认RS485接线A/B是否接反"
            )
    );

    /**
     * 演示模式对话接口
     *
     * @param emitter SSE，用于向前端推送流式事件
     * @param request 对话请求参数
     */
    public void streamChat(SseEmitter emitter, StreamChatV2Request request) throws IOException {
        String message = request.getMessage() != null ? request.getMessage() : "";

        // 1. 优先关键词路由（无AI依赖）
        PresentationIntent intent = classifyByKeyword(message);

        // 2. 关键词未命中时，回退到AI意图识别（AI服务不可用/连接失败时不抛异常，降级为未识别指令，
        //    保证网关或大模型故障时接口仍能正常返回数据）
        if (intent == null) {
            try {
                ChatClient client = chatClientBuilder.
                        defaultSystem(systemPrompt())
                        .build();
                String content = client.prompt()
                        .user(message)
                        .call()
                        .content();
                intent = PresentationIntent.fromCode(content);
            } catch (Exception e) {
                log.warn("AI意图识别失败，降级为不支持指令返回: message={}, error={}", message, e.getMessage());
            }
        }

        // 3. 不支持的指令
        if (intent == null) {
            StreamChatResponseV2<Object> response = StreamChatResponseV2.builder()
                    .type(MESSAGE_COMPLETE)
                    .payload("当前不支持该指令，请切换到智能语音模式")
                    .build();
            emitter.send(SseEmitter.event().name(MESSAGE_COMPLETE).data(response));
            return;
        }

        log.info("用户输入：{}，意图分支为：{}", message, intent.getDes());
        // 4. 匹配不同的指令模式
        switch (intent) {
            // 3.1 全站情况：以文字概述全站运行态势（客流、环境、设备、能耗、未来1小时到发计划）
            // dev 环境使用文字概述版（handleStationOverview2），其他环境使用原始版
            case STATION_OVERVIEW -> {
                if (isDevProfile()) {
                    handleStationOverview2(emitter, request);
                } else {
                    handleStationOverview(emitter, request);
                }
            }
            // 3.2 打开摄像头：固定选择进站口 a1-5、检票口 a1-5、出站口 a1-5
            case OPEN_CAMERA -> handleOpenCamera(emitter, request);
            // 3.3 某个层的故障设备
            case FLOOR_FAULT_DEVICE -> handleFloorFaultDevice(emitter, request);
            // 3.4 生成工单：直接固定第一台故障设备（无AI分析）
            case CREATE_WORK_ORDER -> handleCreateWorkOrder(emitter, request);
            // 3.5 查看报表：复用已有的报表逻辑
            case VIEW_REPORT -> handleViewReport(emitter, request);
            // 3.6 照明控制：开/关照明
            case LIGHTING_CONTROL -> handleLightingControl(emitter, request);
            default -> {
                emitter.send(SseEmitter.event().name(MESSAGE_COMPLETE).data(StreamChatResponseV2.builder()
                        .type(MESSAGE_COMPLETE)
                        .payload("当前不支持该指令，请切换到智能语音模式")
                        .build()));
            }
        }
    }

    /**
     * 判断当前激活的 Spring profile 是否包含 dev
     */
    private boolean isDevProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equals(profile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理"全站情况"分支：按客流情况、环境状态、设备状态、当日能耗的顺序展示
     *
     * @param emitter SSE，用于向前端推送流式事件
     * @param request 演示模式对话请求参数
     */
    private void handleStationOverview(SseEmitter emitter, StreamChatV2Request request) {
        try {
            // 即时反馈：先推送检索提示（不关闭SSE，数据就绪后再推送正式内容）
            sendInterim(emitter, "正在检索分析……");

            // 1. 查询全站情况数据
            Map<String, Object> data = mcpxClient.getReportData().getData();
            if (data == null || data.isEmpty()) {
                sendMessage(emitter, "暂无可展示的全站数据");
                return;
            }
            JsonNode node = objectMapper.convertValue(data, JsonNode.class);

            StringBuilder content = new StringBuilder();

            // 全站运行总结：置于返回内容最开头
            content.append("# 全站运行平稳，整体态势良好。 \n\n");

            // 2. 客流情况：站内人数、已安检人数、未安检人数、拥堵区域
            JsonNode securityCheck = node.path("securityCheckCurrent");
            // 2.1 当前安检人数统计
            int allCount = securityCheck.path("allCount").asInt(0);
            int checkedCount = securityCheck.path("checkedCount").asInt(0);
            int uncheckedCount = securityCheck.path("uncheckedCount").asInt(0);

            // 2.2 收集拥堵区域数据（附加操作列与useData原始数据，供前端点击查看）
            List<JsonNode> crowdedAreas = buildCrowdedAreas(node.path("crowdedAreas"));

            // 2.3 累计进出站客流（统计区间：2026-07-01 00:00:00 至当前）
//            JsonNode inOutTotal = node.path("stationInOutTotal");
//            int entryCount = inOutTotal.path("entryCount").asInt(0);
//            int exitCount = inOutTotal.path("exitCount").asInt(0);

            // 2.4 转换为md
            content.append("## 一、客流情况\n");
//            content.append("当日累计发送人数：").append(entryCount).append(" 人\n");
//            content.append("当日累计出站人数：").append(exitCount).append(" 人\n");
            content.append("当前在站总人数：").append(allCount).append(" 人\n");
            content.append("已安检人").append(checkedCount).append(" 人\n");
            content.append("未安检").append(uncheckedCount).append(" 人\n");
            if (!crowdedAreas.isEmpty()) {
                content.append("拥堵区域：\n");
                content.append(MarkdownTableConverter.toMarkdownTable(crowdedAreas, crowdedAreaHeaderMap()));
                content.append('\n');
            } else {
                content.append("无拥堵区域\n");
            }

            // 3. 环境状态：候车大厅及各候车室的环境监测数据（来自环境监测接口）
            JsonNode envData = fetchEnvMonitor();
            List<Map<String, Object>> envRows = new ArrayList<>();
            if (envData != null && envData.isArray()) {
                for (JsonNode group : envData) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("groupName", group.path("groupName").asText(""));
                    // 按展示列顺序取值（噪声、照度固定在最后两列）
                    for (int idx : ENV_MONITOR_VALUE_INDEXES) {
                        double v = group.path(String.valueOf(idx)).path("v").asDouble(Double.NaN);
                        row.put(String.valueOf(idx), formatEnvValue(v));
                    }
                    envRows.add(row);
                }
            }
            if (!envRows.isEmpty()) {
                // 转换为md格式
                content.append("## 二、环境状态\n");
                content.append(MarkdownTableConverter.toMarkdownTable(envRows, envMonitorHeaderMap()));
                content.append('\n');
            }

            // 4. 设备状态：各区域设备总数/告警数/故障数
            List<JsonNode> subareas = new ArrayList<>();
            node.path("subareaState").forEach(subareas::add);
            if (!subareas.isEmpty()) {
                // 转换为md格式
                content.append("## 三、设备状态\n");
                content.append(MarkdownTableConverter.toMarkdownTable(subareas, deviceHeaderMap()));
                content.append('\n');
            }

            // 5. 当日能耗：各区域能耗值与状态
            if (!subareas.isEmpty()) {
                // 转换为md格式
                content.append("## 四、当日能耗\n");
                content.append(MarkdownTableConverter.toMarkdownTable(subareas, energyHeaderMap()));
                content.append('\n');
            }

            // 6. 将数据以SSE事件推送给前端
            sendMessage(emitter, content.toString());
        } catch (Exception e) {
            log.error("全站情况获取失败", e);
            sendMessage(emitter, "全站情况获取失败，请稍后重试");
        }
    }

    /**
     * 处理"全站情况"分支（文字概述版）：优先读取 Redis 缓存的概述内容直接返回（定时任务每1分钟刷新），
     * 缓存未命中时即时推送检索提示并实时查询兜底
     * <p>内容以五个章节概述全站运行态势：
     * 一、今日预计发送人数与拥堵区域；二、全站设备总数/告警/故障（区域态势首条全站汇总）；
     * 三、候车大厅温度/湿度/二氧化碳/照度（环境监测接口首个对象）；四、今日总能耗（区域态势首条）；
     * 五、未来1小时列车到发列数与计划到发人数（数字化站房 clxx/klxx 数据）及各时段到发堆叠柱状图
     *
     * @param emitter SSE，用于向前端推送流式事件
     * @param request 演示模式对话请求参数
     */
    private void handleStationOverview2(SseEmitter emitter, StreamChatV2Request request) {
        // 1. 优先读缓存：定时任务已生成好的概述内容可直接返回，无需等待多路接口查询
        StationOverviewContent cached = getCachedStationOverview();
        if (cached != null) {
            log.info("全站概述命中缓存，直接返回缓存内容");
            sendMessage(emitter, cached.payload(), cached.speakPayload());
            return;
        }

        // 2. 缓存未命中：即时推送检索提示，实时查询兜底（顺带回写缓存供后续请求使用）
        sendInterim(emitter, "正在检索分析……");
        try {
            StationOverviewContent content = buildStationOverviewContent();
            if (content == null || content.payload().isBlank()) {
                sendMessage(emitter, "暂无可展示的全站数据");
                return;
            }
            cacheStationOverview(content);
            sendMessage(emitter, content.payload(), content.speakPayload());
        } catch (Exception e) {
            log.error("全站情况获取失败", e);
            sendMessage(emitter, "全站情况获取失败，请稍后重试");
        }
    }

    /**
     * 构建全站概述内容：查询报表聚合、环境监测、今日列车计划与计划到发人数等接口，
     * 一次生成两个版本——payload（展示版：含设备/环境明细与echarts图表）与 speakPayload（播报版：概述句、不含图表）
     * <p>内容以五个章节概述全站运行态势：
     * 一、今日预计发送人数与拥堵区域；二、全站设备情况；三、候车大厅环境；四、今日总能耗；
     * 五、未来1小时列车到发与到发人数
     *
     * @return 概述内容；全站报表数据缺失时返回 null
     */
    private StationOverviewContent buildStationOverviewContent() {
        // 1. 查询全站情况数据
        Map<String, Object> data = mcpxClient.getReportData().getData();
        if (data == null || data.isEmpty()) {
            return null;
        }
        JsonNode node = objectMapper.convertValue(data, JsonNode.class);

        // 2. 公共数据准备
        // 2.1 客流：拥堵区域（在站总人数已改为"今日预计发送人数"，取自 klxx 计划数据，需要时恢复）
//        int stationCurrPeo = node.path("stationRealtime").path("stationCurrPeo").asInt(0);
        List<JsonNode> crowdedAreas = buildCrowdedAreas(node.path("crowdedAreas"));
        List<String> crowdedNames = new ArrayList<>();
        for (JsonNode areas : crowdedAreas) {
            String name = areas.path("areaName").asText("");
            if (!name.isBlank()) {
                crowdedNames.add(name);
            }
        }
        // 2.2 设备与能耗：取区域态势首条（即全站汇总数据）
        JsonNode subareas = node.path("subareaState");
        JsonNode station = subareas.isArray() && subareas.size() > 0 ? subareas.get(0) : null;
        JsonNode deviceCount = station != null ? station.path("deviceStateCount") : null;
        int faultCount = deviceCount != null ? deviceCount.path("fault_total").asInt(0) : 0;
        // 2.3 环境：候车大厅（环境监测接口返回的第一个对象）
        JsonNode envData = fetchEnvMonitor();
        JsonNode hall = envData != null && envData.isArray() && envData.size() > 0 ? envData.get(0) : null;

        // 3. 未来1小时列车到发列数与计划到发人数
        String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 3.1 今日列车计划：arrivetime/leavetime 落在未来一小时内的列车数（字段可能为空串或null，需跳过）
        List<Map<String, Object>> trains = fetchDigitalStationData(dateStr, "clxx");
        int arriveTrains = 0;
        int departTrains = 0;
        for (Map<String, Object> train : trains) {
            if (isWithinNextHour(mapText(train, "arrivetime"))) {
                arriveTrains++;
            }
            if (isWithinNextHour(mapText(train, "leavetime"))) {
                departTrains++;
            }
        }

        // 3.2 计划到发人数：筛选西安东站的记录，按时段聚合到达/发送人数。
        //     时段覆盖当前小时或下一小时的记录累加为未来一小时到发人数；
        //     全部时段用于生成客流到发堆叠柱状图
        int hourNow = LocalTime.now().getHour();
        int hourNext = LocalTime.now().plusHours(1).getHour();
        int arrivePeople = 0;
        int departPeople = 0;
        // 今日预计发送总人数：西安东站全部时段上车人数累加（不受未来一小时窗口限制）
        int todayDepartPeople = 0;
        Map<String, ArriveDepartSlot> slotMap = new LinkedHashMap<>();
        for (Map<String, Object> row : fetchDigitalStationData(dateStr, "klxx")) {
            if (!"西安东站".equals(mapText(row, "stationname"))) {
                continue;
            }
            int arrive = mapInt(row, "debusnum");
            int depart = mapInt(row, "ridingnum");
            todayDepartPeople += depart;

            String timerange = mapText(row, "timerange");
            int[] range = parseRangeHours(timerange);
            if (range == null) {
                continue;
            }

            ArriveDepartSlot slot = slotMap.computeIfAbsent(timerange,
                    k -> new ArriveDepartSlot(timerange, range[0], 0, 0));
            slotMap.put(timerange, new ArriveDepartSlot(timerange, range[0],
                    slot.arrive() + arrive, slot.depart() + depart));

            if (timerangeCovers(timerange, hourNow, hourNext)) {
                arrivePeople += arrive;
                departPeople += depart;
            }
        }
        // 按起始小时升序排列时段
        List<ArriveDepartSlot> slots = new ArrayList<>(slotMap.values());
        slots.sort(Comparator.comparingInt(ArriveDepartSlot::startHour));

        // 4. 组装展示版（payload）：含设备/环境明细与echarts图表
        StringBuilder payload = new StringBuilder();
        payload.append("# 全站运行平稳，整体态势良好。 \n\n");

        // 一、客流：今日预计发送人数（klxx 计划数据换算为万人，最多保留2位小数）与拥堵区域
        String todayDepartWan = formatEnvValue(todayDepartPeople / 10000.0);
        payload.append("## 一、今日预计发送 ").append(todayDepartWan).append(" 万人，");
        // 拥堵区域数量概述（当前启用）
        if (crowdedAreas.isEmpty()) {
            payload.append("当前无拥堵区域。\n\n");
        } else {
            payload.append("当前有 ").append(crowdedAreas.size()).append(" 处拥堵区域。\n\n");
        }
        // 拥堵区域表格展示（需要时切换：放开此段并注释上方数量概述段，前缀无需重复拼接）
//        if (crowdedAreas.isEmpty()) {
//            payload.append("无拥堵区域。\n\n");
//        } else {
//            payload.append("拥堵区域如下：\n\n");
//            payload.append(MarkdownTableConverter.toMarkdownTable(crowdedAreas, crowdedAreaHeaderMap()));
//            payload.append('\n');
//        }

        // 二、设备：明细
        if (deviceCount != null) {
            payload.append("## 二、设备共 ").append(deviceCount.path("total").asInt(0)).append(" 台，")
                    .append("告警 ").append(deviceCount.path("alarm_total").asInt(0)).append(" 台、")
                    .append("故障 ").append(deviceCount.path("fault_total").asInt(0)).append(" 台，")
                    .append("AI已自动为故障设备生成").append(deviceCount.path("fault_total").asInt(0)).append("条维修建议并已下发工单。\n\n");
        } else {
            payload.append("## 二、设备数据暂无。\n\n");
        }

        // 三、环境：明细
        if (hall != null) {
            payload.append("## 三、候车大厅温度 ").append(envValue(hall, 0)).append("℃")
                    .append("，湿度 ").append(envValue(hall, 1)).append("%")
                    .append("，二氧化碳 ").append(envValue(hall, 5)).append("ppm")
                    .append("，照度 ").append(envValue(hall, 3)).append("lx")
                    .append("。\n\n");
        } else {
            payload.append("## 三、候车大厅环境数据暂无。\n\n");
        }

        // 四、能耗
        if (station != null) {
            payload.append("## 四、今日总能耗 ")
                    .append(formatEnvValue(station.path("energy").path("value").asDouble(0)))
                    .append(" kWh。\n\n");
        } else {
            payload.append("## 四、今日能耗数据暂无。\n\n");
        }

        // 五、未来1小时列车到发与到发人数
        payload.append("## 五、未来1小时将到达列车 ").append(arriveTrains).append(" 列、发送列车 ")
                .append(departTrains).append(" 列；预计到达旅客 ").append(arrivePeople)
                .append(" 人、发送旅客 ").append(departPeople).append(" 人。\n\n");

        // 客流到发堆叠柱状图（echarts 代码块，由前端渲染；语音播报不包含）
        payload.append(buildArriveDepartChart(slots));

        // 5. 组装播报版（speakPayload）：概述句，不含echarts图表与表格
        StringBuilder speakPayload = new StringBuilder();
        speakPayload.append("# 全站运行平稳，整体态势良好。 \n\n");

        // 一、客流：今日预计发送人数与拥堵区域（播报版与展示版措辞一致）
        speakPayload.append("## 一、今日预计发送 ").append(todayDepartWan).append(" 万人，");
        // 拥堵区域数量概述（当前启用）
        if (crowdedAreas.isEmpty()) {
            speakPayload.append("当前无拥堵区域。\n\n");
        } else {
            speakPayload.append("当前有 ").append(crowdedAreas.size()).append(" 处拥堵区域。\n\n");
        }
        // 拥堵区域文字列举（需要时切换：放开此段并注释上方数量概述段）
//        if (crowdedNames.isEmpty()) {
//            speakPayload.append("无拥堵区域。\n\n");
//        } else {
//            speakPayload.append("拥堵区域为：").append(String.join("、", crowdedNames)).append("。\n\n");
//        }

        // 二、设备：概述句
        speakPayload.append("## 二、设备有 ").append(faultCount).append(" 台故障，")
                .append("AI已自动生成 ").append(faultCount).append(" 条维修建议并已下发工单。\n\n");

        // 三、环境：概述句
        speakPayload.append("## 三、候车厅整体环境舒适。\n\n");

        // 四、能耗
        if (station != null) {
            speakPayload.append("## 四、今日总能耗 ")
                    .append(formatEnvValue(station.path("energy").path("value").asDouble(0)))
                    .append(" kWh。\n\n");
        } else {
            speakPayload.append("## 四、今日能耗数据暂无。\n\n");
        }

        // 五、未来1小时列车到发与到发人数
        speakPayload.append("## 五、未来1小时将到达列车 ").append(arriveTrains).append(" 列、发送列车 ")
                .append(departTrains).append(" 列；预计到达旅客 ").append(arrivePeople)
                .append(" 人、发送旅客 ").append(departPeople).append(" 人。\n");

        return new StationOverviewContent(payload.toString(), speakPayload.toString());
    }

    /**
     * 定时刷新全站概述缓存：重新查询各路接口并生成 payload/speakPayload 双版本内容写入 Redis。
     * 由 ScheduledService 每5分钟调度一次，"全站情况"分支读缓存返回以消除查询耗时
     */
    public void refreshStationOverviewCache() {
        try {
            StationOverviewContent content = buildStationOverviewContent();
            if (content == null || content.payload().isBlank()) {
                log.warn("定时刷新全站概述缓存：未获取到全站数据，本次跳过");
                return;
            }
            cacheStationOverview(content);
            log.info("定时刷新全站概述缓存完成");
        } catch (Exception e) {
            log.error("定时刷新全站概述缓存失败", e);
        }
    }

    /**
     * 读取 Redis 缓存的全站概述内容（payload 与 speakPayload 双版本，JSON 格式）
     *
     * @return 缓存内容，未命中或 Redis 不可用时返回 null
     */
    private StationOverviewContent getCachedStationOverview() {
        try {
            String json = redisTemplate.opsForValue().get(OVERVIEW_CACHE_KEY);
            if (json == null || json.isBlank()) {
                return null;
            }
            JsonNode node = objectMapper.readTree(json);
            String payload = node.path("payload").asText("");
            String speakPayload = node.path("speakPayload").asText("");
            // 两个版本必须齐全，否则视为未命中（兼容旧格式缓存，会走兜底重建新格式）
            if (payload.isBlank() || speakPayload.isBlank()) {
                return null;
            }
            return new StationOverviewContent(payload, speakPayload);
        } catch (Exception e) {
            log.warn("读取全站概述缓存失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将全站概述内容（payload 与 speakPayload）以 JSON 写入 Redis 缓存，
     * Redis 不可用时仅记录日志，不影响兜底流程
     */
    private void cacheStationOverview(StationOverviewContent content) {
        try {
            ObjectNode cache = objectMapper.createObjectNode();
            cache.put("payload", content.payload());
            cache.put("speakPayload", content.speakPayload());
            redisTemplate.opsForValue().set(OVERVIEW_CACHE_KEY,
                    objectMapper.writeValueAsString(cache));
        } catch (Exception e) {
            log.warn("写入全站概述缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 查询环境监测数据（候车大厅及各候车室的温度/湿度/噪声等，按候车区域分组）
     *
     * @return data 数组节点，查询失败或无数据时返回 null
     */
    private JsonNode fetchEnvMonitor() {
        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
        formParams.add("subarea_cid", ENV_MONITOR_SUBAREA_CID);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formParams, headers);

        String url = problemBaseUrl + "/env/monitor/getEnvMonitor";
        try {
            ResponseEntity<AjaxResultVO> response = restTemplate.exchange(url, HttpMethod.POST, entity, AjaxResultVO.class);
            AjaxResultVO body = response.getBody();
            if (body == null || !body.isOk()) {
                log.warn("查询环境监测数据失败: url={}, msg={}", url, body != null ? body.getMsg() : "无响应");
                return null;
            }
            return objectMapper.convertValue(body.getData(), JsonNode.class);
        } catch (Exception e) {
            log.error("查询环境监测数据异常: url={}", url, e);
            return null;
        }
    }

    /**
     * 查询数字化站房数据（POST /digitalStation/getData，form 参数：dateStr、type）
     * <p>type=clxx 返回今日列车计划列表（traincode/arrivetime/leavetime 等）；
     * type=klxx 返回今日计划到发人数列表（stationname/timerange/ridingnum/debusnum 等）
     *
     * @param dateStr 日期（yyyy-MM-dd 格式）
     * @param type    数据类型
     * @return data 列表（每行为字段-值映射），查询失败或无数据返回空列表
     */
    private List<Map<String, Object>> fetchDigitalStationData(String dateStr, String type) {
        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
        formParams.add("dateStr", dateStr);
        formParams.add("type", type);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formParams, headers);

        String url = problemBaseUrl + "/digitalStation/getData";
        try {
            ResponseEntity<AjaxResultVO> response = restTemplate.exchange(url, HttpMethod.POST, entity, AjaxResultVO.class);
            AjaxResultVO body = response.getBody();
            if (body == null || !body.isOk() || !(body.getData() instanceof List<?> list)) {
                log.warn("查询数字化站房数据失败: type={}, msg={}", type, body != null ? body.getMsg() : "无响应");
                return List.of();
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> raw) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = (Map<String, Object>) raw;
                    rows.add(row);
                }
            }
            return rows;
        } catch (Exception e) {
            log.error("查询数字化站房数据异常: type={}", type, e);
            return List.of();
        }
    }

    /**
     * 判断列车到发时间（HH:mm:ss 或 HH:mm 格式）是否落在未来一小时内。
     * 空串、null 或无法解析的时间不参与统计（上游部分列车仅有到达或发送时间）
     *
     * @param timeText 到发时间文本
     * @return true 表示时间在 [当前时间, 当前时间+1小时) 窗口内
     */
    private static boolean isWithinNextHour(String timeText) {
        if (timeText == null || timeText.isBlank()) {
            return false;
        }
        try {
            LocalTime time = LocalTime.parse(timeText.trim());
            LocalTime now = LocalTime.now();
            LocalTime end = now.plusHours(1);
            // end 早于 now 说明窗口跨天（如 23:30 ~ 00:30），需按两段判断
            if (end.isAfter(now)) {
                return !time.isBefore(now) && time.isBefore(end);
            }
            return !time.isBefore(now) || time.isBefore(end);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * 从时段文本（如 "22-24点"、"0-2点"）解析起止小时
     *
     * @param timerange 时段文本，格式为 "起始小时-结束小时点"
     * @return [起始小时, 结束小时]，格式不符返回 null
     */
    private static int[] parseRangeHours(String timerange) {
        if (timerange == null || timerange.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)").matcher(timerange);
        if (!matcher.find()) {
            return null;
        }
        return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
    }

    /**
     * 判断到发人数时段（如 "22-24点"、"0-2点"）是否覆盖给定小时中的任一小时
     *
     * @param timerange 时段文本，格式为 "起始小时-结束小时点"
     * @param hours     需要覆盖的小时（当前小时、下一小时）
     * @return true 表示时段覆盖任一给定小时
     */
    private static boolean timerangeCovers(String timerange, int... hours) {
        int[] range = parseRangeHours(timerange);
        if (range == null) {
            return false;
        }
        for (int hour : hours) {
            if (hour >= range[0] && hour < range[1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建客流到发堆叠柱状图（```echarts 代码块，由前端渲染）。
     * X 轴为各时段（按起始小时升序），到达=下车人数，发送=上车人数
     *
     * @param slots 按起始小时升序的时段聚合列表
     * @return echarts 图表配置 JSON 代码块，无数据返回空串
     */
    private String buildArriveDepartChart(List<ArriveDepartSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return "";
        }
        ObjectNode chart = objectMapper.createObjectNode();

        ObjectNode title = chart.putObject("title");
        title.put("text", "各时段与客流到发情况");
        title.put("left", "center");
        title.putObject("textStyle").put("fontSize", 14);

        ObjectNode tooltip = chart.putObject("tooltip");
        tooltip.put("trigger", "axis");
        tooltip.putObject("axisPointer").put("type", "shadow");

        ObjectNode legend = chart.putObject("legend");
        ArrayNode legendData = legend.putArray("data");
        legendData.add("到达");
        legendData.add("发送");
        legend.put("bottom", 0);

        ObjectNode grid = chart.putObject("grid");
        grid.put("left", "3%");
        grid.put("right", "4%");
        grid.put("bottom", "12%");
        grid.put("containLabel", true);

        ArrayNode xAxisData = chart.putObject("xAxis").putArray("data");
        for (ArriveDepartSlot slot : slots) {
            xAxisData.add(slot.timerange());
        }

        ObjectNode yAxis = chart.putObject("yAxis");
        yAxis.put("type", "value");
        yAxis.put("name", "人数");

        ArrayNode series = chart.putArray("series");
        ObjectNode arriveSeries = series.addObject();
        arriveSeries.put("name", "到达");
        arriveSeries.put("type", "bar");
        arriveSeries.put("stack", "total");
        ArrayNode arriveValues = arriveSeries.putArray("data");
        arriveSeries.putObject("itemStyle").put("color", "#409eff");
        for (ArriveDepartSlot slot : slots) {
            arriveValues.add(slot.arrive());
        }

        ObjectNode departSeries = series.addObject();
        departSeries.put("name", "发送");
        departSeries.put("type", "bar");
        departSeries.put("stack", "total");
        ArrayNode departValues = departSeries.putArray("data");
        departSeries.putObject("itemStyle").put("color", "#67c23a");
        for (ArriveDepartSlot slot : slots) {
            departValues.add(slot.depart());
        }

        try {
            return "```echarts\n" + objectMapper.writeValueAsString(chart) + "\n```\n";
        } catch (Exception e) {
            log.error("生成客流到发图表配置失败", e);
            return "";
        }
    }

    /**
     * 读取数据行字段文本值，null 返回空串（上游字段可能为 null 或空串）
     */
    private static String mapText(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 读取数据行字段整数值，非数字内容返回 0
     */
    private static int mapInt(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(mapText(row, key));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 读取环境监测分组中指定索引项的数值文本（0温度、1湿度、3照度、5CO2 等），无值返回占位符
     */
    private static String envValue(JsonNode group, int index) {
        double v = group.path(String.valueOf(index)).path("v").asDouble(Double.NaN);
        return formatEnvValue(v);
    }

    /**
     * 设备状态列表字段与中文表头映射（LinkedHashMap 保证列顺序）
     */
    private static Map<String, String> deviceHeaderMap() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("subarea_name", "区域");
        headers.put("deviceStateCount.total", "设备总数");
        headers.put("deviceStateCount.alarm_total", "告警数");
        headers.put("deviceStateCount.fault_total", "故障数");
        return headers;
    }

    /**
     * 环境监测列表字段与中文表头映射（LinkedHashMap 保证列顺序）
     * <p>索引字段含义：0温度、1湿度、4颗粒、5CO2，噪声(2)、照度(3)与CO(6)、PM10(7)、氧气(8)暂不展示
     */
    private static Map<String, String> envMonitorHeaderMap() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("groupName", "区域");
        headers.put("0", "温度");
        headers.put("1", "湿度");
        headers.put("4", "颗粒");
        headers.put("5", "CO2");
        return headers;
    }

    /**
     * 格式化环境监测数值：保留两位小数并去掉多余的浮点尾数（如 68.69999999999999 → 68.7），无值时返回占位符
     */
    private static String formatEnvValue(double value) {
        if (Double.isNaN(value)) {
            return "-";
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    /**
     * 当日能耗列表字段与中文表头映射（LinkedHashMap 保证列顺序）
     */
    private static Map<String, String> energyHeaderMap() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("subarea_name", "区域");
        headers.put("energy.value", "能耗（kWh）");
//        headers.put("energy.message", "能耗状态");
        return headers;
    }

    /**
     * 构建客流分布的转置表格：第一列为行表头，区域名称横向展开为列
     * <p>首行为「区域 | 区域1 | 区域2…」，数据行为「当前人数 | 人数1 | 人数2…」
     *
     * @param groups 客流区域列表（含 groupName、currPeo）
     * @return Markdown 表格
     */
    private static String buildPassengerGroupTable(List<JsonNode> groups) {
        // 1. 表头行：区域 + 各区域名称
        StringBuilder table = new StringBuilder("| 区域");
        for (JsonNode group : groups) {
            table.append(" | ").append(group.path("groupName").asText("").replace("|", "\\|"));
        }
        table.append(" |\n");
        // 2. 分隔行：与 MarkdownTableConverter 的分隔行写法一致，避免出现连续的 ||
        table.append('|').append(" --- |".repeat(groups.size() + 1)).append('\n');
        // 3. 数据行：当前人数
        table.append("| 当前人数");
        for (JsonNode group : groups) {
            table.append(" | ").append(group.path("currPeo").asInt(0));
        }
        table.append(" |\n");
        return table.toString();
    }

    /**
     * 收集拥堵区域数据：附加操作列与 useData 原始数据，供前端点击"查看"打开对应客流区域
     *
     * @param crowdedAreasNode 报表数据中的 crowdedAreas 数组节点
     * @return 附带操作列与原始数据的拥堵区域列表
     */
    private List<JsonNode> buildCrowdedAreas(JsonNode crowdedAreasNode) {
        List<JsonNode> crowdedAreas = new ArrayList<>();
        for (JsonNode areas : crowdedAreasNode) {
            ObjectNode objectNode = (ObjectNode) areas;
            // 添加表格项，用于向前端发送可以点击的操作
            objectNode.put("action", "查看");
            // 组织原数据内容，结构为useData
            PassengerAreaVO area = passengerAreasCache.getArea(areas.path("areaCode").asText());
            Map<String, Object> payload = new HashMap<>();
            payload.put("areaInfo", area);
            payload.put("data_id", "");
            payload.put("subarea_cid", "");
            payload.put("type", "客流区域");
            Map<String, Object> useData = new HashMap<>();
            useData.put("type", "useData");
            useData.put("sessionId", "");
            useData.put("payload", payload);
            objectNode.set("rawData", objectMapper.convertValue(useData, JsonNode.class));
            crowdedAreas.add(objectNode);
        }
        return crowdedAreas;
    }

    /**
     * 拥堵区域列表字段与中文表头映射（LinkedHashMap 保证列顺序）
     */
    private static Map<String, String> crowdedAreaHeaderMap() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("areaName", "区域");
        headers.put("currPeo", "当前人数");
        headers.put("congestion", "拥挤度(%)");
        headers.put("action", "操作");
        headers.put("rawData", "原始数据");
        return headers;
    }

    /**
     * 以 message.delta 事件推送过程提示消息给前端，但不关闭SSE连接，
     * 用于数据检索等耗时操作前的即时反馈，后续仍需调用 {@link #sendMessage} 推送正式内容。
     * 事件结构与 StreamChatV2Service.sendProgressMessage 保持一致，前端将其作为过程提示渲染，收到正式内容后替换
     *
     * @param emitter SSE，用于向前端推送流式事件
     * @param message 提示文本
     */
    private void sendInterim(SseEmitter emitter, String message) {
        try {
            StreamChatResponseV2<Object> response = StreamChatResponseV2.builder()
                    .type(MESSAGE_DELTA)
                    .payload(message)
                    .sessionId("")
                    .build();
            emitter.send(SseEmitter.event().name(MESSAGE_DELTA).data(response));
        } catch (IOException e) {
            log.error("SSE提示消息推送失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 以 message.complete 事件推送文本消息给前端，并关闭SSE连接
     *
     * @param emitter SSE，用于向前端推送流式事件
     * @param message 要推送的文本内容
     */
    private void sendMessage(SseEmitter emitter, String message) {
        sendMessage(emitter, message, null);
    }

    /**
     * 以 message.complete 事件推送文本消息给前端（可附带语音播报内容），并关闭SSE连接
     *
     * @param emitter      SSE，用于向前端推送流式事件
     * @param message      要推送的文本内容（payload）
     * @param speakPayload 语音播报内容（speakPayload），可为 null
     */
    private void sendMessage(SseEmitter emitter, String message, String speakPayload) {
        try {
            StreamChatResponseV2<Object> response = StreamChatResponseV2.builder()
                    .type(MESSAGE_COMPLETE)
                    .payload(message)
                    .speakPayload(speakPayload)
                    .build();
            emitter.send(SseEmitter.event().name(MESSAGE_COMPLETE).data(response));
            emitter.complete();
        } catch (IOException e) {
            log.error("SSE消息推送失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 将消息中的汉字数字（一二三四五六）统一替换为阿拉伯数字（123456），
     * 兼容"出站口一"、"二A检票口"、"三B检票口"等说法。
     * 同时将字母 E（读音与"1"相近）替换为 1，兼容"Ea检票口"="1A检票口"等口语说法。
     * 注意：调用前消息已 toLowerCase，此处 E 已变为 e。
     */
    private static String normalizeChineseNumerals(String text) {
        if (text == null || text.isEmpty()) return text;
        return text
                .replace("一", "1")
                .replace("二", "2")
                .replace("两", "2")
                .replace("三", "3")
                .replace("四", "4")
                .replace("五", "5")
                .replace("六", "6")
                .replace("e", "1");
    }

    /**
     * 以 useData 事件推送设备/摄像头数据给前端，供前端打开对应监控或设备详情
     *
     * @param emitter    SSE
     * @param subareaCid 区域cid
     * @param type       数据类型（机电设备/摄像头）
     * @param dataId     设备/摄像头id
     */
    private void sendUseData(SseEmitter emitter, String subareaCid, String type, String dataId) {
        try {
            Map<String, Object> useDataPayload = new HashMap<>();
            useDataPayload.put("subarea_cid", subareaCid);
            useDataPayload.put("type", type);
            useDataPayload.put("data_id", dataId);

            StreamChatResponseV2<Object> response = StreamChatResponseV2.builder()
                    .type("useData")
                    .payload(useDataPayload)
                    .sessionId("")
                    .build();
            emitter.send(SseEmitter.event().name("useData").data(response));
            log.debug("已推送 useData 事件, type={}, subareaCid={}, dataId={}", type, subareaCid, dataId);
        } catch (IOException e) {
            log.error("推送 useData 事件失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理"打开摄像头"分支
     *
     * @param emitter SSE，用于向前端推送流式事件
     * @param request 演示模式对话请求参数
     */
    private void handleOpenCamera(SseEmitter emitter, StreamChatV2Request request) {
        String message = request.getMessage() != null ? request.getMessage().toLowerCase() : "";
        // 将汉字数字统一转换为阿拉伯数字，兼容"出站口一"、"二A检票口"等说法
        message = normalizeChineseNumerals(message);
        String cameraId = DEFAULT_CAMERA_ID;
        String cameraName = DEFAULT_CAMERA_NAME;

        // 关键词匹配：按优先级顺序检查，所有关键词都命中才算匹配
        for (CameraEntry entry : CAMERA_ENTRIES) {
            boolean allMatch = true;
            for (String kw : entry.keywords()) {
                if (!message.contains(kw)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                cameraId = entry.cameraId();
                cameraName = entry.displayName();
                break;
            }
        }

        log.info("打开摄像头: message={}, matchedCamera={}", request.getMessage(), cameraName);
        // 推送 useData 事件（摄像头）
        sendUseData(emitter, "", "摄像头", cameraId);
        // 推送消息并关闭SSE
        sendMessage(emitter, "已为您打开" + cameraName + "的摄像头画面");
    }

    /**
     * 处理"某个层的故障设备"分支：返回该区域前5台故障设备
     *
     * @param emitter SSE，用于向前端推送流式事件
     * @param request 演示模式对话请求参数
     */
    private void handleFloorFaultDevice(SseEmitter emitter, StreamChatV2Request request) {
        // 1. 获取用户当前指定的区域（只匹配一次：拿正规名称，再直接查 cascadeId）
        String subareaName = SubareaCon.matchSubareaName(request.getMessage());
        String cascadeId = SubareaCon.cascadeIdByName(subareaName);
        try {
            if (cascadeId == null) {
                sendMessage(emitter, "未识别到指定楼层，请说明具体楼层（如：地下层、到达层、候车层、出发层、商业层）");
                return;
            }
            // 1.1 构建请求参数
            ScadaPageQueryDto queryDto = new ScadaPageQueryDto();
            queryDto.setSubareaCid(cascadeId);
            queryDto.setPage("1");
            queryDto.setFaultState("1");
            // 1.2 获取故障设备列表
            AjaxResultVO vo = mcpxClient.StateListPage(queryDto);
            Object data = vo.getData();

            // 2. 转换为jsonNode
            JsonNode node = objectMapper.convertValue(data, JsonNode.class);

            // 3. 提取设备名称（只需要 device_name，最多返回5台）
            List<JsonNode> faultDevices = new ArrayList<>();
            for (JsonNode device : node) {
                if (faultDevices.size() > 5) {
                    break;
                }

                // 3.1 仅保留可用于创建工单的有效设备
                String deviceId = device.path("id").asText("");
                String deviceName = device.path("device_name").asText("");
                if (deviceId.isBlank() || deviceName.isBlank()) {
                    continue;
                }

                // 3.2 缓存首个有效故障设备，供同一 pcId 后续创建工单使用，并推送 useData 事件
                if (faultDevices.isEmpty()) {
                    cacheDeviceInfo.put(request.getPcId(), new DeviceInfo(deviceId, deviceName));
                    // 推送 useData 事件：第一个故障设备（机电设备）
                    sendUseData(emitter, device.path("subarea_cid").asText(""), "机电设备", deviceId);
                }
                faultDevices.add(device);
            }

            // 4. 组装输出：当前xx区域的故障设备为：md表格
            if (faultDevices.isEmpty()) {
                sendMessage(emitter, "当前" + subareaName + "区域暂无故障设备");
                return;
            }
            // 4.1 将查询的内容转换为md表格
            String content = "## 当前" + subareaName + "区域的故障设备为：\n" +
                    MarkdownTableConverter.toMarkdownTable(faultDevices, faultDeviceHeaderMap());
            sendMessage(emitter, content);
        } catch (IllegalArgumentException e) {
            log.error("查询{}层故障设备失败", subareaName, e);
            sendMessage(emitter, "查询故障设备失败" + subareaName + "层的故障设备失败，请稍后重试");
        }
    }

    /**
     * 故障设备列表字段与中文表头映射（LinkedHashMap 保证列顺序）
     */
    private static Map<String, String> faultDeviceHeaderMap() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("device_name", "设备名称");
        return headers;
    }

    /**
     * 处理"生成工单"分支
     *
     * @param emitter SSE，用于向前端推送流式事件
     * @param request 演示模式对话请求参数
     */
    private void handleCreateWorkOrder(SseEmitter emitter, StreamChatV2Request request) {
        // 1. 从当前终端缓存中取得最近查询到的首个故障设备
        DeviceInfo deviceInfo = cacheDeviceInfo.get(request.getPcId());
        if (deviceInfo == null) {
            sendMessage(emitter, "请先查询故障设备，再生成工单");
            return;
        }

        try {
            // 2. 随机选择一套工单模板（故障原因/处理建议/处理措施）
            WorkOrderTemplate template = WORK_ORDER_TEMPLATES.get(
                    ThreadLocalRandom.current().nextInt(WORK_ORDER_TEMPLATES.size()));
            String foundTime = LocalDateTime.now().format(WORK_ORDER_FOUND_TIME_FORMATTER);

            // 3. 组装创建工单命令（无AI诊断，直接创建）
            RepairOrderCreateCommand command = RepairOrderCreateCommand.builder()
                    .processType("")
                    .faultReason(template.faultCause())
                    .handlingAdvice(template.suggestion())
                    .plannedMeasures(template.measures())
                    .issueLevel("MEDIUM")
                    .issueTitle(deviceInfo.deviceName() + "故障")
                    .issueContent(deviceInfo.deviceName() + "故障")
                    .assetId(deviceInfo.id())
//                    .foundTime(foundTime)
                    .userId(request.getUserId())
                    .userName(request.getUserName())
                    .orgCid(MyUtil.isEmpty(request.getOrgCid())?"0.001.001.001":request.getOrgCid())
                    .build();

            // 4. 调用接口创建工单
            Res<Map> vo = mcpxClient.createRepairOrder(command);
            if (vo == null || vo.getCode()==0) {
                String errorMsg = vo != null ? vo.getMsg() : "未知错误";
                log.error("创建工单失败: pcId={}, deviceId={}, msg={}", request.getPcId(), deviceInfo.id(), errorMsg);
                sendMessage(emitter, "创建工单失败：" + errorMsg);
                return;
            }

            // 5. 解析返回的工单信息（issueId / orderId / orderNo）
            Map<String, Object> orderData = vo.getData();
            String orderNo = orderData != null ? (String) orderData.get("orderNo") : "";
            String orderId = orderData != null ? (String) orderData.get("orderId") : "";

            // 6. 组装工单信息（Markdown表格）
            String content = "已为设备“" + deviceInfo.deviceName() + "”创建维修工单\n\n" +
                    "| 工单信息 | 内容 |\n" +
                    "|----|----|\n" +
                    "| 工单编号 | " + orderNo + " |\n" +
                    "| 工单标题 | " + deviceInfo.deviceName() + "故障 |\n" +
                    "| 问题等级 | 中等 |\n" +
                    "| 发现时间 | " + foundTime + " |\n" +
                    "| 处理人 | " + request.getUserName() + " |\n" +
                    "| 故障原因 | " + template.faultCause() + " |\n" +
                    "| 处理建议 | " + template.suggestion() + " |\n" +
                    "| 处理措施 | " + template.measures() + " |";

            log.info("生成工单成功: pcId={}, deviceId={}, deviceName={}, orderId={}, orderNo={}",
                    request.getPcId(), deviceInfo.id(), deviceInfo.deviceName(), orderId, orderNo);
            // 7. 成功之后移除之前缓存的设备信息
            cacheDeviceInfo.remove(request.getPcId());
            sendMessage(emitter, content);

        } catch (Exception e) {
            log.error("生成工单异常: pcId={}, deviceId={}", request.getPcId(), deviceInfo.id(), e);
            sendMessage(emitter, "生成工单失败，请稍后重试");
        }
    }

    /**
     * 处理"查看报表"分支：复用已有的报表逻辑
     *
     * @param emitter SSE，用于向前端推送流式事件
     * @param request 演示模式对话请求参数
     */
    private void handleViewReport(SseEmitter emitter, StreamChatV2Request request) {
        String reportContent = reportService.getLatestReportContent();
        try {
            StreamChatResponseV2<Object> response = StreamChatResponseV2.builder()
                    .type(MESSAGE_COMPLETE)
                    .payload(reportContent)
                    .reportName("站房运行报表")
                    .build();
            emitter.send(SseEmitter.event().name(MESSAGE_COMPLETE).data(response));
            emitter.complete();
        } catch (IOException e) {
            log.error("SSE消息推送失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 照明遥控固定 dataKey
     */
    private static final String LIGHTING_DATA_KEY = "1276967544258826240";

    /**
     * 处理"照明控制"分支：根据用户消息中的"开/关"下发照明遥控指令。
     * packageType=1026（遥调），开=1000，关=0，成功后返回"照明{开启/关闭}成功"。
     *
     * @param emitter SSE，用于向前端推送流式事件
     * @param request 演示模式对话请求参数
     */
    private void handleLightingControl(SseEmitter emitter, StreamChatV2Request request) {
        String message = request.getMessage() != null ? request.getMessage().toLowerCase() : "";
        // 判断开/关：优先匹配"关"，避免"开"字误命中（如"关照明"中不含"开"）
        boolean turnOn;
        if (message.contains("关")) {
            turnOn = false;
        } else if (message.contains("开")) {
            turnOn = true;
        } else {
            sendMessage(emitter, "请明确说明开启或关闭照明");
            return;
        }

        try {
            ScadaYkSendCommand command = ScadaYkSendCommand.builder()
                    .packageType(1026)
                    .key(LIGHTING_DATA_KEY)
                    .value(turnOn ? 1000 : 0)
                    .userId(request.getUserId())
                    .build();

            AjaxResultVO vo = mcpxClient.sendYkYtCommand(command);
            if (vo == null || !vo.isOk()) {
                String errorMsg = vo != null ? vo.getMsg() : "未知错误";
                log.error("照明遥控下发失败: turnOn={}, msg={}", turnOn, errorMsg);
                sendMessage(emitter, "照明控制失败：" + errorMsg);
                return;
            }

            String action = turnOn ? "开启" : "关闭";
            log.info("照明遥控下发成功: turnOn={}", turnOn);
            sendMessage(emitter, "照明" + action + "成功");
        } catch (Exception e) {
            log.error("照明控制异常: turnOn={}", turnOn, e);
            sendMessage(emitter, "照明控制失败，请稍后重试");
        }
    }

    /**
     * 关键词路由：根据用户消息中的关键词匹配意图，无需AI调用。
     * 匹配优先级：全站情况 > 打开摄像头 > 查看故障设备 > 生成工单 > 查看报表。
     *
     * @param message 用户输入消息（小写）
     * @return 匹配到的意图，未命中返回 null
     */
    private static PresentationIntent classifyByKeyword(String message) {
        if (message == null || message.isBlank()) return null;
        String msg = message.toLowerCase();

        // 全站情况：全站/整体概览/设备状态/客流/能耗
        if (containsAny(msg, "全站","运行情况","情况", "整体", "概览", "全站情况", "西安东站")) {
            return PresentationIntent.STATION_OVERVIEW;
        }

        // 打开摄像头：摄像头/监控/画面/视频
        if (containsAny(msg, "摄像头", "监控", "画面", "视频")) {
            return PresentationIntent.OPEN_CAMERA;
        }

        // 查看故障设备：故障设备/故障/层
        if (containsAny(msg, "故障设备", "故障","层")) {
            return PresentationIntent.FLOOR_FAULT_DEVICE;
        }

        // 生成工单：工单/维修工单/生成工单/创建工单
        if (containsAny(msg, "工单", "维修工单", "生成工单", "创建工单")) {
            return PresentationIntent.CREATE_WORK_ORDER;
        }

        // 照明控制：包含"照明"或"灯"，同时包含"开"或"关"
        if ((containsAny(msg, "照明", "灯")) && (containsAny(msg, "开", "关"))) {
            return PresentationIntent.LIGHTING_CONTROL;
        }

        // 查看报表：报表/报告/运行报表/运行报告
        if (containsAny(msg, "报表", "报告", "运行报表", "运行报告")) {
            return PresentationIntent.VIEW_REPORT;
        }

        return null;
    }

    /**
     * 判断消息中是否包含任意一个关键词
     */
    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回ai系统提示词
     *
     * @return 返回ai系统提示词
     */
    private static String systemPrompt() {
        return """
                你是一个意图分类助手。根据用户输入，判断属于以下哪种请求类型，只回答对应的单个数字，不要输出任何其他内容。
                
                1 - 全站情况：查看全站整体概览，如展示设备、进出站客流人数、环境、能耗等
                2 - 打开摄像头：查看摄像头画面，如进站口、安检口、检票口、出站口的摄像头
                3 - 查看某个层的故障设备：查看指定楼层（如到达层、地下层、出发层、商业层、候车层）的故障设备
                4 - 生成工单：为故障设备生成维修工单
                5 - 查看报表：查看运行报表、报告等
                6 - 照明控制：开启或关闭照明，如“打开照明”、“关灯”
                
                示例：
                用户输入：全站现在什么情况 -> 1
                用户输入：打开进站口摄像头 -> 2
                用户输入：站台层有哪些故障设备 -> 3
                用户输入：给故障设备生成工单 -> 4
                用户输入：查看今天的运行报表 -> 5
                用户输入：打开照明 -> 6
                用户输入：关灯 -> 6

                只回答数字 1、2、3、4、5 或 6，不要解释。
                如果没有匹配的，回答 0
                """;
    }
}
