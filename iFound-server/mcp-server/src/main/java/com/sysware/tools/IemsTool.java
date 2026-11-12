package com.sysware.tools;

import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.item.SubAllEnergyQueryDto;
import com.sysware.swcloud.feign.IemsClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class IemsTool {
    @Autowired
    private IemsClient iemsClient;

    //    @Tool(name = "listIemsDevice",description = "查询电表")
    public List listDevice(
            @ToolParam(description = "组织 id") String orgId,
//                           @ToolParam(description = "设备 id",required = false) String id,
            @ToolParam(description = "区域 cid，如果知道区域名称不知道区域cid应该先查询出区域cid", required = false) String subareaCid,
            @ToolParam(description = "能源类型 cid", required = false) String energyTypeCid,
            @ToolParam(description = "设备名称", required = false) String name,
            @ToolParam(description = "设备类型 cid", required = false) String deviceType,
            @ToolParam(description = "是否用于统计单位总用电量：1 是，0 否", required = false) Integer org_total_flag) {
        System.out.println("mcp 调用查询电表:组织id=" + orgId + ",区域cid" + subareaCid + ",能源类型cid" + energyTypeCid + ",设备名称" + name + ",设备类型" + deviceType + ",是否用于统计单位总用电量" + org_total_flag);
        return iemsClient.listDevice(new HashMap<String, Object>() {{
            put("org_id", orgId);
            put("subarea_cid", subareaCid);
            put("energy_type_cid", energyTypeCid);
            put("name", name);
            put("device_type", deviceType);
            put("org_total_flag", org_total_flag);
        }});
    }

//    @Tool(name = "getEmsByDevice", description = "查询指定电表的能耗数据：包含日、月、年能耗数据")
    public AjaxResultVO getEmsByDevice(@ToolParam(description = "设备id") String deviceId,
                                       @ToolParam(description = "日期，格式yyyyMMdd，如 20260130") String date) {
        System.out.println("mcp 调用查询指定电表的能耗数据：设备 id=" + deviceId + "，日期=" + date);
        Map<String, Object> inMap = new HashMap<String, Object>() {{
            put("device_id", deviceId);
            put("date", date);
        }};
        return iemsClient.getDmyDataByDevice(inMap);
    }

    /**
     * 综合查询区域能耗（平铺参数，供 MCP/LLM 调用）。
     */
//    @Tool(name = "getSubareaAllEnergy", description = "综合查询区域能耗")
    public AjaxResultVO getSubareaAllEnergy(
            @ToolParam(description = "组织id列表，通常固定传3") List<String> orgIds,
            @ToolParam(description = "查询时间节点列表。hour传YYYYMMDD，c传YYYYMM，month/year传YYYY") List<String> dateTimes,
            @ToolParam(description = "区域id列表") List<String> subCids,
            @ToolParam(description = "查询的时间维度，c：天，month：月，hour：小时，year：年") String timeType,
            @ToolParam(description = "查询的能耗设备类型id，可不传，默认0.001", required = false) List<String> typeCids) {

        SubAllEnergyQueryDto dto = SubAllEnergyQueryDto.builder()
                .orgIds(orgIds)
                .dateTimes(dateTimes)
                .subCids(subCids)
                .timeType(timeType)
                .typeCids(typeCids)
                .build();
        return iemsClient.subareaAllEnergy(dto);
    }

}
