package com.sysware.xian.module.entity.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 区域名称中的中文/阿拉伯数字等价归一化。
 * 仅处理“5站台/五站台/第五站台”这类数字+位置单位表达，不负责拼音转换。
 */
public final class EntityAreaNameNormalizer {
    private static final Pattern CHINESE_NUMBER_WITH_UNIT =
            Pattern.compile("第?([零〇一二三四五六七八九十]{1,4})(?=(?:站台|候车层|站厅|站|台|层|楼|区))");
    private static final Pattern ARABIC_NUMBER =
            Pattern.compile("(?<![A-Za-z])([0-9]{1,2})(?=(?:站台|候车层|站厅|站|台|层|楼|区))");

    private static final String[] DIGITS = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};

    private EntityAreaNameNormalizer() { }

    /** 将“五站台”“第五站台”归一为“5站台”，便于和真实区域名称作精确等价比较。 */
    public static String normalizeNumberedAreaExpression(String value) {
        String normalized = EntityTextNormalizer.normalize(value);
        Matcher matcher = CHINESE_NUMBER_WITH_UNIT.matcher(normalized);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            Integer number = parseChineseNumber(matcher.group(1));
            if (number == null) continue;
            matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(number)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 为 ES Pinyin Analyzer 生成数字区域的中文读法候选。
     * 例如“5站台”生成“五站台”，“15站台”生成“十五站台”；也可用于设备名称、location 中的数字位置描述。
     */
    public static String toChineseNumeralVariant(String value) {
        if (value == null || value.isBlank()) return value;
        Matcher matcher = ARABIC_NUMBER.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            int number;
            try {
                number = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ex) {
                continue;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(toChineseNumber(number)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 将“五站台”“第五站台”转换为“5站台”，用于从语音原句中准确剥离区域词。
     */
    public static String toArabicNumeralVariant(String value) {
        if (value == null || value.isBlank()) return value;
        Matcher matcher = CHINESE_NUMBER_WITH_UNIT.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            Integer number = parseChineseNumber(matcher.group(1));
            if (number == null) continue;
            matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(number)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static Integer parseChineseNumber(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.startsWith("第") ? value.substring(1) : value;
        if (text.isEmpty()) return null;
        if (!text.contains("十")) {
            if (text.length() == 1) return digitValue(text.charAt(0));
            // “一零”这种逐位读法不强行解释成十，避免错误合并。
            return null;
        }
        if (text.indexOf('十') != text.lastIndexOf('十')) return null;
        String[] parts = text.split("十", -1);
        int tens = parts[0].isEmpty() ? 1 : digitValue(parts[0].charAt(0));
        int ones = parts.length < 2 || parts[1].isEmpty() ? 0 : digitValue(parts[1].charAt(0));
        if (tens < 1 || tens > 9 || ones < 0 || ones > 9) return null;
        return tens * 10 + ones;
    }

    private static int digitValue(char value) {
        return switch (value) {
            case '零', '〇' -> 0;
            case '一' -> 1;
            case '二' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> -1;
        };
    }

    private static String toChineseNumber(int value) {
        if (value < 0 || value > 99) return String.valueOf(value);
        if (value < 10) return DIGITS[value];
        if (value == 10) return "十";
        int tens = value / 10;
        int ones = value % 10;
        return (tens == 1 ? "" : DIGITS[tens]) + "十" + (ones == 0 ? "" : DIGITS[ones]);
    }
}
