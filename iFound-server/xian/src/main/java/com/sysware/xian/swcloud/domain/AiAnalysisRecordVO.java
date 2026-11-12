package com.sysware.xian.swcloud.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Description: AI分析记录视图（resultData反序列化为ProblemAnalysisResult结构）
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisRecordVO {

    @Schema(title = "id", description = "主键", example = "1")
    private Long id;

    @Schema(title = "type", description = "分析类型（problem_analysis=问题库AI分析；report=报告）", example = "problem_analysis")
    private String type;

    @Schema(title = "oData", description = "原始数据JSON（本次分析拉取的问题明细列表）")
    private String oData;

    @Schema(title = "resultData", description = "分析结果（ProblemAnalysisResult结构：统计周期/totalCount/levelStats/categoryStats/areaStats/workFocus/inspectionFocus/reportText/knowledgeAnswer）；report类型为null，正文见resultDataRaw")
    private ProblemAnalysisResult resultData;

    @Schema(title = "resultDataRaw", description = "结果数据原始文本（problem_analysis为JSON字符串；report为AI输出的Markdown报告正文）")
    private String resultDataRaw;

    @Schema(title = "remake", description = "备注（统计周期等）", example = "问题库AI分析(20260701~20260731)")
    private String remake;

    @Schema(title = "createTime", description = "创建时间", example = "2026-08-01T02:00:05")
    private LocalDateTime createTime;
}
