package com.sysware.xian.module.entity.assistant;

import lombok.Data;

/** 自然语言实体检索请求。 */
@Data
public class EntityAssistantRequest {
    private String prompt;

    /** TEXT 或 VOICE_ASR，仅用于记录输入来源；拼音/错字纠错对两种来源统一生效。 */
    private String inputSource = "TEXT";

    /** 可选 ASR 备选转写文本，作为补充纠错证据，不取代原始文本，也不决定是否启用纠错。 */
    private java.util.List<String> asrAlternatives = java.util.List.of();
}
