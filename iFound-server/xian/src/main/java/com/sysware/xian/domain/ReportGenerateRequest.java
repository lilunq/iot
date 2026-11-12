package com.sysware.xian.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 报表生成请求参数
 */
@Data
@Schema(description = "报表生成请求")
public class ReportGenerateRequest {

    @Schema(description = "查看运行报表", requiredMode = Schema.RequiredMode.REQUIRED, example = "生成运行报告")
    private String input;
}
