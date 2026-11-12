package com.sysware.xian.swcloud.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * @Description: 问题库分页请求参数
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemPageRequest {

    @Schema(title = "limit", description = "每页条数", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "1000")
    private int limit = 1000;

    @Schema(title = "dateStart", description = "日期起始（yyyyMMdd），默认今日", example = "20260708")
    private String dateStart;

    @Schema(title = "dateEnd", description = "日期截止（yyyyMMdd），默认今日", example = "20260708")
    private String dateEnd;

    /**
     * 填充日期默认值（当日）
     */
    public void fillDefaultDate() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (dateStart == null || dateStart.isBlank()) {
            dateStart = today;
        }
        if (dateEnd == null || dateEnd.isBlank()) {
            dateEnd = today;
        }
    }

    /**
     * 快速构建日期范围请求（limit 使用默认值 1000）
     *
     * @param dateStart 起始日期（yyyyMMdd）
     * @param dateEnd   截止日期（yyyyMMdd）
     * @return ProblemPageRequest 实例
     */
    public static ProblemPageRequest ofRange(String dateStart, String dateEnd) {
        ProblemPageRequest req = new ProblemPageRequest();
        req.setDateStart(dateStart);
        req.setDateEnd(dateEnd);
        return req;
    }
}
