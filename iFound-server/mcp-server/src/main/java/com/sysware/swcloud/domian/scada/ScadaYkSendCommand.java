package com.sysware.swcloud.domian.scada;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * @Description: 设备遥控下方命令实体类
 * @Author: LiYanRu
 * @date: 2026-06-18
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(name = "ScadaYkSendCommand", description = "设备遥控下方命令实体类")
public class ScadaYkSendCommand {
    /**
     * 命令包类型，决定走遥控/遥信（1004）还是遥调/遥测（1026）分支
     */
    @Schema(description = "命令包类型，决定走遥控还是遥调分支", name = "packageType", example = "1004", requiredMode = REQUIRED)
    private int packageType;
    /**
     * 控制值
     */
    @Schema(description = "控制值", name = "value", example = "1", requiredMode = REQUIRED)
    private double value;
    /**
     * 数据 key，对应遥控和遥调的点位dataKey
     */
    @Schema(description = "数据 key，对应遥控和遥调的点位dataKey", name = "key", example = "1253769574193565696", requiredMode = REQUIRED)
    private String key;
    /**
     * 操作人员的id
     */
    @Schema(description = "执行操作的人员id，默认9", name = "userId", example = "9", requiredMode = REQUIRED)
    private String userId;
    /**
     * 遥控类型枚举，固定为1
     */
    @Schema(description = "遥控类型枚举，固定为1", name = "ykType", example = "1", requiredMode = NOT_REQUIRED)
    private int ykType = 1;
}