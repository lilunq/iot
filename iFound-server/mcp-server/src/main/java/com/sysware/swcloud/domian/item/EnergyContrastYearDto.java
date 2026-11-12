package com.sysware.swcloud.domian.item;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * @Description: 设备能耗查询参数（年统计）
 * @Author: LiYanru
 * @date: 2026-06-14
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "EnergyContrastYearDto", description = "设备能耗查询参数(年)")
public class EnergyContrastYearDto {
    /**
     * 时间点（多值 yyyy）
     */
    @Schema(description = "时间点（多值 yyyy）", example = "[\"2026\"]", requiredMode = REQUIRED)
    private List<String> dateTime;

    /**
     * 设备 id 集合
     */
    @Schema(description = "设备 id 集合", example = "[\"1205445618000990208\"]", requiredMode = REQUIRED)
    private List<String> devIds;
}
