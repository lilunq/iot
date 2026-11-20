package com.sysware.xian.module.entity.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private static final Pattern POSITION_MENTION = Pattern.compile(
            "第?(?:[0-9]{1,2}|[零〇一二三四五六七八九十]{1,3}|[\\p{IsHan}])(?:候车层|站台|站厅|站|台|层|楼|区)");

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

    /**
     * 为位置短语生成规范化表达。
     * 例如“5站台” -> [5站台, 五站台, 第五站台]；
     * “第五站台”也会归一到同一组候选。非数字同音词（如“武站台”）保留原文，
     * 由 ES Pinyin Analyzer 与索引中的“五站台”位置词条验证，不在 Java 中猜读音。
     */
    public static List<String> positionPhraseVariants(String value) {
        if (value == null || value.isBlank()) return List.of();
        Set<String> variants = new LinkedHashSet<>();
        String source = value.trim();
        variants.add(source);

        String arabic = toArabicNumeralVariant(source);
        if (arabic != null && !arabic.isBlank()) variants.add(arabic);

        String numberSource = arabic == null || arabic.isBlank() ? source : arabic;
        String chinese = toChineseNumeralVariant(numberSource);
        if (chinese != null && !chinese.isBlank()) {
            variants.add(chinese);
            String withoutOrdinal = chinese.replaceFirst("^第", "");
            if (!withoutOrdinal.isBlank()) {
                variants.add(withoutOrdinal);
                if (POSITION_MENTION.matcher(withoutOrdinal).matches()) {
                    variants.add("第" + withoutOrdinal);
                }
            }
        }

        String normalized = normalizeNumberedAreaExpression(source);
        if (normalized != null && !normalized.isBlank()) variants.add(normalized);
        return new ArrayList<>(variants);
    }

    /**
     * 从设备名称和 location 中提取独立的位置词条，写入 ES 的 device_position_variants。
     * 位置词条单独建立拼音 token，避免查询“武站台”时只命中“站台”两个公共分词。
     */
    public static List<String> extractPositionVariants(String... sourceValues) {
        Set<String> variants = new LinkedHashSet<>();
        if (sourceValues == null) return List.of();
        for (String source : sourceValues) {
            if (source == null || source.isBlank()) continue;
            Matcher matcher = POSITION_MENTION.matcher(source);
            while (matcher.find()) {
                variants.addAll(positionPhraseVariants(matcher.group()));
            }
        }
        return new ArrayList<>(variants);
    }

    public static boolean isNumberedPositionPhrase(String value) {
        return value != null && POSITION_MENTION.matcher(value.trim()).matches();
    }

    /**
     * 单字同音误识别的编号站台表达，例如武站台、吾站台、五站台。
     * 必须再由 ES Pinyin Analyzer 对照索引中的真实位置词条核验，不能仅凭字符形态纠错。
     */
    public static boolean isHomophoneNumberedPositionPhrase(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = EntityTextNormalizer.normalize(value).replaceAll("[\\s，,。！？!?；;：:、]+", "");
        return normalized.matches("^(?:[0-9]{1,2}|[零〇一二三四五六七八九十]{1,3}|[\\p{IsHan}])站台$");
    }

    /**
     * 位置短语单独出现时，不应让 LLM 返回的“站台/设备”等泛词覆盖它。
     * 允许同音字开头，例如“武站台”“吾站台”。
     */
    public static boolean isLocationOnlyDescription(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = EntityTextNormalizer.normalize(value).replaceAll("[\\s，,。！？!?；;：:、]+", "");
        return normalized.codePointCount(0, normalized.length()) >= 3
                && normalized.matches("^.{1,10}(?:候车层|站台|站厅|楼层|站|台|层|楼|区)$");
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
