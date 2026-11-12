package com.sysware.xian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sysware.xian.domain.TunnelRunScore;
import com.sysware.xian.mapper.TunnelRunScoreMapper;
import com.sysware.xian.swcloud.domain.R;
import com.sysware.xian.swcloud.fegin.MCPXClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {
    @Autowired
    private MCPXClient mcpClient;
    @Autowired
    private TunnelRunScoreMapper tunnelRunScoreMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper JSON = new ObjectMapper();

    // 运行水平常量
    private static final String LEVEL_NORMAL = "正常";
    private static final String LEVEL_ATTENTION = "注意";
    private static final String LEVEL_URGENT = "紧急";

    /**
     * 实时计算站房运行水平
     * <p>
     * 通过 /getReportData 报表聚合接口一次性获取设备/环境/能耗/客流4个维度数据
     */
    public HashMap<String, Object> getTunnelRunScore(String orgId) {
        Map<String, HashMap<String, Object>> reportData = fetchReportData(orgId);
        return calculateScore(orgId,
                reportData.get("device"),
                reportData.get("env"),
                reportData.get("energy"),
                reportData.get("passenger"));
    }

    /**
     * 从 /getReportData 报表聚合接口获取数据，提取设备/环境/能耗/客流4个维度
     * <p>
     * 报表数据中 subareaState 数组第一个元素为全站的设备/环境/能耗数据，
     * stationRealtime 和 crowdedAreas 为客流数据。
     *
     * @param orgId 组织ID
     * @return Map 包含 device/env/energy/passenger 4个维度的 HashMap
     */
    @SuppressWarnings("unchecked")
    private Map<String, HashMap<String, Object>> fetchReportData(String orgId) {
        HashMap<String, Object> device = defaultHealthMap();
        HashMap<String, Object> env = defaultHealthMap();
        HashMap<String, Object> energy = defaultHealthMap();
        energy.put("value", 0);
        energy.put("status", "正常");
        HashMap<String, Object> passenger = new HashMap<>();
        passenger.put("healthRate", "100.0");
        passenger.put("value", 0);
        passenger.put("status", "正常");

        try {
            R<Map<String, Object>> response = mcpClient.getReportData();
            if (response != null && "200".equals(response.getCode()) && response.getData() != null) {
                Map<String, Object> data = response.getData();

                // 1. 从 subareaState[0] 提取全站设备/环境/能耗数据
                Object subareaStateObj = data.get("subareaState");
                if (subareaStateObj instanceof List) {
                    List<Map<String, Object>> subareaState = (List<Map<String, Object>>) subareaStateObj;
                    if (!subareaState.isEmpty()) {
                        Map<String, Object> station = subareaState.get(0);

                        // 1.1 设备数据（deviceStateCount: total / fault_total / alarm_total）
                        Object dscObj = station.get("deviceStateCount");
                        if (dscObj instanceof Map) {
                            Map<String, Object> dsc = (Map<String, Object>) dscObj;
                            int total = toInt(dsc.get("total"));
                            int faultTotal = toInt(dsc.get("fault_total"));
                            int alarmTotal = toInt(dsc.get("alarm_total"));
                            double healthRate = total > 0
                                    ? (total - faultTotal - alarmTotal) * 100.0 / total
                                    : 100.0;
                            device.put("totalCount", total);
                            device.put("faultCount", faultTotal);
                            device.put("alarmCount", alarmTotal);
                            device.put("healthRate", String.format("%.1f", healthRate));
                        }

                        // 1.2 环境数据（environment: totalDevices / abnormalDevices）
                        Object envObj = station.get("environment");
                        if (envObj instanceof Map) {
                            Map<String, Object> envMap = (Map<String, Object>) envObj;
                            int totalDevices = toInt(envMap.get("totalDevices"));
                            int abnormalDevices = toInt(envMap.get("abnormalDevices"));
                            double healthRate = totalDevices > 0
                                    ? (totalDevices - abnormalDevices) * 100.0 / totalDevices
                                    : 100.0;
                            env.put("totalCount", totalDevices);
                            env.put("alarmCount", abnormalDevices);
                            env.put("faultCount", 0);
                            env.put("healthRate", String.format("%.1f", healthRate));
                        }

                        // 1.3 能耗数据（energy: status / message / value）
                        Object energyObj = station.get("energy");
                        if (energyObj instanceof Map) {
                            Map<String, Object> energyMap = (Map<String, Object>) energyObj;
                            String status = String.valueOf(energyMap.get("status"));
                            String message = String.valueOf(energyMap.get("message"));
                            energy.put("value", energyMap.get("value"));
                            energy.put("status", message);
                            // status "0" = 正常，非 "0" = 异常
                            energy.put("healthRate", "0".equals(status) ? "100.0" : "50.0");
                            energy.put("totalCount", 1);
                            energy.put("alarmCount", "0".equals(status) ? 0 : 1);
                            energy.put("faultCount", 0);
                        }
                    }
                }

                // 2. 从 crowdedAreas 和 stationRealtime 提取客流数据（保留完整内容）
                int crowdedAreaCount = toInt(data.get("crowdedAreaCount"));
                long stationCurrPeo = 0;
                Object stationRtObj = data.get("stationRealtime");
                if (stationRtObj instanceof Map) {
                    Map<String, Object> stationRt = (Map<String, Object>) stationRtObj;
                    stationCurrPeo = toLong(stationRt.get("stationCurrPeo"));
                    // 保留完整的车站实时客流概览（stationCurrPeo + groupItems 各分组区域人数）
                    passenger.put("stationRealtime", stationRt);
                }
                // 保留完整的拥堵区域列表（areaCode/areaName/currPeo/congestion）
                Object crowdedAreasObj = data.get("crowdedAreas");
                if (crowdedAreasObj instanceof List) {
                    passenger.put("crowdedAreas", crowdedAreasObj);
                }
                // 累计进出站数据（entryCount/exitCount）
                Object inOutObj = data.get("stationInOutTotal");
                if (inOutObj instanceof Map) {
                    passenger.put("stationInOutTotal", inOutObj);
                }

                // 客流健康率：0个拥堵=100(正常)，1-2个=75(注意)，3+个=50(紧急)
                double passengerHealthRate;
                String passengerStatus;
                if (crowdedAreaCount == 0) {
                    passengerHealthRate = 100.0;
                    passengerStatus = "正常";
                } else if (crowdedAreaCount <= 2) {
                    passengerHealthRate = 75.0;
                    passengerStatus = "注意";
                } else {
                    passengerHealthRate = 50.0;
                    passengerStatus = "拥堵";
                }
                passenger.put("healthRate", String.format("%.1f", passengerHealthRate));
                passenger.put("value", stationCurrPeo);
                passenger.put("status", passengerStatus);
                passenger.put("crowdedAreaCount", crowdedAreaCount);
            } else {
                log.warn("报表接口返回异常: {}", response != null ? response.getMsg() : "null");
            }
        } catch (Exception e) {
            log.error("获取报表数据失败: {}", e.getMessage(), e);
        }

        Map<String, HashMap<String, Object>> result = new HashMap<>();
        result.put("device", device);
        result.put("env", env);
        result.put("energy", energy);
        result.put("passenger", passenger);
        return result;
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private HashMap<String, Object> defaultHealthMap() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("healthRate", "100.0");
        map.put("totalCount", 0);
        map.put("alarmCount", 0);
        map.put("faultCount", 0);
        return map;
    }

    /**
     * 根据健康率判定运行水平
     * 正常：健康率 >= 85
     * 注意：60 <= 健康率 < 85
     * 紧急：健康率 < 60
     */
    private String calculateLevel(double healthRate) {
        if (healthRate >= 85) {
            return LEVEL_NORMAL;
        } else if (healthRate >= 60) {
            return LEVEL_ATTENTION;
        } else {
            return LEVEL_URGENT;
        }
    }

    /**
     * 核心水平计算
     */
    @SuppressWarnings("unchecked")
    private HashMap<String, Object> calculateScore(String orgId,
                                                    HashMap<String, Object> device,
                                                    HashMap<String, Object> env,
                                                    HashMap<String, Object> energy,
                                                    HashMap<String, Object> passenger) {
        // 各维度健康率
        BigDecimal deviceRate = parseRate(device.get("healthRate"));
        BigDecimal envRate = parseRate(env.get("healthRate"));
        BigDecimal energyRate = parseRate(energy.get("healthRate"));
        BigDecimal passengerRate = parseRate(passenger.get("healthRate"));

        // 判定各维度运行水平
        String deviceLevel = calculateLevel(deviceRate.doubleValue());
        String envLevel = calculateLevel(envRate.doubleValue());
        String energyLevel = calculateLevel(energyRate.doubleValue());
        String passengerLevel = calculateLevel(passengerRate.doubleValue());

        // 整体水平判定：任一维度为紧急则整体紧急，无紧急且有注意则整体注意，否则正常
        String totalLevel;
        String levelDesc;
        if (LEVEL_URGENT.equals(deviceLevel) || LEVEL_URGENT.equals(envLevel) ||
            LEVEL_URGENT.equals(energyLevel) || LEVEL_URGENT.equals(passengerLevel)) {
            totalLevel = LEVEL_URGENT;
            levelDesc = "站房运行紧急，存在严重异常需立即处理";
        } else if (LEVEL_ATTENTION.equals(deviceLevel) || LEVEL_ATTENTION.equals(envLevel) ||
                   LEVEL_ATTENTION.equals(energyLevel) || LEVEL_ATTENTION.equals(passengerLevel)) {
            totalLevel = LEVEL_ATTENTION;
            levelDesc = "站房运行注意，部分指标需关注";
        } else {
            totalLevel = LEVEL_NORMAL;
            levelDesc = "站房运行正常，各项指标良好";
        }

        HashMap<String, Object> result = new HashMap<>();
        result.put("totalLevel", totalLevel);
        result.put("levelDesc", levelDesc);

        // 设备维度原始数据
        HashMap<String, Object> deviceDim = new HashMap<>();
        deviceDim.put("healthRate", deviceRate);
        deviceDim.put("level", deviceLevel);
        deviceDim.put("totalCount", device.get("totalCount"));
        deviceDim.put("alarmCount", device.get("alarmCount"));
        deviceDim.put("faultCount", device.get("faultCount"));
        result.put("device", deviceDim);

        // 环境维度原始数据
        HashMap<String, Object> envDim = new HashMap<>();
        envDim.put("healthRate", envRate);
        envDim.put("level", envLevel);
        envDim.put("totalCount", env.get("totalCount"));
        envDim.put("alarmCount", env.get("alarmCount"));
        envDim.put("faultCount", env.get("faultCount"));
        result.put("environment", envDim);

        // 能耗维度原始数据
        HashMap<String, Object> energyDim = new HashMap<>();
        energyDim.put("healthRate", energyRate);
        energyDim.put("level", energyLevel);
        energyDim.put("value", energy.get("value"));
        energyDim.put("status", energy.get("status"));
        result.put("energy", energyDim);

        // 客流维度原始数据（含完整 crowdedAreas 和 stationRealtime）
        HashMap<String, Object> passengerDim = new HashMap<>();
        passengerDim.put("healthRate", passengerRate);
        passengerDim.put("level", passengerLevel);
        passengerDim.put("value", passenger.get("value"));
        passengerDim.put("status", passenger.get("status"));
        passengerDim.put("crowdedAreaCount", passenger.get("crowdedAreaCount"));
        passengerDim.put("crowdedAreas", passenger.get("crowdedAreas"));
        passengerDim.put("stationRealtime", passenger.get("stationRealtime"));
        passengerDim.put("stationInOutTotal", passenger.get("stationInOutTotal"));
        result.put("passenger", passengerDim);

        return result;
    }

    private BigDecimal parseRate(Object val) {
        if (val == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(val.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 定时任务：每小时计算并存储运行水平
     */
    public void scheduledCalculateScore() {
        log.info("开始定时计算站房运行水平...");
        try {
            String orgId = "3";
            Map<String, HashMap<String, Object>> reportData = fetchReportData(orgId);
            HashMap<String, Object> scoreData = calculateScore(orgId,
                    reportData.get("device"),
                    reportData.get("env"),
                    reportData.get("energy"),
                    reportData.get("passenger"));

            // 存储到数据库
            TunnelRunScore record = new TunnelRunScore();
            record.setOrgId(orgId);
            record.setTotalLevel((String) scoreData.get("totalLevel"));
            record.setLevelDesc((String) scoreData.get("levelDesc"));

            // 4个维度运行水平
            @SuppressWarnings("unchecked")
            HashMap<String, Object> deviceDim = (HashMap<String, Object>) scoreData.get("device");
            record.setDeviceLevel((String) deviceDim.get("level"));
            record.setDeviceData(JSON.writeValueAsString(deviceDim));

            @SuppressWarnings("unchecked")
            HashMap<String, Object> envDim = (HashMap<String, Object>) scoreData.get("environment");
            record.setEnvLevel((String) envDim.get("level"));
            record.setEnvData(JSON.writeValueAsString(envDim));

            @SuppressWarnings("unchecked")
            HashMap<String, Object> energyDim = (HashMap<String, Object>) scoreData.get("energy");
            record.setEnergyLevel((String) energyDim.get("level"));
            record.setEnergyData(JSON.writeValueAsString(energyDim));

            @SuppressWarnings("unchecked")
            HashMap<String, Object> passengerDim = (HashMap<String, Object>) scoreData.get("passenger");
            record.setPassengerLevel((String) passengerDim.get("level"));
            record.setPassengerData(JSON.writeValueAsString(passengerDim));

            record.setCreateTime(LocalDateTime.now());

            tunnelRunScoreMapper.insert(record);
            log.debug("站房运行水平已存储: 总水平={}, 描述={}", record.getTotalLevel(), record.getLevelDesc());
        } catch (Exception e) {
            log.error("定时计算站房运行水平失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 清除10天前的历史评分数据
     * 由 ScheduledService 每周一凌晨2点调用
     */
    public void scheduledCleanOldScores() {
        log.info("开始清理历史评分数据...");
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(10).withHour(0).withMinute(0).withSecond(0).withNano(0);
            int deleted = tunnelRunScoreMapper.deleteByCreateTimeBefore(cutoffTime);
            log.info("历史评分数据清理完成，删除 {} 条记录", deleted);
        } catch (Exception e) {
            log.error("清理历史评分数据失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 定时任务：将 tunnel_run_score 所有记录的 create_time 日期改为当日，时分秒保持不变
     * 由 ScheduledService 每天凌晨1点调用
     */
    public int refreshAllCreateDateToToday() {
        LocalDate today = LocalDate.now();
        List<TunnelRunScore> records = tunnelRunScoreMapper.selectList(
                new LambdaQueryWrapper<TunnelRunScore>()
                        .select(TunnelRunScore::getId, TunnelRunScore::getCreateTime));
        int updated = 0;
        for (TunnelRunScore record : records) {
            LocalDateTime createTime = record.getCreateTime();
            if (createTime == null) {
                continue;
            }
            LocalDateTime newCreateTime = LocalDateTime.of(today, createTime.toLocalTime());
            if (!newCreateTime.equals(createTime)) {
                tunnelRunScoreMapper.update(null, new LambdaUpdateWrapper<TunnelRunScore>()
                        .eq(TunnelRunScore::getId, record.getId())
                        .set(TunnelRunScore::getCreateTime, newCreateTime));
                updated++;
            }
        }
        log.info("站房运行评分日期刷新完成，共更新 {} 条记录", updated);
        return updated;
    }

    /**
     * 查询历史评分数据
     */
    public List<TunnelRunScore> getHistoryScore(String orgId, String startTime, String endTime) {
        LocalDateTime start = startTime != null ? LocalDateTime.parse(startTime, FMT) : LocalDateTime.now().minusDays(7);
        LocalDateTime end = endTime != null ? LocalDateTime.parse(endTime, FMT) : LocalDateTime.now();
        return tunnelRunScoreMapper.selectByOrgAndTimeRange(orgId, start, end);
    }

    /**
     * 查询最新一条历史评分数据
     */
    public TunnelRunScore getLatestScore(String orgId) {
        return tunnelRunScoreMapper.selectLatest(orgId);
    }
}
