package com.sysware.xian.swcloud.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * @Description: 值班人员分页请求参数
 * @Author: LiYanRu
 * @date: 2026-07-16
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RosterPageRequest {

    @Schema(title = "page", description = "页码", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private int page = 1;

    @Schema(title = "limit", description = "每页条数", requiredMode = Schema.RequiredMode.REQUIRED, example = "30")
    private int limit = 30;

    @Schema(title = "dutyDateStart", description = "值班日期起始（yyyyMMdd），默认今日", example = "20260716")
    private String dutyDateStart;

    @Schema(title = "dutyDateEnd", description = "值班日期截止（yyyyMMdd），默认今日", example = "20260716")
    private String dutyDateEnd;

    @Schema(title = "personName", description = "值班人员姓名（右模糊）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "张")
    private String personName;

    /**
     * 填充日期默认值（当日）
     */
    public void fillDefaultDate() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (dutyDateStart == null || dutyDateStart.isBlank()) {
            dutyDateStart = today;
        }
        if (dutyDateEnd == null || dutyDateEnd.isBlank()) {
            dutyDateEnd = today;
        }
    }
}
