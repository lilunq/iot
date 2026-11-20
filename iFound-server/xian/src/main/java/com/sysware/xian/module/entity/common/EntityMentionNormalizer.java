package com.sysware.xian.module.entity.common;

import java.util.regex.Pattern;

/**
 * 自然语言实体提及归一化。
 * 负责区分明确的区域约束与可能属于设备名称/别名的位置描述，不负责拼音生成。
 */
public final class EntityMentionNormalizer {
    private static final Pattern EXPLICIT_AREA_MARKER = Pattern.compile(
            ".*(区域内|区域范围内|区域的|范围内|范围的|站台内|站台区域|站厅内|候车层内|楼层内|辖区内|所属区域).*");
    private static final Pattern POSITION_PREFIX = Pattern.compile(
            ".*(在|位于|处于|部署于|安装在).{1,16}(区域|站台|站厅|候车层|楼层|层|楼).*");
    private static final Pattern LOCATION_ONLY = Pattern.compile(
            "^(?:第)?[0-9零〇一二三四五六七八九十]{1,4}(?:候车层|站台|站厅|站|台|层|楼|区)$"
            + "|^.{2,12}(?:候车层|站台|站厅|楼层|区域)$");
    private static final Pattern GENERIC_DEVICE_COLLECTION = Pattern.compile(
            "^.{2,16}的(?:所有|全部)?(?:设备|设施)(?:列表|清单|信息)?$");

    private EntityMentionNormalizer() { }

    /**
     * 只有出现明确区域语义，或“X的设备”这类泛设备集合表达时，才把位置词升级为区域硬过滤。
     * “指挥中心的空调”“实验室的排风机”等具体名称表达默认保留完整短语，不擅自解释成区域。
     */
    public static boolean isExplicitAreaConstraint(String prompt, String areaName, String keyword) {
        if (!hasText(prompt)) return false;
        String source = EntityTextNormalizer.normalize(prompt);
        // “X的设备/设施”是集合查询，可把 X 作为待验证的区域线索；
        // “指挥中心的空调”等具体设备表达仍保留为完整名称，不自动拆成区域。
        return EXPLICIT_AREA_MARKER.matcher(source).matches()
                || POSITION_PREFIX.matcher(source).matches()
                || GENERIC_DEVICE_COLLECTION.matcher(stripQueryFillers(source)).matches();
    }

    /**
     * 提取“X的设备/设施”中的位置线索。返回值只是待验证文本，不能直接作为区域 ID。
     */
    public static String genericDeviceAreaMention(String prompt) {
        if (!hasText(prompt)) return null;
        String source = stripQueryFillers(EntityTextNormalizer.normalize(prompt));
        if (!GENERIC_DEVICE_COLLECTION.matcher(source).matches()) return null;
        String mention = source.replaceFirst("的(?:所有|全部)?(?:设备|设施)(?:列表|清单|信息)?$", "").trim();
        return mention.length() >= 2 ? mention : null;
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
