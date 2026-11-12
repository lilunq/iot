package com.sysware.xian.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sysware.xian.domain.SpeakerPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SpeakerPermissionMapper extends BaseMapper<SpeakerPermission> {

    @Select("SELECT * FROM ai_speaker_permission WHERE name = #{name}")
    SpeakerPermission selectByName(String name);
}
