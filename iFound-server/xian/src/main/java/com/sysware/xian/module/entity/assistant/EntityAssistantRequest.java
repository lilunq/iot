package com.sysware.xian.module.entity.assistant;

import lombok.Data;

/** 自然语言实体检索请求。 */
@Data
public class EntityAssistantRequest {
    private String prompt;

    /** TEXT 或 VOICE_ASR；缺省保持旧调用的普通文本语义。 */
    private String inputSource = "TEXT";

    /** 可选 ASR 备选转写文本，只有明确匹配真实区域词典时才用于纠错。 */
    private java.util.List<String> asrAlternatives = java.util.List.of();
}
