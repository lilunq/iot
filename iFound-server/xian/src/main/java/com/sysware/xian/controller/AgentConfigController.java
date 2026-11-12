package com.sysware.xian.controller;

import com.sysware.xian.domain.AgentConfig;
import com.sysware.xian.service.AgentConfigService;
import com.sysware.xian.swcloud.domain.AjaxResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Tag(name = "Agent配置管理", description = "Agent配置的增删改查")
public class AgentConfigController {

    private final AgentConfigService agentConfigService;

    @GetMapping("/list")
    @Operation(summary = "获取Agent列表")
    public AjaxResultVO list() {
        return AjaxResultVO.ok().data(agentConfigService.listAll());
    }

    @GetMapping("/available")
    @Operation(summary = "获取可用Agent列表")
    public AjaxResultVO available() {
        return AjaxResultVO.ok().data(agentConfigService.listAvailable());
    }

    @GetMapping("/{agentId}")
    @Operation(summary = "获取Agent详情")
    public AjaxResultVO getById(@PathVariable String agentId) {
        AgentConfig agent = agentConfigService.getById(agentId);
        return agent != null ? AjaxResultVO.ok().data(agent) : AjaxResultVO.error("Agent不存在");
    }

    @PostMapping
    @Operation(summary = "新增Agent")
    public AjaxResultVO add(@RequestBody AgentConfig agent) {
        agentConfigService.save(agent);
        agentConfigService.evictAvailableCache();
        return AjaxResultVO.ok();
    }

    @PutMapping
    @Operation(summary = "更新Agent")
    public AjaxResultVO update(@RequestBody AgentConfig agent) {
        agentConfigService.updateById(agent);
        agentConfigService.evictAvailableCache();
        return AjaxResultVO.ok();
    }

    @DeleteMapping("/{agentId}")
    @Operation(summary = "删除Agent")
    public AjaxResultVO delete(@PathVariable String agentId) {
        agentConfigService.removeById(agentId);
        agentConfigService.evictAvailableCache();
        return AjaxResultVO.ok();
    }
}
