package com.sysware.xian.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent配置实体
 */
@Data
@TableName("agent_config")
@Schema(description = "Agent配置")
public class AgentConfig {
    @TableId
    @Schema(description = "Agent ID，唯一标识", example = "ops-agent")
    private String agentId;
    @Schema(description = "Agent名称", example = "运维Agent")
    private String agentName;
    @Schema(description = "Agent类型")
    private String agentType;
    @Schema(description = "负责的业务域，多个用逗号分隔", example = "维修工单,设备故障")
    private String businessDomains;
    @Schema(description = "接收任务接口URL，系统Agent派发任务时POST完整任务体", example = "http://172.12.20.30:10098/dispatch/receive")
    private String receiveTaskUrl;
    @Schema(description = "任务查询接口URL")
    private String queryTaskUrl;
    /** 结果校验接口配置 */
    @Schema(description = "结果校验接口URL")
    private String resultCheckUrl;
    /** 结果查询接口配置 */
    @Schema(description = "结果查询接口URL")
    private String resultQueryUrl;
    /** Hermes对话接口URL */
    @Schema(description = "Hermes对话接口URL")
    private String hermesUrl;
    /** 采纳结果回调接口URL（系统Agent采纳/驳回后通知子Agent） */
    @Schema(description = "采纳结果回调接口URL，系统Agent采纳/驳回后向该地址POST通知（字段：id、adoptStatus、adoptUser、decisionContent、decisionData）", example = "http://172.12.20.31:8080/dispatch/adopt-notify")
    private String adoptFeedbackUrl;

    @Schema(description = "是否可用：0-不可用，1-可用", example = "1")
    private Integer available;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
