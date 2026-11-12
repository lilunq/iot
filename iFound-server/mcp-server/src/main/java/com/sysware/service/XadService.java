package com.sysware.service;

import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.feign.XadClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 西安东环境监测服务：查询环境数据并转换为中文 Markdown 展示格式。
 */
@Service
@RequiredArgsConstructor
public class XadService {

    /**
     * 支持查询环境的区域 CID 白名单。
     */
    private static final Set<String> SUPPORTED_SUBAREA_CIDS = Set.of(
            "0.001.001.001-0.002", // 到达层
            "0.001.001.001-0.003", // 出发层
            "0.001.001.001-0.004"  // 候车层
    );

    /**
     * 监测项数字键 → 中文名称、单位（按展示顺序）。
     */
    private static final LinkedHashMap<String, String[]> ENV_ITEMS = new LinkedHashMap<>();

    static {
        ENV_ITEMS.put("0", new String[]{"温度", "℃"});
        ENV_ITEMS.put("1", new String[]{"湿度", "%"});
        ENV_ITEMS.put("2", new String[]{"噪音", "dB"});
        ENV_ITEMS.put("3", new String[]{"照度", "lx"});
        ENV_ITEMS.put("4", new String[]{"PM2.5", "ug/m³"});
        ENV_ITEMS.put("5", new String[]{"CO₂", "ppm"});
        ENV_ITEMS.put("8", new String[]{"氧气", "%"});
    }

    private static final String EMPTY_RESULT = "当前未查询到相关环境数据。";

    private static final String EMPTY_PRODUCTION = "当前未查询到生产统计数据。";

    private final XadClient xadClient;

    /**
     * 查询到达层/出发层/候车层的环境监测数据（温度、湿度、噪音、照度、PM2.5、CO2、氧气等）。
     *
     * @param subareaCid 区域 CID，非必填；仅支持 0.001.001.001-0.002（到达层）、
     *                   0.001.001.001-0.003（出发层）、0.001.001.001-0.004（候车层），
     *                   不传则查询全部支持区域
     * @return 格式化的中文 Markdown 文本
     */
    public String queryEnvMonitor(String subareaCid) {
        if (subareaCid != null && !subareaCid.isBlank() && !SUPPORTED_SUBAREA_CIDS.contains(subareaCid)) {
            return "该区域暂不支持环境查询，仅支持到达层、出发层、候车层。";
        }
        Map<String, Object> param = new HashMap<>();
        if (subareaCid != null && !subareaCid.isBlank()) {
            param.put("subarea_cid", subareaCid);
        }
        AjaxResultVO result = xadClient.getEnvMonitor(param);
        if (result == null || !result.isOk() || result.getData() == null) {
            return EMPTY_RESULT;
        }
        return formatEnvMonitor(result.getData());
    }

    /**
     * 将环境监测原始数据转换为中文 Markdown：
     * 单个区域分组输出"区域：温度23.55℃、湿度59.75%……"；多个区域分组输出 Markdown Table。
     * 数值统一保留2位小数并带单位。
     */
    private String formatEnvMonitor(Object data) {
        if (!(data instanceof List) || ((List<?>) data).isEmpty()) {
            return EMPTY_RESULT;
        }
        List<EnvGroup> groups = new ArrayList<>();
        for (Object o : (List<?>) data) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<?, ?> group = (Map<?, ?>) o;
            String name = String.valueOf(group.get("groupName"));
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, String[]> item : ENV_ITEMS.entrySet()) {
                Object metric = group.get(item.getKey());
                if (metric instanceof Map) {
                    Object v = ((Map<?, ?>) metric).get("v");
                    if (v != null) {
                        values.put(item.getKey(), formatValue(v) + item.getValue()[1]);
                    }
                }
            }
            if (!values.isEmpty()) {
                groups.add(new EnvGroup(name, values));
            }
        }
        if (groups.isEmpty()) {
            return EMPTY_RESULT;
        }
        if (groups.size() == 1) {
            return formatSingleGroup(groups.get(0));
        }
        return formatGroupTable(groups);
    }

    /**
     * 单个区域：候车大厅：温度23.55℃、湿度59.75%、噪音57.00dB……
     */
    private String formatSingleGroup(EnvGroup group) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String[]> item : ENV_ITEMS.entrySet()) {
            String value = group.values.get(item.getKey());
            if (value != null) {
                parts.add(item.getValue()[0] + value);
            }
        }
        return group.name + "：" + String.join("、", parts);
    }

    /**
     * 多个区域：Markdown Table，表头为"监测项(单位)"，缺失监测项填 -。
     */
    private String formatGroupTable(List<EnvGroup> groups) {
        StringBuilder sb = new StringBuilder();
        sb.append("| 区域 |");
        for (String[] item : ENV_ITEMS.values()) {
            sb.append(" ").append(item[0]).append("(").append(item[1]).append(") |");
        }
        sb.append("\n|:--:|");
        for (int i = 0; i < ENV_ITEMS.size(); i++) {
            sb.append(":--:|");
        }
        for (EnvGroup group : groups) {
            sb.append("\n| ").append(group.name).append(" |");
            for (String key : ENV_ITEMS.keySet()) {
                sb.append(" ").append(group.values.getOrDefault(key, "-")).append(" |");
            }
        }
        return sb.toString();
    }

    /**
     * 数值保留2位小数（四舍五入），转换失败时原样返回。
     */
    private String formatValue(Object v) {
        try {
            return String.format("%.2f", Double.parseDouble(String.valueOf(v)));
        } catch (NumberFormatException e) {
            return String.valueOf(v);
        }
    }

    private static class EnvGroup {
        final String name;
        final Map<String, String> values;

        EnvGroup(String name, Map<String, String> values) {
            this.name = name;
            this.values = values;
        }
    }

    /**
     * 查询近7个月旅客发送量生产统计数据（当月累计与每日旅客发送量）。
     *
     * @return 格式化的中文 Markdown 表格文本
     */
    public String queryProductionMonthly() {
        AjaxResultVO result = xadClient.list(new HashMap<>());
        if (result == null || !result.isOk() || !(result.getData() instanceof List)) {
            return EMPTY_PRODUCTION;
        }
        List<?> rows = (List<?>) result.getData();
        if (rows.isEmpty()) {
            return EMPTY_PRODUCTION;
        }

        List<String> monthTitles = new ArrayList<>();
        List<String> sumValues = new ArrayList<>();
        List<Map<Integer, String>> dailyList = new ArrayList<>();
        int maxDay = 0;
        for (Object o : rows) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) o;
            monthTitles.add(formatMonth(row.get("month")));
            sumValues.add(formatNumber(row.get("sum_value")));
            Map<Integer, String> daily = new TreeMap<>();
            for (int d = 1; d <= 31; d++) {
                Object v = row.get("d" + d);
                if (v != null) {
                    daily.put(d, formatNumber(v));
                    maxDay = Math.max(maxDay, d);
                }
            }
            dailyList.add(daily);
        }
        if (monthTitles.isEmpty()) {
            return EMPTY_PRODUCTION;
        }
        return formatProductionTable(monthTitles, sumValues, dailyList, maxDay);
    }

    /**
     * 转置表格：行 = 当月累计 + 日期（1日~最大有数日期），列 = 月份（保持接口返回顺序）。
     * 数值原样输出，不加千分位分隔符。
     */
    private String formatProductionTable(List<String> monthTitles, List<String> sumValues,
                                         List<Map<Integer, String>> dailyList, int maxDay) {
        StringBuilder sb = new StringBuilder();
        sb.append("| 日期 |");
        for (String title : monthTitles) {
            sb.append(" ").append(title).append(" |");
        }
        sb.append("\n|:--:|");
        for (int i = 0; i < monthTitles.size(); i++) {
            sb.append(":--:|");
        }
        sb.append("\n| 当月累计（人次） |");
        for (String sum : sumValues) {
            sb.append(" ").append(sum).append(" |");
        }
        for (int d = 1; d <= maxDay; d++) {
            sb.append("\n| ").append(d).append("日 |");
            for (Map<Integer, String> daily : dailyList) {
                String v = daily.get(d);
                sb.append(" ").append(v == null ? "-" : v).append(" |");
            }
        }
        sb.append("\n\n以上为各月每日旅客发送量，单位：人次。");
        return sb.toString();
    }

    /**
     * month 字段（如 202611）转为"2026年11月"。
     */
    private String formatMonth(Object month) {
        try {
            int v = Integer.parseInt(String.valueOf(month).trim());
            return (v / 100) + "年" + (v % 100) + "月";
        } catch (NumberFormatException e) {
            return String.valueOf(month);
        }
    }

    /**
     * 数值原样输出（去掉多余的小数位），不加千分位分隔符。
     */
    private String formatNumber(Object v) {
        if (v == null) {
            return "-";
        }
        try {
            return new BigDecimal(String.valueOf(v).trim()).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return String.valueOf(v);
        }
    }
}
