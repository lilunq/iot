package com.sysware.core.report;

import com.sysware.core.report.domain.ReportDataVO;
import com.sysware.core.report.domain.ReportDeviceEvent;
import com.sysware.core.report.domain.ReportDeviceHealthRate;
import com.sysware.core.report.domain.ReportDeviceState;
import com.sysware.swcloud.domian.R;
import com.sysware.swcloud.domian.ifim.StationInOutTotalVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 报表请求工具，由后端一次性调用多个接口，避免让ai串行调用多次
 *
 * @Author: LiYanRu
 * @date: 2026-07-21
 **/
@Component
@RequiredArgsConstructor
public class ReportTool {
    /** 事件接口返回的时间格式 */
    private static final DateTimeFormatter EVENT_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 报表展示使用的时间格式 */
    private static final DateTimeFormatter DISPLAY_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReportService reportService;

    /**
     * 获取报表数据摘要（中文 Markdown 表格）。
     * <p>
     * 将聚合数据转为可直接阅读的中文表格，AI 只需在此基础上生成图表、分析与建议，
     * 避免自行解析原始 JSON 字段。各模块为：设备健康率、各区域设备状态、各区域能耗状态、
     * 故障设备、严重事件、当日客流。
     *
     * @return data 为 Markdown 文本
     */
    @Tool(name = "getReportData", description = "获取报表数据摘要，返回中文 Markdown 表格，"
            + "包含六个部分：设备健康率、各区域设备状态、各区域能耗状态、故障设备、告警事件、当日客流"
            + "（当日累计发送人数 entryCount、当日累计到站人数 exitCount）")
    public R<String> getReportData() {
        ReportDataVO report = reportService.getReportData();
        return R.success(toMarkdown(report));
    }

    /**
     * 将报表聚合数据转为中文 Markdown 表格。
     *
     * @param report 报表聚合数据
     * @return Markdown 文本
     */
    private String toMarkdown(ReportDataVO report) {
        StringBuilder md = new StringBuilder("# 报表数据摘要\n\n");

        // 一、设备健康率
        md.append("## 一、设备健康率\n\n");
        ReportDeviceHealthRate health = report.getDeviceHealthRate();
        if (health == null) {
            md.append("暂无设备健康率数据。\n\n");
        } else {
            md.append("| 指标 | 数值 |\n| --- | --- |\n");
            md.append("| 设备总数 | ").append(cell(health.getTotalCount())).append(" |\n");
            md.append("| 健康率 | ").append(health.getHealthRate() == null ? "暂无" : health.getHealthRate() + "%").append(" |\n");
            md.append("| 告警设备 | ").append(cell(health.getAlarmCount())).append(" |\n");
            md.append("| 故障设备 | ").append(cell(health.getFaultCount())).append(" |\n\n");
        }

        // 二、各区域设备状态
        md.append("## 二、各区域设备状态\n\n");
        List<Map<String, Object>> subareas = report.getSubareaState();
        if (subareas == null || subareas.isEmpty()) {
            md.append("暂无区域态势数据。\n\n");
        } else {
            md.append("| 区域 | 设备总数 | 告警数 | 故障数 | 需关注 |\n");
            md.append("| --- | --- | --- | --- | --- |\n");
            for (Map<String, Object> area : subareas) {
                Map<String, Object> count = asMap(area.get("deviceStateCount"));
                md.append("| ").append(cell(area.get("subarea_name")))
                        .append(" | ").append(cell(count.get("total")))
                        .append(" | ").append(cell(count.get("alarm_total")))
                        .append(" | ").append(cell(count.get("fault_total")))
                        .append(" | ").append("1".equals(String.valueOf(area.get("subarea_alter"))) ? "是" : "否")
                        .append(" |\n");
            }
            md.append('\n');
        }

        // 三、各区域能耗状态
        md.append("## 三、各区域能耗状态\n\n");
        if (subareas == null || subareas.isEmpty()) {
            md.append("暂无能耗数据。\n\n");
        } else {
            md.append("| 区域 | 今日能耗（kWh） | 状态 |\n");
            md.append("| --- | --- | --- |\n");
            for (Map<String, Object> area : subareas) {
                Map<String, Object> energy = asMap(area.get("energy"));
                md.append("| ").append(cell(area.get("subarea_name")))
                        .append(" | ").append(cell(energy.get("value")))
                        .append(" | ").append(energyStatus(energy))
                        .append(" |\n");
            }
            md.append('\n');
        }

        // 四、故障设备
        md.append("## 四、故障设备\n\n");
        Integer faultDeviceCount = report.getFaultDeviceCount();
        md.append("故障设备共 ").append(faultDeviceCount == null ? 0 : faultDeviceCount).append(" 台。\n\n");
        List<ReportDeviceState> faultDevices = report.getFaultDevices();
        if (faultDevices == null || faultDevices.isEmpty()) {
            md.append("暂无故障设备明细。\n\n");
        } else {
            md.append("| 设备名称 | 设备别名 | 所属区域CID | 状态 |\n");
            md.append("| --- | --- | --- | --- |\n");
            for (ReportDeviceState device : faultDevices) {
                md.append("| ").append(cell(device.getDeviceName()))
                        .append(" | ").append(cell(device.getDeviceAlias()))
                        .append(" | ").append(cell(device.getSubareaCid()))
                        .append(" | ").append(deviceStatus(device))
                        .append(" |\n");
            }
            md.append('\n');
        }

        // 五、严重事件
        md.append("## 五、严重事件\n\n");
        Integer deviceEventCount = report.getDeviceEventCount();
        md.append("严重事件共 ").append(deviceEventCount == null ? 0 : deviceEventCount).append(" 条");
        String eventStartTime = formatEventTime(report.getDeviceEventQueryStartTime());
        if (!eventStartTime.isBlank()) {
            md.append("（查询起始时间：").append(eventStartTime).append("）");
        }
        md.append("。\n\n");
        List<ReportDeviceEvent> deviceEvents = report.getDeviceEvents();
        if (deviceEvents == null || deviceEvents.isEmpty()) {
            md.append("暂无严重事件明细。\n\n");
        } else {
            md.append("| 发生时间 | 接收时间 | 设备名称 | 事件描述 | 区域CID | 等级 |\n");
            md.append("| --- | --- | --- | --- | --- | --- |\n");
            for (ReportDeviceEvent event : deviceEvents) {
                md.append("| ").append(cell(formatEventTime(event.getHappenTime())))
                        .append(" | ").append(cell(formatEventTime(event.getReceiveTime())))
                        .append(" | ").append(cell(event.getDeviceName()))
                        .append(" | ").append(cell(event.getEventInfo()))
                        .append(" | ").append(cell(event.getSubareaCid()))
                        .append(" | ").append(eventLevel(event.getEventLevel()))
                        .append(" |\n");
            }
            md.append('\n');
        }

        // 六、当日客流（entryCount / exitCount 保持不变）
        md.append("## 六、当日客流\n\n");
        StationInOutTotalVO inOutTotal = report.getStationInOutTotal();
        if (inOutTotal == null) {
            md.append("暂无当日客流数据。\n\n");
        } else {
            md.append("| 指标 | 人数 |\n| --- | --- |\n");
            md.append("| 当日累计发送人数 | ").append(cell(inOutTotal.getEntryCount())).append(" |\n");
            md.append("| 当日累计到站人数 | ").append(cell(inOutTotal.getExitCount())).append(" |\n\n");
        }

        return md.toString();
    }

    /**
     * 安全转换为 Map，非 Map 类型返回空 Map。
     *
     * @param value 原始值
     * @return Map
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    /**
     * Markdown 单元格文本：null 转空串，处理竖线与换行避免破坏表格结构。
     *
     * @param value 原始值
     * @return 单元格文本
     */
    private static String cell(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    /**
     * 能耗状态：优先使用接口返回的描述，缺失时按状态码转换。
     *
     * @param energy 能耗数据
     * @return 状态描述
     */
    private static String energyStatus(Map<String, Object> energy) {
        String message = cell(energy.get("message"));
        if (!message.isBlank()) {
            return message;
        }
        return "0".equals(String.valueOf(energy.get("status"))) ? "正常" : "异常";
    }

    /**
     * 设备状态：故障优先，其次告警。
     *
     * @param device 故障设备
     * @return 状态描述
     */
    private static String deviceStatus(ReportDeviceState device) {
        if ("1".equals(device.getFault())) {
            return "故障";
        }
        return "1".equals(device.getAlarm()) ? "告警" : "正常";
    }

    /**
     * 事件等级转换：3=告警、4=故障、10=火警，其余保留原值。
     *
     * @param level 事件等级
     * @return 中文等级
     */
    private static String eventLevel(String level) {
        if (level == null) {
            return "";
        }
        return switch (level) {
            case "2" -> "告警";
            case "3" -> "故障";
            case "10" -> "火警";
            default -> level;
        };
    }

    /**
     * 事件时间格式转换：yyyyMMddHHmmss 转为 yyyy-MM-dd HH:mm:ss，无法解析时原样返回。
     *
     * @param time 原始时间
     * @return 展示时间
     */
    private static String formatEventTime(String time) {
        if (time == null || time.isBlank()) {
            return "";
        }
        try {
            return LocalDateTime.parse(time.trim(), EVENT_TIME_FMT).format(DISPLAY_TIME_FMT);
        } catch (DateTimeParseException e) {
            return time;
        }
    }
}
