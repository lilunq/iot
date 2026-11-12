package com.sysware.xian.swcloud.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @Description: 大屏应急预案结果（应急预案知识库答案 + 今日值班人员填充）
 * @Author: LiYanRu
 * @date: 2026-08-11
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyPlanScreenResult {

    @Schema(title = "createTime", description = "创建时间")
    private String createTime;

    @Schema(title = "completeTime", description = "完成时间")
    private String completeTime;

    @Schema(title = "operatorName", description = "操作人姓名")
    private String operatorName;

    @Schema(title = "operatorId", description = "操作人ID")
    private String operatorId;

    @Schema(title = "question", description = "提问问题")
    private String question;

    @Schema(title = "emergencyAnswer", description = "应急预案答案（含填充的值班人员名称和电话，原文不删减）")
    private String emergencyAnswer;

    @Schema(title = "dutyPersons", description = "匹配到的今日值班人员列表（名称+电话+岗位）")
    private List<DutyPersonInfo> dutyPersons;

    /**
     * 值班人员信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DutyPersonInfo {

        @Schema(title = "personName", description = "人员姓名", example = "张三")
        private String personName;

        @Schema(title = "phone", description = "联系电话", example = "13800138000")
        private String phone;

        @Schema(title = "positionName", description = "岗位名称（兼容旧接口，新接口映射自 membertype）", example = "领导")
        private String positionName;

        @Schema(title = "positionGroupName", description = "岗位分组名称（兼容旧接口，新接口映射自 depname）", example = "领导班子")
        private String positionGroupName;

        @Schema(title = "memberType", description = "人员类型：领导/副主任/干部", example = "领导")
        private String memberType;

        @Schema(title = "depname", description = "部门名称", example = "领导班子")
        private String depname;

        @Schema(title = "area", description = "区域", example = "西安东站")
        private String area;
    }

    /**
     * 创建实例并填充时间
     */
    public static EmergencyPlanScreenResultBuilder builderWithNow() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return EmergencyPlanScreenResult.builder()
                .createTime(now)
                .completeTime(now);
    }
}
