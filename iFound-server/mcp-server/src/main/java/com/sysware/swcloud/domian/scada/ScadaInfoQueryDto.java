package com.sysware.swcloud.domian.scada;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * @Description: 设备信息列表分页查询请求参数
 * @Author: LiYanRu
 * @date: 2026-07-02
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(name = "ScadaInfoQueryDto", description = "设备信息列表分页查询请求参数")
public class ScadaInfoQueryDto {
    /**
     * 当前页码，从 1 开始。
     */
    @NotNull(message = "page不能为空")
    @Min(value = 1, message = "page不能小于0")
    @Schema(name = "page", description = "当前页码，从 1 开始", example = "1", requiredMode = REQUIRED)
    private Integer page;
    /**
     * 每页条数。
     */
    @NotNull(message = "limit不能为空")
    @Min(value = 1, message = "limit必须大于0")
    @Schema(name = "limit", description = "每页条数", example = "10", requiredMode = REQUIRED)
    private Integer limit;
    /**
     * 单个组织 ID，StateListPage 场景下必填。
     */
    @NotNull(message = "orgId不能为空")
    @JsonProperty("org_id")
    @Schema(name = "org_id", description = "单个组织 ID，StateListPage 场景下必填", example = "3", requiredMode = REQUIRED)
    private Long orgId;
    /**
     * 单个设备 ID。
     */
    @JsonProperty("device_id")
    @Schema(name = "device_id", description = "单个设备 ID，精确查询时使用", example = "1203022680400334848")
    private Long deviceId;
    /**
     * 系统类型，支持前缀匹配。
     */
    @JsonProperty("system_type")
    @Schema(name = "system_type", description = "系统类型，支持前缀匹配", example = "1.001")
    private String systemType;

    /**
     * 区域级联 ID，支持前缀匹配。
     */
    @JsonProperty("subarea_cid")
    @Schema(name = "subarea_cid", description = "区域级联 ID，支持前缀匹配", example = "0.001.001.001-0")
    private String subareaCid;
    /**
     * 设备名称，支持模糊查询。
     */
    @JsonProperty("device_name")
    @Schema(name = "device_name", description = "设备名称，支持模糊查询", example = "照明")
    private String deviceName;

}