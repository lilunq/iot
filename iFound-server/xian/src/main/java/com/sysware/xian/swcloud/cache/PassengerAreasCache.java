package com.sysware.xian.swcloud.cache;

import com.sysware.swcloud.common.core.vo.Res;
import com.sysware.xian.domain.videoAnalytics.PassengerAreaVO;
import com.sysware.xian.swcloud.fegin.MCPXClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.apachecommons.CommonsLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用于缓存所有客流区域详情信息
 *
 * @Author: LiYanRu
 * @date: 2026-08-08
 **/
@Component
@Slf4j
public class PassengerAreasCache {
    private final MCPXClient mcpxClient;

    public PassengerAreasCache(MCPXClient mcpxClient) {
        this.mcpxClient = mcpxClient;
        refreshCache();
    }

    /**
     * 刷新客流区域缓存：拉取接口数据并按区域编码(code)转为 map 放入缓存
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void refreshCache() {
        Res<List<PassengerAreaVO>> res;
        try {
            res = mcpxClient.getALLPassengerAreas();
        } catch (Exception e) {
            log.warn("刷新客流区域缓存失败：调用 MCP 接口异常：{}", e.getMessage());
            return;
        }
        if (res == null) {
            log.warn("刷新客流区域缓存失败：MCP 接口返回空响应");
            return;
        }
        List<PassengerAreaVO> data = res.getData();
        if (data == null || data.isEmpty()) {
            log.warn("刷新客流区域缓存失败：MCP 接口未返回有效数据，res={}", res);
            return;
        }
        try {
            // 将客流区域列表按区域编码(code)转为 map 后放入缓存
            Map<String, PassengerAreaVO> map = data.stream()
                    .collect(Collectors.toMap(PassengerAreaVO::getCode, Function.identity()));
            cache.putAll(map);
            log.info("客流区域缓存刷新成功，共 {} 个区域", map.size());
        } catch (Exception e) {
            log.error("刷新客流区域缓存失败：转换数据异常：{}", e.getMessage(), e);
        }
    }

    /**
     * 区域code->客流区域信息
     */
    private final Map<String, PassengerAreaVO> cache = new ConcurrentHashMap<>();

    /**
     * 获取缓存的区域信息
     *
     * @param key 区域code
     * @return 区域信息
     */
    public PassengerAreaVO getArea(String key) {
        return cache.get(key);
    }
}
