package com.sysware.xian.swcloud.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @Description: 应急调度结果
 * @Author: LiYanRu
 * @date: 2026-07-16
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchResult {

    @Schema(title = "createTime", description = "创建时间")
    private String createTime;

    @Schema(title = "operatorName", description = "操作人姓名")
    private String operatorName;

    @Schema(title = "operatorId", description = "操作人ID")
    private String operatorId;

    @Schema(title = "emergencyAnswer", description = "应急预案问答的answer")
    private String emergencyAnswer;

    @Schema(title = "dutyPersonId", description = "值班人员ID")
    private Object dutyPersonId;

    @Schema(title = "positionGroupName", description = "值班岗位分组名称")
    private String positionGroupName;

    @Schema(title = "dutyPersonName", description = "值班人姓名")
    private String dutyPersonName;

    @Schema(title = "completeTime", description = "完成时间")
    private String completeTime;

    @Schema(title = "backup", description = "备用字段")
    private String backup;

    @Schema(title = "reference", description = "知识库引用的原文片段")
    private JsonNode reference;

    /**
     * 创建实例并填充时间
     */
    public static DispatchResultBuilder builderWithNow() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return DispatchResult.builder()
                .createTime(now)
                .completeTime(now);
    }
}
