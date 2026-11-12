package com.sysware.xian.swcloud.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description: AI分析记录分页查询响应
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisPageResult {

    @Schema(title = "records", description = "当前页的分析记录列表（按创建时间倒序），每条含id/type/oData原始问题数据JSON/resultData分析结果（ProblemAnalysisResult结构）/remake备注/createTime")
    private List<AiAnalysisRecordVO> records;

    @Schema(title = "total", description = "符合条件的记录总数", example = "31")
    private long total;
}
