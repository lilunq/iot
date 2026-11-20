package com.sysware.xian.module.entity.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自然语言实体提及归一化。
 * 负责区分明确的区域约束与可能属于设备名称/别名的位置描述，不负责拼音生成。
 */
public final class EntityMentionNormalizer {
    private static final Pattern EXPLICIT_AREA_MARKER = Pattern.compile(
            ".*(区域内|区域范围内|区域的|范围内|范围的|站台区域|站厅区域|候车层区域|楼层区域|辖区内|所属区域).*");
    private static final Pattern LOCATION_ONLY = Pattern.compile(
            "^(?:第)?[0-9零〇一二三四五六七八九十]{1,4}(?:候车层|站台|站厅|站|台|层|楼|区)$"
            + "|^.{1,12}(?:候车层|站台|站厅|楼层|区域)$");
    private static final Pattern GENERIC_DEVICE_COLLECTION = Pattern.compile(
            "^.{2,16}的(?:所有|全部)?(?:设备|设施)(?:列表|清单|信息)?$");
    private static final Pattern EXPLICIT_AREA_NAME = Pattern.compile(
            "^(.{2,16}?)(?:区域范围内|区域内|区域的|范围内|范围的|站台区域|站厅区域|候车层区域|楼层区域|辖区内)");

    private EntityMentionNormalizer() { }

    /**
     * 只有用户明确表达区域/范围语义时才生成区域硬过滤。
     * “X的设备”“位于X的设备”中的 X 都先作为位置线索，不推断为区域。
     */
    public static boolean isExplicitAreaConstraint(String prompt, String areaName, String keyword) {
        if (!hasText(prompt)) return false;
        String source = EntityTextNormalizer.normalize(prompt);
        return EXPLICIT_AREA_MARKER.matcher(source).matches();
    }

    /**
     * 从明确的“X区域内/范围内”表达中提取待核验的区域名称。
     * 只提取文本，不产生区域 ID；ID 仍必须来自精确词典或唯一拼音匹配。
     */
    public static String explicitAreaNameMention(String prompt) {
        if (!hasText(prompt)) return null;
        String source = stripQueryFillers(EntityTextNormalizer.normalize(prompt));
        source = source.replaceFirst("^(?:在|位于|处于|部署于|安装在)", "");
        Matcher matcher = EXPLICIT_AREA_NAME.matcher(source);
        if (matcher.find()) {
            String mention = matcher.group(1).trim();
            return mention.length() >= 2 ? mention : null;
        }
        Matcher owner = Pattern.compile("所属区域(?:为|是)?(.{2,16})").matcher(source);
        if (owner.find()) {
            String mention = owner.group(1).replaceFirst("的.*$", "").replaceFirst("(?:内的|中的|设备|设施).*$", "").trim();
            return mention.length() >= 2 ? mention : null;
        }
        return null;
    }

    /**
     * 提取“X的设备/设施”中的位置描述，用于搜索设备名称、别名和 location；
     * 该值不是区域名称，也不能生成区域过滤条件。
     */
    public static String genericDeviceLocationMention(String prompt) {
        if (!hasText(prompt)) return null;
        String source = stripQueryFillers(EntityTextNormalizer.normalize(prompt));
        if (GENERIC_DEVICE_COLLECTION.matcher(source).matches()) {
            String mention = source.replaceFirst("的(?:所有|全部)?(?:设备|设施)(?:列表|清单|信息)?$", "").trim();
            return mention.length() >= 2 ? mention : null;
        }

        // 对“武站台的排风机”“吾站台的空调”提取“的”前面的独立位置短语。
        // “指挥中心的空调”等完整名称不符合站台/楼层位置形态，仍保留完整短语处理。
        int separator = source.indexOf("的");
        if (separator > 1 && separator <= 16) {
            String mention = source.substring(0, separator).trim();
            if (EntityAreaNameNormalizer.isLocationOnlyDescription(mention)) return mention;
        }
        return null;
    }

    private static String stripQueryFillers(String value) {
        return value.replaceFirst("^(?:麻烦)?(?:请)?(?:帮我)?(?:查询|查找|搜索|查一下|查|找一下|找|看看|我想|我需要)+", "")
                .replaceFirst("^(?:一下|帮我|请)+", "")
                .replaceAll("[，,。！？!?；;：:、\\s]+$", "")
                .trim();
    }

    /**
     * 提取可作为完整设备名称候选的短语，保留“指挥中心的空调”中的位置描述和“的”。
     * 泛化的“5站台的设备”不会被当作设备名称。
     */
    public static String fullDevicePhrase(String prompt) {
        if (!hasText(prompt)) return "";
        String value = EntityTextNormalizer.normalize(prompt);
        value = value.replaceFirst("^(?:麻烦)?(?:请)?(?:帮我)?(?:查询|查找|搜索|查一下|查|找一下|找|看看)", "");
        value = value.replaceFirst("^(?:我想|我需要|帮我查询|帮我查找)", "");
        value = value.replaceFirst("(?:当前|现在)?(?:的)?(?:运行状态|故障信息|报警信息)$", "");
        value = value.replaceFirst("(?:设备列表|设备清单|设备信息|设备)$", "");
        value = value.replaceFirst("[，,。！？!?；;：:、\\s]+$", "");
        value = value.replaceFirst("的+$", "");
        value = value.trim();
        if (value.length() < 2 || LOCATION_ONLY.matcher(value).matches()) return "";
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
