package com.sysware.swcloud.domian.item;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * @Description: 能耗设备分页查询参数
 * @Author: LiYanRu
 * @date: 2026-06-17
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(name = "ItemDeviceQueryDto", description = "能耗设备分页查询参数")
public class ItemDeviceQueryDto {

    /**
     * 所属单位 ID，固定传3
     */
    @Schema(name = "org_id", description = "所属单位 ID，固定传3", example = "3", requiredMode = REQUIRED)
    private String orgId;

    /**
     * 区域级联 ID（对应 sw_subarea.cascade_id），使用 LIKE 'xxx%' 前缀匹配，传父级 CID 可查到所有子区域设备
     */
    @Schema(name = "subarea_cid", description = "区域级联 ID(LIKE 'xxx%' 前缀匹配,传父级 CID 可查到所有子区域设备)", example = "0.001.001.001-0")
    private String subareaCid;

    /**
     * 能耗类型级联 ID（对应 sw_tree_data.cascade_id），前缀匹配
     */
    @Schema(name = "energy_type_cid", description = "能耗类型级联 ID(前缀匹配,对应 sw_tree_data.cascade_id)")
    private String energyTypeCid;

    /**
     * 设备名称模糊搜索（LIKE '%xxx%'）
     */
    @Schema(name = "name", description = "设备名称模糊搜索(LIKE '%xxx%')", example = "照明")
    private String name;

    /**
     * 设备类型
     */
    @Schema(name = "device_type", description = "设备类型")
    private String deviceType;

    /**
     * 是否用于统计单位总用电量：0 / 1
     */
    @Schema(name = "org_total_flag", description = "是否用于统计单位总用电量:0 / 1", example = "1")
    private String orgTotalFlag;

    /**
     * 设备 ID（精确查询单条）
     */
    @Schema(name = "id", description = "设备 ID(精确查询单条)", example = "1205445618000990208")
    private String id;

    /**
     * 起始页（从 1 开始）
     */
    @Schema(name = "startPage", description = "起始页(从 1 开始)", example = "1", requiredMode = REQUIRED)
    private Integer startPage;

    /**
     * 每页条数
     */
    @Schema(name = "pageSize", description = "每页条数", example = "10", requiredMode = REQUIRED)
    private Integer pageSize;
}
