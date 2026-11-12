package com.sysware.core.subAgent;

import com.sysware.core.subAgent.domain.SubAgentRequest;
import com.sysware.swcloud.domian.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 子 agent 委派api接口
 *
 * @Author: LiYanRu
 * @date: 2026-07-24
 **/
@RestController
@Slf4j
@RequestMapping("SubAgent")
@RequiredArgsConstructor
@Tag(name = "子Agent接口", description = "子Agent委派相关接口")
public class SubAgentController {

    private final SubAgentCache subAgentCache;
    private final SubAgentService subAgentService;

    /**
     * 委派能管子 Agent 执行任务
     *
     * @param request 任务与上下文
     * @return 子 Agent 最终回复
     */
    @PostMapping("energy")
    @Operation(summary = "委派能管子Agent", description = "派遣子能源管理Agent执行独立子任务并返回结果")
    public R<String> energyAgent(@RequestBody SubAgentRequest request) {
        String message = subAgentService.energyAgent(request);
        return R.success(message);
    }

    /**
     * 委派站房子 Agent 执行任务
     *
     * @param request 任务与上下文
     * @return 子 Agent 最终回复
     */
    @PostMapping("station")
    @Operation(summary = "委派站房子Agent", description = "派遣子站房Agent执行独立子任务并返回结果")
    public R<String> stationAgent(@RequestBody SubAgentRequest request) {
        String message = subAgentService.stationAgent(request);
        return R.success(message);
    }

    /**
     * 删除子agent的暂存的会话id
     *
     * @return 返回结果
     */
    @DeleteMapping("clear")
    @Operation(summary = "清空子Agent会话缓存", description = "清除SubAgentCache中缓存的会话id")
    public R<String> clearSubAgentSessionCache() {
        subAgentCache.clear();
        return R.success();
    }
}
