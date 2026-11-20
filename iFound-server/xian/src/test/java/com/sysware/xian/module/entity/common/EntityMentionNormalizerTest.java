package com.sysware.xian.module.entity.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityMentionNormalizerTest {

    @Test
    void genericDeviceCollectionTreatsLocationAsAreaConstraint() {
        assertTrue(EntityMentionNormalizer.isExplicitAreaConstraint(
                "查询5站台的设备", "5站台", "设备"));
        assertTrue(EntityMentionNormalizer.isExplicitAreaConstraint(
                "武站台的设备", "武站台", "设备"));
    }

    @Test
    void explicitAreaLanguageTreatsLocationAsHardConstraint() {
        assertTrue(EntityMentionNormalizer.isExplicitAreaConstraint(
                "查询在5站台区域内的排风机", "5站台", "排风机"));
    }

    @Test
    void concreteNameLocationPhraseIsNotAutomaticallyAnAreaFilter() {
        assertFalse(EntityMentionNormalizer.isExplicitAreaConstraint(
                "查询指挥中心的空调", "指挥中心", "空调"));
        assertFalse(EntityMentionNormalizer.isExplicitAreaConstraint(
                "查询智慧中心的空调", "智慧中心", "空调"));
    }

    @Test
    void fullDevicePhraseKeepsEmbeddedLocationDescription() {
        assertEquals("指挥中心的空调",
                EntityMentionNormalizer.fullDevicePhrase("帮我查询指挥中心的空调设备"));
        assertEquals("智慧中心的空调",
                EntityMentionNormalizer.fullDevicePhrase("查询智慧中心的空调"));
        assertEquals("",
                EntityMentionNormalizer.fullDevicePhrase("查询5站台的设备"));
    }
}
