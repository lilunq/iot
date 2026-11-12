package com.sysware.xian.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "通用数据分析请求参数")
public class CommonAnalysisRequest {
    /** 输入内容，可以是任意文本 */
    @Schema(title = "content", description = "输入内容，可以是任意文本。AI将智能识别并分析，支持包含区域/设备信息", example = "请分析一下一站台的运行情况", required = true)
    private String content;
}