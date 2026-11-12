package com.sysware.xian.swcloud.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description: 问题库统计项（维度取值 + 数量）
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemStatItem {

    @Schema(title = "name", description = "统计维度取值（如A1类问题、劳动安全、候车厅）", example = "劳动安全")
    private String name;

    @Schema(title = "count", description = "该取值的问题数量", example = "5")
    private long count;
}
