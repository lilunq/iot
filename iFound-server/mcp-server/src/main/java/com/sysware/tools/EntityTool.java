package com.sysware.tools;

import com.sysware.swcloud.feign.EntityCenterClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 面向 Agent 的统一实体解析 Tool。 */
@Component
@RequiredArgsConstructor
public class EntityTool {
    private final EntityCenterClient entityCenterClient;

    @Tool(name = "resolveBusinessEntity",
            description = "将用户提到的区域、系统、设备分类、设备或摄像头名称/编号/别名解析为稳定业务实体。实体类型必须明确指定：AREA、SYSTEM、DEVICE_TYPE、DEVICE、CAMERA。设备查询可传区域cascade_cid、系统element_key作为过滤条件；摄像头不使用区域过滤。故障、告警、在线等实时状态不在实体解析阶段过滤，应由设备/摄像头业务查询工具处理。若返回AMBIGUOUS，应向用户澄清候选，不要自行猜测。")
    public Map<String, Object> resolveBusinessEntity(
            @ToolParam(description = "用户提到的实体名称、编号或别名") String query,
            @ToolParam(description = "实体类型：AREA、SYSTEM、DEVICE_TYPE、DEVICE、CAMERA") String entityType,
            @ToolParam(description = "区域稳定标识 cascade_cid，仅用于设备查询", required = false) String areaId,
            @ToolParam(description = "系统稳定标识 element_key，仅用于设备查询", required = false) String systemKey,
            @ToolParam(description = "最多返回候选数，默认5，最大20", required = false) Integer limit) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("query", query);
        request.put("entityType", entityType);
        if (areaId != null && !areaId.isBlank()) request.put("areaId", areaId);
        if (systemKey != null && !systemKey.isBlank()) request.put("systemKey", systemKey);
        if (limit != null) request.put("limit", limit);
        return entityCenterClient.resolve(request);
    }
}
