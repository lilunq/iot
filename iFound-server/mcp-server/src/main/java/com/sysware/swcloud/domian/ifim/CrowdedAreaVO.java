package com.sysware.swcloud.domian.ifim;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "拥堵区域实时客流数据")
public class CrowdedAreaVO {

    @Schema(description = "区域编码", example = "HC-XB-4")
    private String areaCode;

    @Schema(description = "区域名称", example = "候车大厅西北区-4")
    private String areaName;

    @Schema(description = "当前区域人数", example = "50")
    private Integer currPeo;

    @Schema(description = "人数报警阈值", example = "80")
    private Integer peopleNumThreshold;

    @Schema(description = "人数拥挤度（实时人数/人数阈值，百分比，0~100）", example = "100")
    @JsonAlias("peopleNumCongestion")
    private Integer congestion;
}
