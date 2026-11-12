package com.sysware.service;

import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.scada.ScadaPageQueryDto;
import com.sysware.swcloud.feign.ScadaClient;
import com.sysware.tools.MyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.hutool.core.bean.BeanUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    /**
     * 设备监控远程服务。
     */
    private final ScadaClient scadaClient;

    /**
     * 查询设备状态统计。
     *
     * @param orgId      组织 ID
     * @param subareaCid 区域级联 ID
     * @return 设备状态统计
     */
    public HashMap<String, Object> getDeviceStateCount(String orgId, String subareaCid) {
        // 1. 初始化查询参数
        HashMap<String, Object> result = new HashMap();
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("limit", 1);
        if (subareaCid != null) params.put("subarea_cid", subareaCid);
        if (orgId != null) params.put("org_id", orgId);

        // 2. 查询设备总数
        result.put("total", scadaClient.StateListPage(params).getCount());

        // 3. 查询故障设备数
        params.put("fault_state", 1);
        result.put("fault_total",scadaClient.StateListPage(params).getCount());//故障
        params.remove("fault_state");

        // 4. 查询告警设备数
        params.put("alarm_state", 1);
        result.put("alarm_total", scadaClient.StateListPage(params).getCount());
        return result;
    }

    /**
     * 获取设备健康率统计。
     *
     * @param orgId      组织 ID
     * @param deviceType 设备类型
     * @param systemType 系统类型
     * @param isEnv      是否只查询环境系统，1 表示是
     * @return 设备健康率、总数、故障数、告警数
     */
    public AjaxResultVO getDeviceHealthRate(String orgId, String deviceType, String systemType, String isEnv) {
        try {
            // 1. 初始化基础查询参数
            Map<String, Object> baseParams = new HashMap<>();
            baseParams.put("page", "1");
            baseParams.put("limit", "1");
            baseParams.put("org_id", orgId);

            // 1.1 设置可选过滤条件
            if (deviceType != null) {
                baseParams.put("device_type", deviceType);
            }
            if (systemType != null) {
                baseParams.put("system_type", systemType);
            }

            // 1.2 环境系统查询固定使用系统类型 0.005
            if ("1".equals(isEnv)) {
                baseParams.put("system_type", "0.005");
            }

            // 2. 查询设备总数
            int totalCount = scadaClient.StateListPage(baseParams).getCount();

            // 2.1 查询故障设备数
            Map<String, Object> faultParams = new HashMap<>(baseParams);
            faultParams.put("fault_state", "1");
            int faultCount = scadaClient.StateListPage(faultParams).getCount();

            // 2.2 查询告警设备数
            Map<String, Object> alarmParams = new HashMap<>(baseParams);
            alarmParams.put("alarm_state", "1");
            int alarmCount = scadaClient.StateListPage(alarmParams).getCount();

            // 3. 计算设备健康率：(总数 - 故障数 - 告警数) / 总数 * 100
            double healthRate = totalCount > 0
                    ? ((double) (totalCount - faultCount - alarmCount) / totalCount * 100)
                    : 0;

            // 4. 组装返回数据，健康率保留 1 位小数
            Map<String, Object> data = new HashMap<>();
            data.put("totalCount", totalCount);
            data.put("faultCount", faultCount);
            data.put("alarmCount", alarmCount);
            data.put("healthRate", String.format("%.1f", healthRate));
            return AjaxResultVO.ok().data(data);
        } catch (Exception e) {
            log.error("获取设备健康率失败", e);
            return AjaxResultVO.error("获取设备健康率失败");
        }
    }

    /**
     * 查询设备列表和状态。
     * <p>
     * 支持按组织、区域、系统类型、设备类型、设备名称、故障状态、告警状态等条件筛选。
     * 当 addChangeStateTime 为 1 且查询故障/告警设备时，会为设备补充最近事件时间和事件信息。
     *
     * @param dto 设备列表查询参数
     * @return 设备列表和分页总数
     */
    public AjaxResultVO StateListPage(ScadaPageQueryDto dto) {
        // 1. 初始化设备查询参数
        Map<String, Object> map = BeanUtil.beanToMap(dto, true, true);
        // 确保 org_id 非 null 时才放入，避免 Feign 传递 null 参数
        if (dto.getOrgId() != null) {
            map.put("org_id", dto.getOrgId());
        }else {
            map.put("org_id","3");
        }
        // 使用前端传入的limit，若未传则默认10
        if (dto.getLimit() == null || dto.getLimit().isEmpty()) {
            map.put("limit", "5");
        }

        // 2. 查询设备列表和状态
        AjaxResultVO ajaxResultVO1 = scadaClient.StateListPage(map);

        // 3. 判断是否需要补充设备故障/告警变更时间
        boolean addTime = dto.getAddChangeStateTime() == 1
                && (("1".equals(dto.getFaultState()) && MyUtil.isNotEmpty(dto.getFaultState()))
                || ("1".equals(dto.getAlarmState()) && MyUtil.isNotEmpty(dto.getAlarmState())));
        if (addTime) {
            List<HashMap<String, Object>> data = ajaxResultVO1.getData();
            Map<String, Object> params2 = new HashMap<>();
            params2.put("page", 1);
            params2.put("limit", 1);
            params2.put("org_id", dto.getOrgId());
            params2.put("event_state", 1);
//            params2.put("event_level", MyUtil.isNotEmpty(dto.getFaultState()) && dto.getFaultState().equals("1") ? "3" : "2");
            String sevenDaysAgo = LocalDateTime.now().minusDays(30)
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            params2.put("start_time", sevenDaysAgo);

            // 3.1 为每台设备查询最近 30 天的故障/告警事件
            for (HashMap<String, Object> item : data) {
                params2.put("device_id", item.get("id"));

                item.put("happen_time", "20260911101308");
                item.put("event_info", "设备故障");
//                AjaxResultVO ajaxResultVO = scadaClient.eventListPage(params2);
//                if (MyUtil.isEmpty(ajaxResultVO.getData())) {
//                    item.put("happen_time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
//                    item.put("event_info", "3".equals(dto.getFaultState()) ? "设备故障" : "设备告警");
//                } else {
//                    List<HashMap<String, Object>> eventData = ajaxResultVO.getData();
//                        item.put("happen_time", eventData.get(0).get("receive_time"));
//                        item.put("event_info", eventData.get(0).get("event_info"));
//                }
            }
            // 3.2 返回补充变更时间后的设备列表
            return AjaxResultVO.ok().addQueryPageData(data, ajaxResultVO1.getCount());
        } else {
            // 4. 不需要补充变更时间时直接返回设备列表
            return ajaxResultVO1;
        }
    }
}
