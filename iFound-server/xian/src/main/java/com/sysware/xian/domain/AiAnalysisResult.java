package com.sysware.xian.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI分析结果记录实体（定时任务分析结果持久化）
 */
@Data
@TableName("ai_analysis_result")
public class AiAnalysisResult {

    @Schema(title = "id", description = "主键，自增", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(title = "type", description = "分析类型（problem_analysis=问题库月AI分析,report=报表,env_analysis=环境,emergency-plan=应急预案）", example = "problem_analysis")
    private String type;

    @Schema(title = "oData", description = "原始数据JSON（本次分析拉取的问题明细列表）")
    @TableField("o_data")
    private String oData;

    @Schema(title = "resultData", description = "分析结果JSON（ProblemAnalysisResult序列化内容）")
    private String resultData;

    @Schema(title = "remake", description = "备注（统计周期等）", example = "问题库AI分析(20260701~20260731)")
    private String remake;

    @Schema(title = "createTime", description = "创建时间", example = "2026-08-01T02:00:05")
    private LocalDateTime createTime;
}
