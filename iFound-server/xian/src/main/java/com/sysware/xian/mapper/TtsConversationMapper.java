package com.sysware.xian.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sysware.xian.domain.TtsConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 语音对话记录Mapper
 */
@Mapper
public interface TtsConversationMapper extends BaseMapper<TtsConversation> {
}
