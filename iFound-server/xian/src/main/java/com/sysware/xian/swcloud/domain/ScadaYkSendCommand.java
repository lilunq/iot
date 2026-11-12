package com.sysware.xian.swcloud.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.tool.annotation.ToolParam;

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
    @ToolParam(description = "命令包类型，1004表示遥控，1026表示遥调")
    @Schema(description = "命令包类型，决定走遥控还是遥调分支", name = "packageType", example = "1004", requiredMode = REQUIRED)
    private int packageType;
    /**
     * 控制值
     */
    @ToolParam(description = "控制值，开通常传1，关通常传0，遥调时传目标设定值")
    @Schema(description = "控制值", name = "value", example = "1", requiredMode = REQUIRED)
    private double value;
    /**
     * 数据 key，对应遥控和遥调的点位dataKey
     */
    @ToolParam(description = "数据key，对应遥控或遥调点位的dataKey")
    @Schema(description = "数据 key，对应遥控和遥调的点位dataKey", name = "key", example = "1253769574193565696", requiredMode = REQUIRED)
    private String key;
    /**
     * 操作人员的id
     */
    @ToolParam(description = "执行操作的人员ID")
    @Schema(description = "执行操作的人员id，默认9", name = "userId", example = "1024", requiredMode = REQUIRED)
    private String userId = "1024";
    /**
     * 遥控类型枚举，固定为1
     */
    @ToolParam(description = "遥控类型，通常固定为1", required = false)
    @Schema(description = "遥控类型枚举，固定为1", name = "ykType", example = "1", requiredMode = NOT_REQUIRED)
    private int ykType = 1;
}
