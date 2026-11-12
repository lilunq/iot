package com.sysware.xian.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置
 *
 * @author sysware
 */
@Configuration
@MapperScan({"com.sysware.xian.mapper", "com.sysware.xian.module.entity.init.alias"})
public class MyBatisConfig {

//    @Bean
//    public MybatisPlusInterceptor mybatisPlusInterceptor() {
//        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
//        // 添加分页插件，用于 MyBatis-Plus 的分页查询
//        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
//        return interceptor;
//    }
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 配置分页插件，必须放在最后一个位置
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
        pagination.setDbType(DbType.MYSQL);
        pagination.setOverflow(true);
        // 如果配置多个插件, 切记分页最后添加
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}