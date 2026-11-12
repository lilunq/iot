package com.sysware.xian.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分析派发任务实体
 */
@Data
@TableName("dispatch_task")
@Schema(description = "分析派发任务（Agent协作任务）")
public class DispatchTask {
    /** 任务ID */
    @TableId(type = IdType.AUTO)
    @Schema(description = "任务ID，创建时自动生成", example = "1946063744774477696")
    private String id;

    /** 分区CID */
    @Schema(description = "区域级联ID", example = "0.001.001.001-0")
    private String subareaCid;

    /** 创建者AgentId */
    @Schema(description = "创建者Agent ID，系统Agent固定为commander，子Agent上报时为子Agent ID", example = "commander")
    private String creatorAgentId;

    /** 创建者名称 */
    @Schema(description = "创建者Agent名称", example = "系统Agent")
    private String creatorAgentName;

    /** 父任务ID */
    @Schema(description = "父任务ID，无父任务可不传")
    private String parentId;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间，格式yyyy-MM-dd HH:mm:ss，不传自动生成", example = "2025-06-21 10:30:00")
    private LocalDateTime createTime;

    /** 业务域 */
    @Schema(description = "业务域", example = "客流压力/维修工单/能耗")
    private String businessDomain;

    /** 思考过程概览 */
    @Schema(description = "思考概述，AI分析过程摘要")
    private String thinkingOverview;

    /** 等级 */
    @Schema(description = "问题等级：高/中/低", example = "高")
    private String level;

    /** 问题描述 */
    @Schema(description = "问题描述", example = "TX22消防泵房三合一传感器(id:1226586217819148288)故障")
    private String problemDesc;

    /** 数据快照JSON */
    @Schema(description = "数据快照，问题发现时的原始数据(JSON字符串)")
    private String dataSnapshot;

    /** 问题总结 */
    @Schema(description = "问题总结", example = "TX22传感器故障，需维修")
    private String problemSummary;

    /** 是否派发 0-未派发 1-已派发 2-已取消 */
    @Schema(description = "派发状态：0-未派发，1-已派发，2-已取消", example = "0")
    private Integer dispatched;

    /** 派发时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "派发时间，格式yyyy-MM-dd HH:mm:ss，派发成功时自动记录")
    private LocalDateTime dispatchedTime;

    /** 派发人员 */
    @Schema(description = "派发人姓名", example = "张三")
    private String dispatchedUser;

    /** 决策内容 */
    @Schema(description = "决策内容，处置方案描述", example = "给设备TX22消防泵房三合一传感器派单维修，预计2小时内完成")
    private String decisionContent;

    /** 决策数据：维修工单存纯设备ID字符串 */
    @Schema(description = "决策数据。维修工单为纯设备ID字符串如\"1226586210521059328\"", example = "1226586210521059328")
    private String decisionData;

    /** 预期成效 */
    @Schema(description = "预期成效", example = "恢复传感器正常运行，保障监控数据准确")
    private String expectedEffect;

    /** 要派发的子AgentId */
    @Schema(description = "目标子Agent ID", example = "ops-agent")
    private String targetAgentId;

    /** 要派发的子Agent名称 */
    @Schema(description = "目标子Agent名称，不传可由Agent配置自动补全", example = "设备运维Agent")
    private String targetAgentName;

    /** 任务类型: hint-提示, collaboration-协同方案(Agent协作), normal-正常/误报 */
    @Schema(description = "任务类型：hint-提示(仅存档展示)，collaboration-协同方案(需用户采纳/驳回)，normal-正常/误报(入库即完结)", example = "collaboration")
    private String taskType;

    /** 采纳状态：0未采纳，1已采纳，2已驳回 */
    @Schema(description = "采纳状态：0-未采纳，1-已采纳，2-已驳回", example = "1")
    private String adoptStatus;

    /** 用户采纳时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "采纳/驳回时间，格式yyyy-MM-dd HH:mm:ss，操作时自动记录")
    private LocalDateTime adoptTime;

    /** 采纳人 */
    @Schema(description = "采纳人/驳回人姓名", example = "李四")
    private String adoptUser;

    /** 结果执行状态 无，1成功，2失败 */
    @Schema(description = "执行结果状态：0-未完成，1-成功，2-失败", example = "1")
    private Integer resultExecuteStatus;

    /** 结果执行人 */
    @Schema(description = "执行人姓名或执行Agent标识", example = "ops-agent")
    private String resultExecuteUser;

    /** 结果数据 */
    @Schema(description = "执行结果原始数据(JSON字符串)")
    private String resultData;

    /** 结果描述 */
    @Schema(description = "结果描述", example = "工单XXX于2025-06-21 10:30:00派发给张三")
    private String resultDesc;

    /** 用户反馈 */
    @Schema(description = "用户反馈")
    private String userFeedback;

    /** 完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "完成时间，格式yyyy-MM-dd HH:mm:ss", example = "2025-06-21 12:30:00")
    private LocalDateTime completeTime;

    /** 备注 */
    @Schema(description = "备注", example = "已确认现场无人作业")
    private String remark;
}
