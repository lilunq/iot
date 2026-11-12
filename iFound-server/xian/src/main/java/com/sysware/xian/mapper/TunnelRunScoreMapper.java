package com.sysware.xian.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sysware.xian.domain.TunnelRunScore;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface TunnelRunScoreMapper extends BaseMapper<TunnelRunScore> {

    @Select("SELECT * FROM tunnel_run_score WHERE org_id = #{orgId} AND create_time >= #{startTime} AND create_time <= #{endTime} ORDER BY create_time ASC")
    List<TunnelRunScore> selectByOrgAndTimeRange(@Param("orgId") String orgId, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT * FROM tunnel_run_score WHERE org_id = #{orgId} ORDER BY create_time DESC LIMIT 1")
    TunnelRunScore selectLatest(@Param("orgId") String orgId);

    @Select("SELECT DATE(create_time) AS statDate, " +
            "ROUND(AVG(total_score), 2) AS averageScore, " +
            "MIN(total_score) AS minScore, MAX(total_score) AS maxScore, " +
            "COUNT(total_score) AS scorePoints " +
            "FROM tunnel_run_score " +
            "WHERE org_id = #{orgId} AND create_time >= #{startTime} AND create_time <= #{endTime} " +
            "GROUP BY DATE(create_time) ORDER BY statDate ASC")
    List<Map<String, Object>> selectDailyScoreStats(@Param("orgId") String orgId,
                                                     @Param("startTime") LocalDateTime startTime,
                                                     @Param("endTime") LocalDateTime endTime);

    @Delete("DELETE FROM tunnel_run_score WHERE create_time < #{beforeTime}")
    int deleteByCreateTimeBefore(@Param("beforeTime") LocalDateTime beforeTime);
}
