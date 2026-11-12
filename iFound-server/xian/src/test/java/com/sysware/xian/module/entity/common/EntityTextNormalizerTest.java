package com.sysware.xian.module.entity.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityTextNormalizerTest {
    @Test
    void normalizesUnicodeWidthWhitespaceAndEnglishCase() {
        assertEquals("a b 冷水机组", EntityTextNormalizer.normalize(" Ａ  B　冷水机组 "));
    }

    @Test
    void nullBecomesEmptyString() {
        assertEquals("", EntityTextNormalizer.normalize(null));
    }
}
