package com.sysware.swcloud.domian.ifim;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "车站实时客流概览")
public class StationRealtimeVO {

    @Schema(description = "实时在站总人数", example = "931")
    private Long stationCurrPeo;

    @Schema(description = "各分组区域实时人数列表（安检口/候车区/检票口/出站口/换乘区/进站厅等）")
    private List<AreaRealtimeVO> groupItems;
}
