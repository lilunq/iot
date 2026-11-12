package com.sysware.xian.swcloud.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description: 问题库AI分析结果
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemAnalysisResult {

    @Schema(title = "dateStart", description = "统计起始日期（yyyyMMdd）", example = "20260701")
    private String dateStart;

    @Schema(title = "dateEnd", description = "统计截止日期（yyyyMMdd）", example = "20260729")
    private String dateEnd;

    @Schema(title = "totalCount", description = "统计周期内问题总数", example = "8")
    private long totalCount;

    @Schema(title = "levelStats", description = "按问题级别（problemLevel）统计，数量降序")
    private List<ProblemStatItem> levelStats;

    @Schema(title = "categoryStats", description = "按问题类别（problemCategory）统计，数量降序")
    private List<ProblemStatItem> categoryStats;

    @Schema(title = "areaStats", description = "按问题区域（problemArea）统计，数量降序")
    private List<ProblemStatItem> areaStats;

    @Schema(title = "workFocus", description = "AI分析的下月工作重点列表（一是/二是风格的条目）")
    private List<String> workFocus;

    @Schema(title = "inspectionFocus", description = "AI分析的检查重点列表（按岗位/类别分条）")
    private List<String> inspectionFocus;

    @Schema(title = "reportText", description = "AI生成的完整通报风格文本（含下月工作重点与检查重点两部分）")
    private String reportText;

    @Schema(title = "knowledgeAnswer", description = "应急预案知识库针对TOP问题类别的答复（AI分析依据之一）")
    private String knowledgeAnswer;
}
