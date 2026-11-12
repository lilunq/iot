package com.sysware.xian.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "StreamChatV2 对话参数")
public class StreamChatV2Request {

    @Schema(description = "用户输入", example = "打开照明", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "message不能为空")
    private String message;

    @Schema(description = "用户名称", example = "开发者", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "userName不能为空")
    private String userName;

    @Schema(description = "用户ID", example = "1221830454739472384", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "userId不能为空")
    private String userId;

    @Schema(description = "来源ID", example = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "pcId不能为空")
    private String pcId;

    @Schema(description = "组织CID", example = "0.001.001.001", defaultValue = "0.001.001.001",requiredMode = Schema.RequiredMode.REQUIRED)
    private String orgCid;
}
