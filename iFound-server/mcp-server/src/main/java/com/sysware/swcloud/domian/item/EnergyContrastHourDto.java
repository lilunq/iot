package com.sysware.swcloud.domian.item;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * @Description: 设备能耗查询参数（时统计-单值时间）
 * @Author: LiYanru
 * @date: 2026-06-14
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "EnergyContrastHourDto", description = "设备能耗查询参数(时)")
public class EnergyContrastHourDto {
    /**
     * 时间点（单值 yyyyMMddHH）
     */
    @Schema(name = "dateTime", description = "时间点(单值,格式:yyyyMMddHH)", example = "20260605", requiredMode = REQUIRED)
    private String dateTime;

    /**
     * 设备 id 集合
     */
    @Schema(description = "设备 id 集合", example = "[\"1205445618000990208\"]", requiredMode = REQUIRED)
    private List<String> devIds;
}
