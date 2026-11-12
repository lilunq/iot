package com.sysware.swcloud.domian;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description: 应急调度/大屏应急预案请求参数
 * @Author: LiYanRu
 * @date: 2026-11-12
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DispatchRequest {

    @Schema(title = "question", description = "用户输入问题（应急预案相关）", requiredMode = Schema.RequiredMode.REQUIRED, example = "旅客滞留三级应急预案")
    private String question;

    @Schema(title = "operatorName", description = "操作人姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    private String operatorName;

    @Schema(title = "operatorId", description = "操作人ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    private String operatorId;

    @Schema(title = "type", description = "处理方式：1=知识库总结+AI完成人员匹配并格式化输出；2=关键词匹配+AI格式化；3=知识库原文片段+AI总结（默认）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "3")
    private Integer type;
}
