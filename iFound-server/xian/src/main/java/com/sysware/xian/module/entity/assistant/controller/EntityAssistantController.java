package com.sysware.xian.module.entity.assistant.controller;

import com.sysware.xian.module.entity.assistant.EntityAssistantRequest;
import com.sysware.xian.module.entity.assistant.EntityAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 自然语言实体检索：LLM 结构化解析 + ES 多维召回。 */
@RestController
@Tag(name = "Agent协作接口", description = "")
@RequestMapping("/entity-center/assistant")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch", name = "enabled", havingValue = "true")
public class EntityAssistantController {
    private final EntityAssistantService assistantService;

    @Operation(summary = "自然语言实体解析检索接口", description = "解析用户自然语言中的实体类型、关键词及设备过滤条件，并返回实体检索结果。")
    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody EntityAssistantRequest request) {
        return assistantService.search(request == null ? null : request.getPrompt(),
                request == null ? "TEXT" : request.getInputSource(),
                request == null ? java.util.List.of() : request.getAsrAlternatives());
    }
}
