package com.sysware.swcloud.domian.ifim;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "累计进出站人数")
public class StationInOutTotalVO {

    @Schema(description = "累计进站人数")
    private Integer entryCount;

    @Schema(description = "累计出站人数")
    private Integer exitCount;
}
