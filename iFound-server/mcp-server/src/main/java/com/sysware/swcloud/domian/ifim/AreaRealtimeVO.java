package com.sysware.swcloud.domian.ifim;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "实时客流分组区域数据")
public class AreaRealtimeVO {

    @Schema(description = "分组编码", example = "PS-SECURITY-GATE")
    private String groupCode;

    @Schema(description = "分组名称（安检口/候车区/检票口/出站口/换乘区/进站厅等）", example = "安检口")
    private String groupName;

    @Schema(description = "分组类型：GATE_AREA=闸口区域，NORMAL_AREA=普通区域", example = "GATE_AREA")
    private String category;

    @Schema(description = "当前区域人数", example = "3")
    private Integer currPeo;
}
