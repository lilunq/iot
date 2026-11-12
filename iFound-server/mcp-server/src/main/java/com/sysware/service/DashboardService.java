package com.sysware.service;

import com.alibaba.fastjson.JSON;
import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.feign.AdminClient;
import com.sysware.swcloud.feign.IemsClient;
import com.sysware.swcloud.feign.IFoundClient;
import com.sysware.swcloud.feign.ScadaClient;
import com.sysware.tools.MyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.hutool.core.date.DateUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 面板聚合业务服务。
 * <p>
 * 负责聚合区域态势相关的设备、环境、能耗、客流数据。
 * @Author: LiYanRu
 * @date: 2026-07-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    /**
     * 设备业务服务。
     */
    private final DeviceService deviceService;

    /**
     * 区域管理远程服务。
     */
    private final AdminClient adminClient;

    /**
     * 设备监控远程服务。
     */
    private final ScadaClient scadaClient;

    /**
     * 能耗远程服务。
     */
    private final IemsClient iemsClient;

    /**
     * iFound AI平台远程服务（环境分析等）。
     */
    private final IFoundClient iFoundClient;

    /**
     * 获取组织下所有区域态势。
     *
     * @param orgId      组织 ID
     * @param subareaCid 区域级联 ID
     * @return 区域态势列表
     */
    public List<HashMap<String, Object>> getSubareaState(String orgId, String subareaCid) {
        // 1. 初始化区域列表
        List<HashMap<String, Object>> subareas = getSubareas(orgId, subareaCid);
        List<HashMap<String, Object>> result = new ArrayList<>();

        // 1.1 获取各区域环境分析统计（一次调用，按subareaCid匹配）
        Map<String, Map<String, Object>> envStatsMap = getEnvStatsMap(orgId);

        // 2. 聚合每个区域的实时态势数据
        for (HashMap<String, Object> item : subareas) {
            HashMap<String, Object> subarea = new HashMap<>();
            String cascadeId = String.valueOf(item.get("cascade_id"));
            subarea.put("subarea_cid", cascadeId);
            subarea.put("subarea_name", item.get("name"));
            subarea.put("subarea_alter", "0");

            // 2.1 聚合设备状态统计
            HashMap<String, Object> deviceStateCount = deviceService.getDeviceStateCount(orgId, cascadeId);
            subarea.put("deviceStateCount", deviceStateCount);
            if ((Integer) deviceStateCount.get("alarm_total") > 0 || (Integer) deviceStateCount.get("fault_total") > 0) {
                subarea.put("subarea_alter", "1");
            }

            // 2.2 聚合区域今日能耗数据
            subarea.put("energy", getEnergyState(orgId, item));

            // 2.3 聚合环境分析统计（总数/异常数，来自AI定时分析落库）
            Map<String, Object> envStats = envStatsMap.get(cascadeId);
            subarea.put("environment", envStats != null ? envStats : new HashMap<>());
            if (envStats != null) {
                int abnormal = envStats.get("abnormalDevices") != null
                        ? Integer.parseInt(String.valueOf(envStats.get("abnormalDevices"))) : 0;
                if (abnormal > 0) {
                    subarea.put("subarea_alter", "1");
                }
            }

            result.add(subarea);
        }

        // 3. 将异常区域排在前面
        result.sort((a, b) -> {
            String alterA = String.valueOf(a.get("subarea_alter"));
            String alterB = String.valueOf(b.get("subarea_alter"));
            return alterB.compareTo(alterA);
        });
        return result;
    }

    /**
     * 从 xian-ai 获取各区域环境分析统计，构建 subareaCid → {totalDevices, abnormalDevices} 映射。
     *
     * @param orgId 组织ID
     * @return subareaCid → 环境统计Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> getEnvStatsMap(String orgId) {
        Map<String, Map<String, Object>> map = new HashMap<>();
        try {
            AjaxResultVO response = iFoundClient.getEnvAnalysisStats(orgId);
            if (response == null || !response.isOk()) {
                log.warn("获取环境分析统计失败: {}", response != null ? response.getMsg() : "无响应");
                return map;
            }
            Object dataObj = response.get("data");
            if (!(dataObj instanceof Map)) return map;
            Map<String, Object> data = (Map<String, Object>) dataObj;
            Object areasObj = data.get("areas");
            if (!(areasObj instanceof List)) return map;
            List<Map<String, Object>> areas = (List<Map<String, Object>>) areasObj;
            for (Map<String, Object> area : areas) {
                String subareaCid = String.valueOf(area.get("subareaCid"));
                Map<String, Object> env = new HashMap<>();
                env.put("totalDevices", area.get("totalDevices"));
                env.put("abnormalDevices", area.get("abnormalDevices"));
                map.put(subareaCid, env);
            }
            // 补充父级区域汇总（如0.001.001.001-0 = 所有子区域之和）
            Map<String, Map<String, Object>> parentSums = new HashMap<>();
            for (String cid : map.keySet()) {
                int lastDot = cid.lastIndexOf('.');
                if (lastDot < 0) continue;
                String parentCid = cid.substring(0, lastDot);
                if (map.containsKey(parentCid) || parentSums.containsKey(parentCid)) continue;
                String prefix = parentCid + ".";
                int total = 0, abnormal = 0;
                for (Map.Entry<String, Map<String, Object>> e : map.entrySet()) {
                    if (e.getKey().startsWith(prefix)) {
                        total += toEnvInt(e.getValue().get("totalDevices"));
                        abnormal += toEnvInt(e.getValue().get("abnormalDevices"));
                    }
                }
                Map<String, Object> sum = new HashMap<>();
                sum.put("totalDevices", total);
                sum.put("abnormalDevices", abnormal);
                parentSums.put(parentCid, sum);
            }
            map.putAll(parentSums);
        } catch (Exception e) {
            log.error("获取环境分析统计异常", e);
        }
        return map;
    }

    /**
     * 获取异常环境数据。
     *
     * @param orgId 组织 ID
     * @param subareaCid 区域级联 ID
     * @param alarmState 告警状态
     * @return 异常环境数据列表
     */
    public List<HashMap<String, Object>> getEnvAlter(String orgId, String subareaCid, String alarmState) {
        // 1. 初始化区域列表和结果列表
        List<HashMap<String, Object>> subareas = getSubareas(orgId, subareaCid);
        List<HashMap<String, Object>> result = new ArrayList<>();

        // 2. 聚合每个区域的环境异常数据
        for (HashMap<String, Object> item : subareas) {
            HashMap<String, Object> subarea = new HashMap<>();
            subarea.put("subarea_cid", item.get("cascade_id"));
            subarea.put("subarea_name", item.get("name"));
            subarea.put("subarea_alter", "0");

            // 2.1 查询环境设备异常列表
            AjaxResultVO envAjaxResultVO = getEnvironmentDevicePage(orgId, item, alarmState, "30");
            List<HashMap<String, Object>> environmentAlarmDevices = envAjaxResultVO.getData();
            if (envAjaxResultVO.getCount() > 0) {
                subarea.put("subarea_alter", "1");
            }

            // 2.2 组装异常点位数据
            if (MyUtil.isEmpty(environmentAlarmDevices)) {
                subarea.put("environment", new HashMap<>());
            } else {
                appendAlarmData(environmentAlarmDevices, "30");
                subarea.put("environment", environmentAlarmDevices);
            }
            result.add(subarea);
        }

        // 3. 将异常区域排在前面
        result.sort((a, b) -> {
            String alterA = String.valueOf(a.get("subarea_alter"));
            String alterB = String.valueOf(b.get("subarea_alter"));
            return alterB.compareTo(alterA);
        });
        return result;
    }

    /**
     * 安全将环境统计值转为int。
     */
    private int toEnvInt(Object value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 获取待统计的区域列表。
     *
     * @param orgId      组织 ID
     * @param subareaCid 区域级联 ID
     * @return 区域列表
     */
    private List<HashMap<String, Object>> getSubareas(String orgId, String subareaCid) {
        // 1. 初始化查询参数
        Map<String, Object> params = new HashMap<>();

        // 1.1 根据是否指定区域决定查询范围
        if (MyUtil.isEmpty(subareaCid)) {
            params.put("org_id", orgId);
        } else {
            params.put("cascade_id", subareaCid);
        }

        // 2. 查询区域列表
        List<HashMap<String, Object>> subareas = adminClient.getSubareaList(params).getData();
        return subareas == null ? new ArrayList<>() : subareas;
    }

    /**
     * 获取区域今日能耗
     *
     * @param orgId 组织 ID
     * @param item  区域信息
     * @return 能耗状态
     */
    private HashMap<String, Object> getEnergyState(String orgId, HashMap<String, Object> item) {
        // 1. 初始化能耗查询参数
        Map<String, Object> inMap = new HashMap<>();
        inMap.put("orgId", orgId);
        inMap.put("groupCid", JSON.toJSONString(List.of(
                Map.of("subCid", item.get("cascade_id"), "typeCid", "0.001")
        )));

        // 2. 查询当月能耗数据
        String today = DateUtil.format(new Date(), "yyyyMM");
        AjaxResultVO items = iemsClient.listData(inMap, List.of(today), List.of(String.valueOf(item.get("cascade_id"))), List.of("0.001"), "day");
        HashMap sub = (HashMap) ((HashMap) items.get("sub")).get(today);

        // 3. 组装能耗状态
        HashMap<String, Object> energy = new HashMap<>();
        energy.put("status", "0");
        energy.put("message", "能耗正常");
        if (sub.isEmpty()) {
            energy.put("value", "");
        } else {
            String ems = String.valueOf(((ArrayList<?>) sub.get(item.get("cascade_id"))).get(DateUtil.dayOfMonth(new Date()) - 1));
            energy.put("value", ems);
        }
        return energy;
    }

    /**
     * 写入区域环境状态。
     *
     * @param orgId   组织 ID
     * @param item    区域信息
     * @param subarea 区域态势结果
     */
    private void putEnvironmentState(String orgId, HashMap<String, Object> item, HashMap<String, Object> subarea) {
        // 1. 查询环境告警设备
        AjaxResultVO environmentDevicePage = getEnvironmentDevicePage(orgId, item, "1", "30");
        List<HashMap<String, Object>> environmentAlarmDevices = environmentDevicePage.getData();
        if (environmentDevicePage.getCount() > 0) {
            subarea.put("subarea_alter", "1");
        }

        // 1.1 没有环境告警设备时返回空环境数据
        if (MyUtil.isEmpty(environmentAlarmDevices)) {
            subarea.put("environment", new HashMap<>());
            return;
        }

        // 3. 查询每个环境告警设备的异常点位
        appendAlarmData(environmentAlarmDevices, "5");
        subarea.put("environment", environmentAlarmDevices);
    }


    /**
     * 查询环境设备分页数据。
     *
     * @param orgId 组织 ID
     * @param item 区域信息
     * @param alarmState 告警状态
     * @param limit 查询数量
     * @return 环境设备分页数据
     */
    private AjaxResultVO getEnvironmentDevicePage(String orgId, HashMap<String, Object> item,  String alarmState, String limit) {
        // 1. 初始化查询参数
        Map<String, Object> params = new HashMap<>();
        params.put("page", "1");
        params.put("limit", limit);
        params.put("org_id", orgId);
        params.put("system_type", "0.005");
        params.put("subarea_cid", (String) item.get("cascade_id"));

        // 1.1 有告警状态时追加状态过滤条件
        if (MyUtil.isNotEmpty(alarmState)) {
            params.put("alarm_state", alarmState);
        }

        // 2. 查询环境设备分页数据
        return scadaClient.StateListPage(params);
    }

    /**
     * 追加环境设备异常点位数据。
     *
     * @param environmentAlarmDevices 环境告警设备列表
     * @param limit 查询数量
     */
    private void appendAlarmData(List<HashMap<String, Object>> environmentAlarmDevices, String limit) {
        // 1. 遍历环境告警设备
        for (HashMap<String, Object> environmentDevice : environmentAlarmDevices) {
            List<HashMap<String, Object>> deviceDataList = scadaClient.listOneDeviceDataOrState(new HashMap<>() {{
                put("page", "1");
                put("limit", limit);
                put("device_id", environmentDevice.get("id"));
            }}).getData();

            // 1.1 只保留异常状态点位
            List<HashMap<String, Object>> alarmDataList = new ArrayList<>();
            for (HashMap<String, Object> deviceData : deviceDataList) {
                if (!String.valueOf(deviceData.get("state")).equals("1")) {
                    continue;
                }
                HashMap<String, Object> alarmData = new HashMap<>();
                alarmData.put("name", MyUtil.isEmpty(deviceData.get("data_short_name")) ? deviceData.get("data_name") : deviceData.get("data_short_name"));
                alarmData.put("value", MyUtil.isNotEmpty(deviceData.get("dataValue")) ? deviceData.get("dataValue") : "无");
                alarmData.put("data_key", MyUtil.isNotEmpty(deviceData.get("data_key")) ? deviceData.get("data_key") : "无");
                alarmData.put("message", "异常");
                alarmDataList.add(alarmData);
            }
            environmentDevice.put("alarm_data", alarmDataList);
        }
    }
}
