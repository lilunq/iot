package com.sysware.swcloud.domian.ifim;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "当前安检人数统计")
public class SecurityCheckCurrentVO {

    @Schema(description = "站内人数", example = "927")
    private Integer allCount;

    @Schema(description = "已安检人数", example = "863")
    private Integer checkedCount;

    @Schema(description = "未安检人数", example = "64")
    private Integer uncheckedCount;
}
