package com.sysware.swcloud.domian;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(name = "素材表实体", description = "素材表实体description")
@Data
public class YcYxParam {
    @Schema(description = "名称")
    public String name;
    @Schema(description = "遥测列表")
    public List<DataParam> ycList;
    @Schema(description = "遥信列表")
    public List<DataParam> yxList;
}

