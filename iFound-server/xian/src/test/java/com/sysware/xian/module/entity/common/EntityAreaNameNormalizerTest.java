package com.sysware.xian.module.entity.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityAreaNameNormalizerTest {
    @Test
    void normalizesArabicAndChineseStationNumbers() {
        assertEquals("5站台", EntityAreaNameNormalizer.normalizeNumberedAreaExpression("五站台"));
        assertEquals("5站台", EntityAreaNameNormalizer.normalizeNumberedAreaExpression("第五站台"));
        assertEquals("5站台", EntityAreaNameNormalizer.normalizeNumberedAreaExpression("5站台"));
    }

    @Test
    void normalizesNumbersFromTenToNinetyNine() {
        assertEquals("15站台", EntityAreaNameNormalizer.normalizeNumberedAreaExpression("十五站台"));
        assertEquals("21站台", EntityAreaNameNormalizer.normalizeNumberedAreaExpression("第二十一站台"));
    }

    @Test
    void createsChineseReadingCandidateForPinyinAnalysis() {
        assertEquals("五站台", EntityAreaNameNormalizer.toChineseNumeralVariant("5站台"));
        assertEquals("十五站台", EntityAreaNameNormalizer.toChineseNumeralVariant("15站台"));
        assertEquals("第十二层", EntityAreaNameNormalizer.toChineseNumeralVariant("第12层"));
    }
}
