package com.sysware.xian.mapper;

import com.sysware.xian.domain.ZoneConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ZoneConfigMapper {
    
    @Select("SELECT * FROM zone_config WHERE org_id = #{orgId} ORDER BY sort_order ASC")
    List<ZoneConfig> selectByOrgId(@Param("orgId") String orgId);
    
    @Select("SELECT * FROM zone_config WHERE id = #{id}")
    ZoneConfig selectById(@Param("id") String id);
    
    @Select("SELECT * FROM zone_config WHERE cascade_id = #{cascadeId}")
    ZoneConfig selectByCascadeId(@Param("cascadeId") String cascadeId);
    
    @Insert("INSERT INTO zone_config (id, org_id, parent_id, name, cascade_id, coord_x, coord_y, coord_z, sort_order, status, remark) " +
            "VALUES (#{id}, #{orgId}, #{parentId}, #{name}, #{cascadeId}, #{coordX}, #{coordY}, #{coordZ}, #{sortOrder}, #{status}, #{remark})")
    int insert(ZoneConfig zoneConfig);
    
    @Update("UPDATE zone_config SET parent_id=#{parentId}, name=#{name}, cascade_id=#{cascadeId}, " +
            "coord_x=#{coordX}, coord_y=#{coordY}, coord_z=#{coordZ}, sort_order=#{sortOrder}, " +
            "status=#{status}, remark=#{remark} WHERE id=#{id}")
    int update(ZoneConfig zoneConfig);
    
    @Delete("DELETE FROM zone_config WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
