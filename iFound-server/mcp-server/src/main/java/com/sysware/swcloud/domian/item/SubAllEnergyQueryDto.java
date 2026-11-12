package com.sysware.swcloud.domian.item;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * @Description: 综合查询区域能耗参数
 * @Author: LiYanRu
 * @date: 2026-06-18
 **/
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Schema(name = "SubAllEnergyQueryDto", description = "综合查询区域能耗参数")
public class SubAllEnergyQueryDto {
    /**
     * 组织id，固定为3
     */
    @Schema(name = "orgIds", description = "组织id列表，通常固定传3", example = "[\"3\"]", requiredMode = REQUIRED)
    private List<String> orgIds;
    /**
     * 查询时间节点
     * hour -> dateTimes 传 YYYYMMDD
     * 例子：["20260618"]
     * c -> dateTimes 传 YYYYMM
     * 例子：["202606"]
     * month -> dateTimes 传 YYYY
     * 例子：["2026"]
     * year -> dateTimes 传 YYYY
     */
    @Schema(name = "dateTimes", description = "查询时间节点列表。hour传YYYYMMDD，c传YYYYMM，month/year传YYYY", example = "[\"20260618\"]", requiredMode = REQUIRED)
    private List<String> dateTimes;
    /**
     * 区域id
     */
    @Schema(name = "subCids", description = "区域id列表", example = "[\"0.001.001.001-0.001\"]", requiredMode = REQUIRED)
    private List<String> subCids;
    /**
     * 查询的能耗设备类型id，可不传，默认0.001
     */
    @Schema(name = "typeCids", description = "查询的能耗设备类型id，可不传，默认0.001", example = "[\"0.001\"]")
    private List<String> typeCids;
    /**
     * 查询的时间维度，c：天，month：月,hour：小时，year：年
     */
    @Schema(name = "timeType", description = "查询的时间维度，c：天，month：月，hour：小时，year：年", example = "hour", requiredMode = REQUIRED)
    private String timeType;
}