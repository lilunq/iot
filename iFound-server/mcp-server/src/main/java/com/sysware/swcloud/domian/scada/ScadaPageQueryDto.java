package com.sysware.swcloud.domian.scada;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.json.JsonReadFeature;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * @Description: 设备列表查询参数实体类
 * @Author: LiYanru
 * @date: 2026-06-18
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(name = "ScadaPageQueryDto", description = "设备列表查询参数实体类")
public class ScadaPageQueryDto {

    /**
     * 宽松 JSON 解析器：Agent 等调用方可能把 JSON 文本整体作为字符串发送，或携带尾逗号
     */
    private static final JsonMapper LENIENT_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build();

    /**
     * 兼容双重序列化的请求体：当请求体是"包含 JSON 的字符串"时，解包后再反序列化。
     * 正常的对象形式请求体不受影响，仍走默认的属性反序列化。
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ScadaPageQueryDto fromJsonString(String json) {
        try {
            return LENIENT_MAPPER.readValue(json, ScadaPageQueryDto.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("请求体 JSON 解析失败: " + ex.getOriginalMessage(), ex);
        }
    }

    /**
     * 页码
     */
    @Schema(description = "页码", name = "page", example = "1", requiredMode = REQUIRED)
    private String page;

    /**
     * 每页条数
     */
    @Schema(description = "每页条数", name = "limit", example = "10")
    private String limit;

    /**
     * 设备ID集合，用于 ES 实体候选的批量状态过滤。
     */
    @Schema(description = "设备ID集合，非必填；与实体检索返回的业务ID对应", name = "ids")
    private List<String> ids;

    /**
     * 设备ID
     */
    @Schema(description = "设备ID", name = "deviceId")
    @JsonAlias("device_id")
    private String deviceId;

    /**
     * 组织ID，固定为3
     */
    @Schema(description = "组织ID，固定为3", name = "orgId", example = "3", requiredMode = REQUIRED)
    @JsonAlias("org_id")
    private String orgId;

    /**
     * 设备名称，模糊查询
     */
    @Schema(description = "设备名称，模糊查询", name = "deviceName")
    @JsonAlias("device_name")
    private String deviceName;

    /**
     * 区域CID，右模糊查询
     */
    @Schema(description = "区域CID，右模糊查询", name = "subareaCid", example = "0.001.001.001-0")
    @JsonAlias("subarea_cid")
    private String subareaCid;

    /**
     * 设备类型
     */
    @Schema(description = "设备类型，右模糊查询", name = "deviceType", example = "1.001")
    @JsonAlias("device_type")
    private String deviceType;

    /**
     * 系统类型
     */
    @Schema(description = "系统类型", name = "systemType")
    @JsonAlias("system_type")
    private String systemType;

    /**
     * 有该参数代表只查询故障的设备
     */
    @Schema(description = "有该参数代表只查询故障的设备", name = "faultState")
    @JsonAlias("fault_state")
    private String faultState;

    /**
     * 有该参数代表只查询告警的设备
     */
    @Schema(description = "有该参数代表只查询告警的设备", name = "alarmState")
    @JsonAlias("alarm_state")
    private String alarmState;

    /**
     * 是否添加告警和故障设备的变更时间
     */
    @Schema(description = "是否添加告警和故障设备的变更时间", name = "addChangeStateTime")
    @JsonAlias("add_change_state_time")
    private int addChangeStateTime = 0;
}