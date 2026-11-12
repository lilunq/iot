package com.sysware.core.subAgent;

import com.sysware.core.subAgent.domain.SubAgentRequest;
import com.sysware.swcloud.domian.R;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 子 agent 工具：将特定任务委派给能管子 agent，并同步返回结果。
 *
 * @Author: LiYanRu
 * @date: 2026-07-23
 **/
@Component
@RequiredArgsConstructor
public class SubAgentTool {

    private final SubAgentService agentService;

    /**
     * 委派能管子 Agent（平铺参数，供 MCP/LLM 调用）。
     */
    @Tool(name = "energyAgent", description = "派遣能源管理Agent执行独立子任务并返回结果。适用于能耗查询、能管分析等可隔离的子任务。")
    public R<String> energyAgent(
            @ToolParam(description = "交给子Agent的完整任务描述") String task,
            @ToolParam(description = "必要的上下文", required = false) String context) {

        String message = agentService.energyAgent(task, context);
        return R.success(message);
    }


    /**
     * 委派站房子 Agent（平铺参数）。
     */
    @Tool(name = "stationAgent", description = "派遣车站运营Agent执行站房运营任务并返回结果。"
            + "task 参数仅填以下四项之一，直接填入，无须额外说明："
            + "今日运营情况怎么样、明日运营情况怎么样、客流情况、车辆情况。")
    public R<String> stationAgent(
            @ToolParam(description = "任务关键词，仅填以下之一：今日运营情况怎么样 / 明日运营情况怎么样 / 客流情况 / 车辆情况") String task,
            @ToolParam(description = "必要的上下文", required = false) String context) {
        String message = agentService.stationAgent(task, context);
        return R.success(message);

    }
}
