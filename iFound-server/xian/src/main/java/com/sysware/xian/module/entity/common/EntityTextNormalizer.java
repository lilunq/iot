package com.sysware.xian.module.entity.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 只负责 Unicode、空白和英文大小写规范化。
 * 不在 Java 层生成全拼或首字母，拼音匹配由 Elasticsearch Pinyin Analyzer 处理。
 */
public final class EntityTextNormalizer {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private EntityTextNormalizer() { }

    public static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
        return WHITESPACE.matcher(normalized).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
}
