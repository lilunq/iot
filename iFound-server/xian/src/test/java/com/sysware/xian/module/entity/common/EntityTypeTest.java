package com.sysware.xian.module.entity.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityTypeTest {
    @Test
    void usesStableBusinessIdentifiersFromSpecification() {
        assertEquals("cascade_cid", EntityType.AREA.getBusinessIdField());
        assertEquals("element_key", EntityType.SYSTEM.getBusinessIdField());
        assertEquals("id", EntityType.DEVICE_TYPE.getBusinessIdField());
        assertEquals("id", EntityType.DEVICE.getBusinessIdField());
        assertEquals("id", EntityType.CAMERA.getBusinessIdField());
    }

    @Test
    void usesIndependentIndexesForDevicesAndCameras() {
        assertEquals("station_devices", EntityType.DEVICE.getIndexAlias());
        assertEquals("station_cameras", EntityType.CAMERA.getIndexAlias());
    }
}
