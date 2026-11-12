package com.sysware.xian.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_speaker_permission")
public class SpeakerPermission {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String name;

    private String orgId;

    private Boolean canAwake;

    private Boolean canYk;

    private String embedding;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
