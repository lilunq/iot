package com.sysware.xian.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sysware.xian.domain.DispatchTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface DispatchTaskMapper extends BaseMapper<DispatchTask> {

    @Select("SELECT * FROM dispatch_task WHERE subarea_cid = #{subareaCid} ORDER BY create_time DESC")
    List<DispatchTask> selectBySubareaCid(@Param("subareaCid") String subareaCid);

    @Select("SELECT * FROM dispatch_task WHERE creator_agent_id = #{agentId} ORDER BY create_time DESC")
    List<DispatchTask> selectByCreatorAgentId(@Param("agentId") String agentId);

    @Select("SELECT * FROM dispatch_task WHERE dispatched = 0 ORDER BY create_time DESC")
    List<DispatchTask> selectUndispatchedTasks();

    @Select("SELECT * FROM dispatch_task " +
            "WHERE (create_time >= #{startTime} AND create_time <= #{endTime}) " +
            "OR (dispatched_time >= #{startTime} AND dispatched_time <= #{endTime}) " +
            "ORDER BY create_time ASC")
    List<DispatchTask> selectEvolutionTasks(@Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);

    @Select("SELECT daily.statDate, SUM(daily.taskCount) AS taskCount, " +
            "SUM(daily.dispatchedCount) AS dispatchedCount " +
            "FROM (" +
            "SELECT DATE(create_time) AS statDate, COUNT(*) AS taskCount, 0 AS dispatchedCount " +
            "FROM dispatch_task WHERE create_time >= #{startTime} AND create_time <= #{endTime} " +
            "GROUP BY DATE(create_time) " +
            "UNION ALL " +
            "SELECT DATE(COALESCE(dispatched_time, create_time)) AS statDate, 0 AS taskCount, COUNT(*) AS dispatchedCount " +
            "FROM dispatch_task WHERE dispatched = 1 " +
            "AND COALESCE(dispatched_time, create_time) >= #{startTime} " +
            "AND COALESCE(dispatched_time, create_time) <= #{endTime} " +
            "GROUP BY DATE(COALESCE(dispatched_time, create_time))" +
            ") daily GROUP BY daily.statDate ORDER BY daily.statDate ASC")
    List<Map<String, Object>> selectDailyEvolutionStats(@Param("startTime") LocalDateTime startTime,
                                                         @Param("endTime") LocalDateTime endTime);

    @Select("SELECT agents.statDate, GROUP_CONCAT(DISTINCT agents.agentName SEPARATOR '||') AS agentNames " +
            "FROM (" +
            "SELECT DATE(create_time) AS statDate, COALESCE(NULLIF(creator_agent_name, ''), creator_agent_id) AS agentName " +
            "FROM dispatch_task WHERE create_time >= #{startTime} AND create_time <= #{endTime} " +
            "UNION ALL " +
            "SELECT DATE(create_time) AS statDate, COALESCE(NULLIF(target_agent_name, ''), target_agent_id) AS agentName " +
            "FROM dispatch_task WHERE create_time >= #{startTime} AND create_time <= #{endTime} " +
            "UNION ALL " +
            "SELECT DATE(COALESCE(dispatched_time, create_time)) AS statDate, COALESCE(NULLIF(creator_agent_name, ''), creator_agent_id) AS agentName " +
            "FROM dispatch_task WHERE dispatched = 1 AND COALESCE(dispatched_time, create_time) >= #{startTime} AND COALESCE(dispatched_time, create_time) <= #{endTime} " +
            "UNION ALL " +
            "SELECT DATE(COALESCE(dispatched_time, create_time)) AS statDate, COALESCE(NULLIF(target_agent_name, ''), target_agent_id) AS agentName " +
            "FROM dispatch_task WHERE dispatched = 1 AND COALESCE(dispatched_time, create_time) >= #{startTime} AND COALESCE(dispatched_time, create_time) <= #{endTime}" +
            ") agents WHERE agents.agentName IS NOT NULL " +
            "GROUP BY agents.statDate ORDER BY agents.statDate ASC")
    List<Map<String, Object>> selectDailyAgentStats(@Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime);

    @Select("<script>" +
            "SELECT daily.statDate, daily.agentName, SUM(daily.taskCount) AS taskCount, " +
            "SUM(daily.dispatchedCount) AS dispatchedCount " +
            "FROM (" +
            "SELECT DATE(create_time) AS statDate, COALESCE(NULLIF(creator_agent_name, ''), creator_agent_id) AS agentName, COUNT(*) AS taskCount, 0 AS dispatchedCount " +
            "FROM dispatch_task WHERE create_time &gt;= #{startTime} AND create_time &lt;= #{endTime} " +
            "<if test='dispatched != null'>AND dispatched = #{dispatched}</if> " +
            "GROUP BY DATE(create_time), COALESCE(NULLIF(creator_agent_name, ''), creator_agent_id) " +
            "UNION ALL " +
            "SELECT DATE(create_time) AS statDate, COALESCE(NULLIF(target_agent_name, ''), target_agent_id) AS agentName, COUNT(*) AS taskCount, 0 AS dispatchedCount " +
            "FROM dispatch_task WHERE create_time &gt;= #{startTime} AND create_time &lt;= #{endTime} " +
            "<if test='dispatched != null'>AND dispatched = #{dispatched}</if> " +
            "GROUP BY DATE(create_time), COALESCE(NULLIF(target_agent_name, ''), target_agent_id) " +
            "UNION ALL " +
            "SELECT DATE(COALESCE(dispatched_time, create_time)) AS statDate, COALESCE(NULLIF(creator_agent_name, ''), creator_agent_id) AS agentName, 0 AS taskCount, COUNT(*) AS dispatchedCount " +
            "FROM dispatch_task WHERE dispatched = 1 AND COALESCE(dispatched_time, create_time) &gt;= #{startTime} AND COALESCE(dispatched_time, create_time) &lt;= #{endTime} " +
            "GROUP BY DATE(COALESCE(dispatched_time, create_time)), COALESCE(NULLIF(creator_agent_name, ''), creator_agent_id) " +
            "UNION ALL " +
            "SELECT DATE(COALESCE(dispatched_time, create_time)) AS statDate, COALESCE(NULLIF(target_agent_name, ''), target_agent_id) AS agentName, 0 AS taskCount, COUNT(*) AS dispatchedCount " +
            "FROM dispatch_task WHERE dispatched = 1 AND COALESCE(dispatched_time, create_time) &gt;= #{startTime} AND COALESCE(dispatched_time, create_time) &lt;= #{endTime} " +
            "GROUP BY DATE(COALESCE(dispatched_time, create_time)), COALESCE(NULLIF(target_agent_name, ''), target_agent_id)" +
            ") daily WHERE daily.agentName IS NOT NULL " +
            "GROUP BY daily.statDate, daily.agentName ORDER BY daily.statDate ASC" +
            "</script>")
    List<Map<String, Object>> selectDailyAgentDetailStats(@Param("startTime") LocalDateTime startTime,
                                                          @Param("endTime") LocalDateTime endTime,
                                                          @Param("dispatched") Integer dispatched);

    @Update("UPDATE dispatch_task SET dispatched = 2, dispatched_time = NOW(), dispatched_user = #{dispatchedUser} , adopt_status = 2, adopt_time = NOW(), adopt_user = #{dispatchedUser}  WHERE id = #{id}")
    int markCancelled(@Param("id") String id, @Param("dispatchedUser") String dispatchedUser);

    @Update("UPDATE dispatch_task SET dispatched = 1, dispatched_time = #{dispatchedTime}, dispatched_user = #{dispatchedUser} , adopt_status = 1, adopt_time = #{dispatchedTime}, adopt_user = #{dispatchedUser} WHERE id = #{id}")
    int markReceived(@Param("id") String id, @Param("dispatchedTime") LocalDateTime dispatchedTime, @Param("dispatchedUser") String dispatchedUser);

    /**
     * 查询运维Agent未完成的任务（target_agent_id='ops-agent' 且 result_execute_status='0'）
     */
    @Select("SELECT * FROM dispatch_task WHERE target_agent_id = 'ops-agent' AND result_execute_status = '0' ORDER BY create_time DESC")
    List<DispatchTask> selectUnfinishedOpsAgentTasks();

    /**
     * 按分区分组统计未完成任务数量，同时获取有未完成任务的分区列表（单次查询）
     * 用于大屏AI洞察：先获取分区列表和各分区count总数，再轮询查top3
     */
    @Select("SELECT subarea_cid, COUNT(*) AS cnt " +
            "FROM dispatch_task " +
            "WHERE result_execute_status = '0' AND subarea_cid IS NOT NULL AND subarea_cid != '' " +
            "GROUP BY subarea_cid " +
            "ORDER BY subarea_cid ASC")
    List<Map<String, Object>> selectSubareaPendingCounts();

    /**
     * 查询指定分区最新3条未完成任务（result_execute_status='0'）
     * 用于大屏AI洞察：轮询各分区获取最新3条任务详情
     */
    @Select("SELECT id, subarea_cid, problem_desc, decision_content, creator_agent_id, target_agent_id, create_time " +
            "FROM dispatch_task " +
            "WHERE result_execute_status = '0' AND subarea_cid = #{subareaCid} " +
            "ORDER BY create_time DESC " +
            "LIMIT 3")
    List<DispatchTask> selectTopPendingBySubarea(@Param("subareaCid") String subareaCid);
}
