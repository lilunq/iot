package com.sysware.xian.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 任务列表分页响应结构（仅用于接口文档展示，实际返回为AjaxResultVO同构JSON）
 */
@Data
@Schema(description = "任务列表分页响应")
public class DispatchTaskPageResponse {

    @Schema(name = "Status", description = "状态：Ok-成功", example = "Ok")
    private String status;

    @Schema(description = "状态码：200-成功，其他-失败", example = "200")
    private String code;

    @Schema(description = "提示信息", example = "操作成功")
    private String msg;

    @Schema(description = "分页数据")
    private PageData data;

    @Data
    @Schema(description = "分页数据（MyBatis-Plus Page结构）")
    public static class PageData {

        @Schema(description = "当前页任务记录列表")
        private List<DispatchTask> records;

        @Schema(description = "总记录数", example = "57")
        private Long total;

        @Schema(description = "每页条数", example = "10")
        private Long size;

        @Schema(description = "当前页码", example = "1")
        private Long current;

        @Schema(description = "总页数", example = "6")
        private Long pages;
    }
}
