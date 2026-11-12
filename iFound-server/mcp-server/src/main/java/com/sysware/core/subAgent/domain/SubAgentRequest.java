package com.sysware.core.subAgent.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 子 Agent 委派请求参数（通用）
 *
 * @Author: LiYanRu
 * @date: 2026-07-24
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubAgentRequest {

    /**
     * 交给子 Agent 的完整任务描述
     */
    @Schema(description = "交给子Agent的完整任务描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "查询今日总用电量")
    private String task;

    /**
     * 必要上下文，可空
     */
    @Schema(description = "必要的上下文", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "区域：地下层")
    private String context;
}