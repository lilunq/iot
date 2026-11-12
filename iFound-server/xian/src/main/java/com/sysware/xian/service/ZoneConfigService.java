package com.sysware.xian.service;

import com.sysware.xian.domain.ZoneConfig;
import com.sysware.xian.mapper.ZoneConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ZoneConfigService {
    
    @Autowired
    private ZoneConfigMapper zoneConfigMapper;
    
    public List<ZoneConfig> getByOrgId(String orgId) {
        return zoneConfigMapper.selectByOrgId(orgId);
    }
    
    public ZoneConfig getById(String id) {
        return zoneConfigMapper.selectById(id);
    }
    
    public ZoneConfig getByCascadeId(String cascadeId) {
        return zoneConfigMapper.selectByCascadeId(cascadeId);
    }
    
    public int add(ZoneConfig zoneConfig) {
        return zoneConfigMapper.insert(zoneConfig);
    }
    
    public int update(ZoneConfig zoneConfig) {
        return zoneConfigMapper.update(zoneConfig);
    }
    
    public int delete(Long id) {
        return zoneConfigMapper.deleteById(id);
    }
}
