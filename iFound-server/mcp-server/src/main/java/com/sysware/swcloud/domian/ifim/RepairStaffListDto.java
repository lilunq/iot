package com.sysware.swcloud.domian.ifim;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description: 分页查询维修人员的参数实体类
 * @Author: LiYanRu
 * @date: 2026-06-09
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RepairStaffListDto {
    /**
     * 数据列表
     */
    private String records;
    /**
     * 总记录数
     */
    private Long total;
    /**
     * 每页显示条数
     */
    private Integer size;
    /**
     * 当前页码
     */
    private Integer current;
    /**
     * 系统用户ID
     */
    private String userId;
    /**
     * 启用状态
     */
    private String activeFlag;
    /**
     * 技能经验描述（模糊查询）
     */
    private String skillsDesc;
    /**
     * 用户ID集合过滤
     */
    private String userIds;
    /**
     * 组织Cid过滤（支持前缀匹配）
     */
    private String orgCid;
}
