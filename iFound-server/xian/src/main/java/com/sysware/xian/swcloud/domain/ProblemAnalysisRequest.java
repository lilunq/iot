package com.sysware.xian.swcloud.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * @Description: 问题库AI分析请求参数
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemAnalysisRequest {

    @Schema(title = "dateStart", description = "统计起始日期（yyyyMMdd），默认本月1号", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "20260701")
    private String dateStart;

    @Schema(title = "dateEnd", description = "统计截止日期（yyyyMMdd），默认今日", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "20260729")
    private String dateEnd;

    /**
     * 填充日期默认值（本月1号 ~ 今日）
     */
    public void fillDefaultDate() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate today = LocalDate.now();
        if (dateStart == null || dateStart.isBlank()) {
            dateStart = today.withDayOfMonth(1).format(fmt);
        }
        if (dateEnd == null || dateEnd.isBlank()) {
            dateEnd = today.format(fmt);
        }
    }
}
