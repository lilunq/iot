package com.sysware.xian.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sysware.xian.domain.AgentConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentConfigMapper extends BaseMapper<AgentConfig> {

    @Select("SELECT * FROM agent_config WHERE available = 1")
    List<AgentConfig> selectAvailableAgents();

    @Select("SELECT * FROM agent_config WHERE FIND_IN_SET(#{domain, business_domains) > 0 AND available = 1 LIMIT 1")
    AgentConfig selectByBusinessDomain(@Param("domain") String domain);
}
