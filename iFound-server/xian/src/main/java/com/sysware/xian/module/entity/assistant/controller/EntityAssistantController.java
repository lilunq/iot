package com.sysware.xian.module.entity.assistant.controller;

import com.sysware.xian.module.entity.assistant.EntityAssistantRequest;
import com.sysware.xian.module.entity.assistant.EntityAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 自然语言实体检索：LLM 结构化解析 + ES 多维召回。 */
@RestController
@RequestMapping("/entity-center/assistant")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch", name = "enabled", havingValue = "true")
public class EntityAssistantController {
    private final EntityAssistantService assistantService;

    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody EntityAssistantRequest request) {
        return assistantService.search(request == null ? null : request.getPrompt());
    }
}
