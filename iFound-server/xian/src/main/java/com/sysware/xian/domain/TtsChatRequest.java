package com.sysware.xian.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
@Schema(defaultValue = "语音参数")
public class TtsChatRequest {
    /** 语音操作用户名 */
    @Schema(title = "requesterName",description = "请求人姓名", example = "测试",required = true)
    @NotEmpty
    private String requesterName;
    /** 输入内容 */
    @Schema(title = "input",description = "语音内容", example = "",required = true)
    @NotEmpty
    private String input;
    /** 上一次的数据返回id */
    @Schema(title = "previousResponseId",description = "上一次的数据返回id", example = "123",required = false)
    private String previousResponseId;
}
