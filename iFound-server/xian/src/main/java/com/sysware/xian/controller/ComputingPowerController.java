package com.sysware.xian.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.xian.swcloud.domain.AjaxResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 计算能力监控接口
 * 通过后台定时轮询metrics接口获取实时指标数据，/computing-power接口直接返回缓存数据
 *
 * @author sysware
 */
@Slf4j
@RestController
@RequestMapping("/computing-power")
@Tag(name = "计算能力监控", description = "AI模型计算能力相关指标查询")
public class ComputingPowerController {

    @Value("${ai.url:http://192.168.128.235:30001}")
    private String aiBaseUrl;

    /**
     * NPU监控详情接口地址（Atlas 300I A2），每次请求/computing-power时实时调用
     */
    @Value("${ai.npu-detail-url:http://192.168.128.235:21004/npu/detail}")
    private String npuDetailUrl;

    /**
     * 轮询间隔(毫秒)
     */
    public static final long POLLING_INTERVAL_MS = 2000;

    /**
     * metrics接口正常的响应应在秒级，读超时必须设置：
     * 该轮询运行在调度线程池上，若底层连接挂起且无超时，会永久卡死执行线程
     */
    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }

    /**
     * 缓存的最新指标快照，使用AtomicReference保证线程安全
     */
    private final AtomicReference<MetricsSnapshot> latestSnapshot = new AtomicReference<>(new MetricsSnapshot());

    /**
     * 上一次轮询时generation_tokens_total（所有engine累计）的值，用于计算增量吞吐量
     */
    private volatile double lastGenerationTokens = 0;
    /**
     * 上一次轮询的时间戳（毫秒），用作实时吞吐量(tokens/s)的计算窗口时长
     */
    private volatile long lastPollTimestamp = 0;

    /**
     * 今日零点时request_success_total（stop+length）的累计值，用于计算今日增量
     */
    private volatile double todayStartSuccessRequests = 0;
    /**
     * 今日零点时request_inference_time_seconds_sum（所有engine累计）的值，用于计算今日平均推理耗时
     */
    private volatile double todayStartInferenceSum = 0;
    /**
     * 今日零点时request_inference_time_seconds_count（所有engine累计）的值，用于计算今日平均推理耗时
     */
    private volatile double todayStartInferenceCount = 0;
    /**
     * 上一次轮询时的日期，用于检测跨天重置
     */
    private volatile LocalDate lastPollDate = null;

    /**
     * 获取计算能力指标数据
     * 每次请求时实时调用/npu/detail接口获取NPU详情，与后台轮询缓存的vLLM指标合并后返回。
     * 其中/npu/detail返回的aiCore即GPU/NPU使用率，映射到gpuUtilizationRate字段（aiCore本身不再单独返回）。
     * 任何调用或计算异常都不会让接口报错，最坏情况返回全0/空值的降级数据。
     */
    @GetMapping
    @Operation(summary = "获取计算能力指标", description = "返回NPU详情与后台轮询获取的最新计算能力指标数据")
    public AjaxResultVO getComputingPower() {
        try {
            // 1. 实时拉取NPU详情（每次请求都调用一次）
            Map<String, Object> result = new LinkedHashMap<>();
            Map<String, Object> npuDetail = fetchNpuDetail();
            if (!npuDetail.isEmpty()) {
                // npu/detail返回的所有数据原样放入
                result.putAll(npuDetail);
            }
            // 2. aiCore映射为gpuUtilizationRate，不再单独返回aiCore
            if (npuDetail.containsKey("aiCore")) {
                result.put("gpuUtilizationRate", npuDetail.get("aiCore"));
                result.remove("aiCore");
            } else if (!result.containsKey("gpuUtilizationRate")) {
                // NPU详情缺失时给一个默认值，保证字段始终存在
                result.put("gpuUtilizationRate", 0);
            }
            // 3. 合并后台轮询缓存的vLLM指标（processingRequest/queueRequest/throughput/reasoningTime/successRequest）
            MetricsSnapshot snapshot = latestSnapshot.get();
            result.putAll(snapshot.toMetricsMap());
            return AjaxResultVO.ok().data(result);
        } catch (Exception e) {
            // 方法级兜底：任何未预期异常都返回结构完整的降级数据（全0/空），接口绝不报错
            log.error("获取计算能力指标异常，返回降级数据: {}", e.getMessage());
            return AjaxResultVO.ok().data(buildDegradedResult());
        }
    }

    /**
     * 构造降级返回数据：NPU详情为空Map，vLLM各项指标为0，保证接口结构完整、字段齐全
     */
    private Map<String, Object> buildDegradedResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gpuUtilizationRate", 0);
        MetricsSnapshot snapshot = new MetricsSnapshot();
        result.putAll(snapshot.toMetricsMap());
        return result;
    }

    /**
     * 获取当前服务器最高温度（/npu/detail返回的maxTemperature，单位℃）
     * 由 ScheduledService 每半小时调用一次用于高温检查。
     * 接口失败或字段缺失时返回0（低于阈值，不会触发高温任务），与fetchNpuDetail的降级策略一致。
     */
    public int getCurrentMaxTemperature() {
        Object value = fetchNpuDetail().get("maxTemperature");
        if (value == null) {
            log.warn("/npu/detail返回缺少maxTemperature字段，按0处理");
            return 0;
        }
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(value)));
        } catch (NumberFormatException e) {
            log.warn("无法解析maxTemperature值: {}", value);
            return 0;
        }
    }

    /**
     * 后台轮询metrics接口
     * 由 ScheduledService 每2秒调用一次（fixedDelay），确保上一次轮询完成后再等待指定间隔执行下一次。
     * 任何异常（网络超时、HTTP错误、解析失败）都被捕获并仅记录日志，绝不抛出——
     * 这样不会中断调度线程，缓存快照保持上一次成功值，接口仍能返回历史数据。
     */
    public void pollMetrics() {
        String metricsUrl = aiBaseUrl + "/metrics";
        try {
            String metricsData = fetchMetrics(metricsUrl);
            processMetrics(metricsData);
        } catch (Exception e) {
            log.error("轮询metrics接口失败: {}", e.getMessage());
        }
    }

    /**
     * 请求metrics接口并返回原始文本
     */
    private String fetchMetrics(String metricsUrl) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.TEXT_PLAIN));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                metricsUrl,
                HttpMethod.GET,
                entity,
                String.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new RuntimeException("metrics接口返回异常, HTTP状态码: " + response.getStatusCode());
        }
        return response.getBody();
    }

    /**
     * 实时请求/npu/detail接口，返回其data对象包含的所有字段。
     * 接口失败或解析异常时返回空Map，不影响主流程的vLLM指标返回。
     */
    private Map<String, Object> fetchNpuDetail() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    npuDetailUrl, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.warn("/npu/detail接口返回异常, HTTP状态码: {}", response.getStatusCode());
                return Collections.emptyMap();
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                log.warn("/npu/detail接口返回缺少data字段");
                return Collections.emptyMap();
            }
            return objectMapper.convertValue(data, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("请求/npu/detail接口失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 解析metrics数据并更新缓存快照
     */
    private void processMetrics(String metricsData) {
        Map<String, String> metricValues = extractMetricValues(metricsData);
        long currentTimestamp = System.currentTimeMillis();

        // 多engine（如DP场景）下同一指标会出现多行，需对所有engine累加，避免只取到engine=0导致统计偏低
        // 计算实时吞吐量(tokens/s) = generation_tokens_total增量 / 实际经过的墙钟时间(秒)
        double currentGenerationTokens = sumMetricAcrossEngines(metricValues, "vllm:generation_tokens_total");
        double throughput = 0.0;
        if (lastPollTimestamp > 0) {
            double deltaTokens = currentGenerationTokens - lastGenerationTokens;
            double deltaSeconds = (currentTimestamp - lastPollTimestamp) / 1000.0;
            // 增量大于0且经过时间合理(>0且不超过两个轮询周期，防止跳过轮询导致均值失真)时才计算
            if (deltaTokens > 0 && deltaSeconds > 0 && deltaSeconds <= POLLING_INTERVAL_MS * 2 / 1000.0) {
                throughput = deltaTokens / deltaSeconds;
            }
        }
        lastGenerationTokens = currentGenerationTokens;
        lastPollTimestamp = currentTimestamp;

        // 提取各项指标（所有engine累加）
        int processingRequest = parseGaugeValue(metricValues, "vllm:num_requests_running");
        int queueRequest = parseGaugeValue(metricValues, "vllm:num_requests_waiting");
        double currentInferenceSum = sumMetricAcrossEngines(metricValues, "vllm:request_inference_time_seconds_sum");
        double currentInferenceCount = sumMetricAcrossEngines(metricValues, "vllm:request_inference_time_seconds_count");
        double currentSuccessRequests = sumSuccessRequests(metricValues);

        // 检测日期变化，重置今日起点值
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (lastPollDate == null || !lastPollDate.equals(today)) {
            // 首次轮询或跨天：以当前累计值作为今日起点
            todayStartSuccessRequests = currentSuccessRequests;
            todayStartInferenceSum = currentInferenceSum;
            todayStartInferenceCount = currentInferenceCount;
            lastPollDate = today;
        }

        // 计算今日成功请求总数（finished_reason为stop和length的增量）
        // 注意：vLLM进程重启等原因会使request_success_total计数器从0重新累加，
        // 导致current < todayStart，这里用Math.max(0,...)兜底，确保结果不会为负数。
        int successRequest = (int) Math.round(Math.max(0.0, currentSuccessRequests - todayStartSuccessRequests));

        // 计算今日平均推理耗时（毫秒）= 今日推理时间增量 / 今日请求增量 * 1000
        // 使用request_inference_time_seconds（RUNNING阶段，不含排队等待）比e2e_request_latency_seconds更贴近"推理耗时"语义
        // 同样用Math.max(0,...)兜底，防止计数器复位导致增量为负
        double reasoningTime = 0.0;
        double deltaInferenceSum = Math.max(0.0, currentInferenceSum - todayStartInferenceSum);
        double deltaInferenceCount = Math.max(0.0, currentInferenceCount - todayStartInferenceCount);
        if (deltaInferenceCount > 0 && deltaInferenceSum > 0) {
            reasoningTime = (deltaInferenceSum / deltaInferenceCount) * 1000.0;
        }

        // 原子更新缓存快照
        latestSnapshot.set(new MetricsSnapshot(
                processingRequest,
                queueRequest,
                Math.round(throughput * 100.0) / 100.0,
                Math.round(reasoningTime * 100.0) / 100.0,
                successRequest));

//        log.debug(
//                "metrics轮询完成 - processingRequest={}, queueRequest={}, throughput={}, reasoningTime={}, successRequest={}",
//                processingRequest, queueRequest, throughput, reasoningTime, successRequest);
    }

    /**
     * 指标快照，不可变对象保证线程安全
     * 注：GPU/NPU使用率(gpuUtilizationRate)由/npu/detail接口的aiCore实时提供，不再缓存于此
     */
    private static class MetricsSnapshot {
        /**
         * 正在处理请求数
         */
        private final int processingRequest;
        /**
         * 正在排队的请求树
         */
        private final int queueRequest;
        /**
         * 吞吐量
         */
        private final double throughput;
        /**
         * 平均推理耗时 ms
         */
        private final double reasoningTime;
        /**
         * 总共请求成功数
         */
        private final int successRequest;

        MetricsSnapshot() {
            this.processingRequest = 0;
            this.queueRequest = 0;
            this.throughput = 0.0;
            this.reasoningTime = 0.0;
            this.successRequest = 0;
        }

        MetricsSnapshot(int processingRequest, int queueRequest,
                        double throughput, double reasoningTime, int successRequest) {
            this.processingRequest = processingRequest;
            this.queueRequest = queueRequest;
            this.throughput = throughput;
            this.reasoningTime = reasoningTime;
            this.successRequest = successRequest;
        }

        Map<String, Object> toMetricsMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("processingRequest", processingRequest);
            result.put("queueRequest", queueRequest);
            result.put("throughput", throughput);
            result.put("reasoningTime", reasoningTime);
            result.put("successRequest", successRequest);
            return result;
        }
    }

    // ==================== metrics数据解析工具方法 ====================

    /**
     * 从metrics文本中提取所有非注释、非TYPE/HELP行的指标键值对
     * 格式: metric_name{labels} value
     * 兼容log1.txt和log2.txt中可能出现的额外行数据
     */
    private Map<String, String> extractMetricValues(String metricsData) {
        Map<String, String> values = new LinkedHashMap<>();
        String[] lines = metricsData.split("\n");

        for (String line : lines) {
            line = line.trim();
            // 跳过空行、注释行和元数据行(TYPE/HELP)
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            // 解析格式: metric_name{labels} value 或 metric_name value
            int braceStart = line.indexOf('{');
            int braceEnd = line.indexOf('}');
            int spaceIdx;

            String metricName;
            String value;

            if (braceStart > 0 && braceEnd > braceStart) {
                // 带标签的指标: metric_name{labels} value
                metricName = line.substring(0, braceStart);
                spaceIdx = line.indexOf(' ', braceEnd);
                if (spaceIdx < 0) {
                    continue;
                }
                value = line.substring(spaceIdx + 1).trim();
            } else {
                // 不带标签的指标: metric_name value
                spaceIdx = line.indexOf(' ');
                if (spaceIdx < 0) {
                    continue;
                }
                metricName = line.substring(0, spaceIdx);
                value = line.substring(spaceIdx + 1).trim();
            }

            // 存储完整的行（含标签）用于后续精确匹配
            values.put(line.substring(0, braceEnd > 0 ? braceEnd + 1 : spaceIdx), value);
            // 同时以metricName为key存储（用于无标签的指标）
            values.putIfAbsent(metricName, value);
        }

        return values;
    }

    /**
     * 从提取的指标中查找匹配特定标签的值
     */
    private double findMetricValue(Map<String, String> metricValues, String metricName, String... labelPatterns) {
        // 先尝试精确匹配（带标签）
        for (Map.Entry<String, String> entry : metricValues.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(metricName + "{")) {
                boolean match = true;
                for (String pattern : labelPatterns) {
                    if (!key.contains(pattern)) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return parseDoubleValue(entry.getValue());
                }
            }
        }
        // 再尝试简单名称匹配
        String value = metricValues.get(metricName);
        if (value != null) {
            return parseDoubleValue(value);
        }
        return 0.0;
    }

    /**
     * 将同一指标名下所有engine（含其它标签维度）的值累加并返回，用于DP等多engine场景，
     * 避免findMetricValue只取到第一个匹配(engine=0)导致统计偏低。
     * 若无任何带标签的匹配，则回退到findMetricValue的无标签取值。
     */
    private double sumMetricAcrossEngines(Map<String, String> metricValues, String metricName, String... labelPatterns) {
        double total = 0.0;
        boolean matched = false;
        for (Map.Entry<String, String> entry : metricValues.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(metricName + "{")) {
                boolean match = true;
                for (String pattern : labelPatterns) {
                    if (!key.contains(pattern)) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    total += parseDoubleValue(entry.getValue());
                    matched = true;
                }
            }
        }
        if (matched) {
            return total;
        }
        return findMetricValue(metricValues, metricName);
    }

    /**
     * 解析gauge类型指标值（如vllm:num_requests_running），对所有engine累加后返回整数
     */
    private int parseGaugeValue(Map<String, String> metricValues, String metricName) {
        double value = sumMetricAcrossEngines(metricValues, metricName);
        return (int) Math.round(value);
    }

    /**
     * 将vllm:request_success_total中finished_reason为stop和length的值累加（含所有engine），返回成功请求总数
     */
    private double sumSuccessRequests(Map<String, String> metricValues) {
        double total = 0.0;
        for (Map.Entry<String, String> entry : metricValues.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("vllm:request_success_total{")) {
                if (key.contains("finished_reason=\"stop\"") || key.contains("finished_reason=\"length\"")) {
                    total += parseDoubleValue(entry.getValue());
                }
            }
        }
        return total;
    }

    /**
     * 将字符串值解析为double，处理科学计数法格式
     */
    private double parseDoubleValue(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("无法解析指标值: {}", value);
            return 0.0;
        }
    }
}
