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

    @Test
    void createsAllNumericPositionSpeechVariants() {
        assertEquals(java.util.List.of("5站台", "五站台", "第五站台"),
                EntityAreaNameNormalizer.positionPhraseVariants("5站台"));
        assertEquals(java.util.List.of("第五站台", "5站台", "五站台"),
                EntityAreaNameNormalizer.positionPhraseVariants("第五站台"));
    }

    @Test
    void extractsPositionPhrasesFromDeviceNameAndLocation() {
        assertEquals(java.util.List.of("5站台", "五站台", "第五站台"),
                EntityAreaNameNormalizer.extractPositionVariants("5站台排风机1", ""));
        assertEquals(java.util.List.of("第五站台", "5站台", "五站台"),
                EntityAreaNameNormalizer.extractPositionVariants("排风机1", "设备安装在第五站台"));
    }

    @Test
    void extractsSingleCharacterStationLabelsWithoutTruncatingGenericLayerNames() {
        assertEquals(java.util.List.of("武站台"),
                EntityAreaNameNormalizer.extractPositionVariants("武站台排风机", ""));
        assertEquals(java.util.List.of("西站台"),
                EntityAreaNameNormalizer.extractPositionVariants("西站台设备", ""));
        assertEquals(java.util.List.of(),
                EntityAreaNameNormalizer.extractPositionVariants("地下层排风机", ""));
    }

    @Test
    void recognizesSingleCharacterStationHomophonesForEsPinyinVerification() {
        assertEquals(true, EntityAreaNameNormalizer.isHomophoneNumberedPositionPhrase("武站台"));
        assertEquals(true, EntityAreaNameNormalizer.isHomophoneNumberedPositionPhrase("吾站台"));
        assertEquals(true, EntityAreaNameNormalizer.isHomophoneNumberedPositionPhrase("五站台"));
        assertEquals(true, EntityAreaNameNormalizer.isHomophoneNumberedPositionPhrase("5站台"));
        assertEquals(true, EntityAreaNameNormalizer.isHomophoneNumberedPositionPhrase("第五站台"));
        assertEquals(true, EntityAreaNameNormalizer.isHomophoneNumberedPositionPhrase("第5站台"));
        assertEquals(false, EntityAreaNameNormalizer.isHomophoneNumberedPositionPhrase("站台"));
        assertEquals(false, EntityAreaNameNormalizer.isHomophoneNumberedPositionPhrase("地下层"));
    }

    @Test
    void keepsHomophoneInputForElasticsearchPinyinVerification() {
        assertEquals(java.util.List.of("武站台"),
                EntityAreaNameNormalizer.positionPhraseVariants("武站台"));
        assertEquals(java.util.List.of("吾站台"),
                EntityAreaNameNormalizer.positionPhraseVariants("吾站台"));
    }

    @Test
    void createsArabicAreaVariantForStrippingOriginalVoicePhrase() {
        assertEquals("5站台", EntityAreaNameNormalizer.toArabicNumeralVariant("五站台"));
        assertEquals("5站台", EntityAreaNameNormalizer.toArabicNumeralVariant("第五站台"));
        assertEquals("21层", EntityAreaNameNormalizer.toArabicNumeralVariant("第二十一层"));
    }
}
