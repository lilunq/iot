package com.sysware.xian.swcloud.cons;

import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 西安东站区域常量池
 *
 * <p>支持区域别名匹配：用户输入别名（如"城市通廊"、"0米层"、"高架层"等）时，
 * 会被归一化为正规区域名称（到达层、出发层、商业层、候车层、地下层等）。
 * 对外返回的始终是正规名称，cascadeId 通过正规名称查表获取。
 *
 * @Author: LiYanRu
 * @date: 2026-08-05
 **/
@NoArgsConstructor
public class SubareaCon {

    // ==================== 区域名称（正规名称） ====================

    /** 西安东站（全站） */
    public static final String SUBAREA_XIAN_EAST_STATION = "西安东站";
    /** 地下层 */
    public static final String SUBAREA_UNDERGROUND = "地下层";
    /** 到达层 */
    public static final String SUBAREA_ARRIVAL = "到达层";
    /** 出发层 */
    public static final String SUBAREA_DEPARTURE = "出发层";
    /** 候车层 */
    public static final String SUBAREA_WAITING = "候车层";
    /** 商业层 */
    public static final String SUBAREA_COMMERCIAL = "商业层";
    /** 给水所 */
    public static final String SUBAREA_WATER_SUPPLY = "给水所";
    /** 出发夹层 */
    public static final String SUBAREA_DEPARTURE_MEZZANINE = "出发夹层";
    /** 到达夹层 */
    public static final String SUBAREA_ARRIVAL_MEZZANINE = "到达夹层";

    // ==================== 区域cascade_id ====================

    /** 西安东站（全站） */
    public static final String CASCADE_ID_XIAN_EAST_STATION = "0.001.001.001-0";
    /** 地下层 */
    public static final String CASCADE_ID_UNDERGROUND = "0.001.001.001-0.001";
    /** 到达层 */
    public static final String CASCADE_ID_ARRIVAL = "0.001.001.001-0.002";
    /** 出发层 */
    public static final String CASCADE_ID_DEPARTURE = "0.001.001.001-0.003";
    /** 候车层 */
    public static final String CASCADE_ID_WAITING = "0.001.001.001-0.004";
    /** 商业层 */
    public static final String CASCADE_ID_COMMERCIAL = "0.001.001.001-0.005";
    /** 给水所 */
    public static final String CASCADE_ID_WATER_SUPPLY = "0.001.001.001-0.006";
    /** 出发夹层 */
    public static final String CASCADE_ID_DEPARTURE_MEZZANINE = "0.001.001.001-0.007";
    /** 到达夹层 */
    public static final String CASCADE_ID_ARRIVAL_MEZZANINE = "0.001.001.001-0.008";

    /** 区域名称与 cascade_id 映射（key 为正规名称） */
    private static final Map<String, String> SUBAREA_CASCADE_MAP = new LinkedHashMap<>();

    /** 区域别名 -> 正规名称（别名不参与对外输出，仅用于匹配归一化） */
    private static final Map<String, String> ALIAS_TO_CANONICAL = new LinkedHashMap<>();

    static {
        SUBAREA_CASCADE_MAP.put(SUBAREA_XIAN_EAST_STATION, CASCADE_ID_XIAN_EAST_STATION);
        SUBAREA_CASCADE_MAP.put(SUBAREA_UNDERGROUND, CASCADE_ID_UNDERGROUND);
        SUBAREA_CASCADE_MAP.put(SUBAREA_ARRIVAL, CASCADE_ID_ARRIVAL);
        SUBAREA_CASCADE_MAP.put(SUBAREA_DEPARTURE, CASCADE_ID_DEPARTURE);
        SUBAREA_CASCADE_MAP.put(SUBAREA_WAITING, CASCADE_ID_WAITING);
        SUBAREA_CASCADE_MAP.put(SUBAREA_COMMERCIAL, CASCADE_ID_COMMERCIAL);
        SUBAREA_CASCADE_MAP.put(SUBAREA_WATER_SUPPLY, CASCADE_ID_WATER_SUPPLY);
        SUBAREA_CASCADE_MAP.put(SUBAREA_DEPARTURE_MEZZANINE, CASCADE_ID_DEPARTURE_MEZZANINE);
        SUBAREA_CASCADE_MAP.put(SUBAREA_ARRIVAL_MEZZANINE, CASCADE_ID_ARRIVAL_MEZZANINE);

        // 到达层别名
        registerAlias(SUBAREA_ARRIVAL, "城市通廊");
        // 出发层别名
        registerAlias(SUBAREA_DEPARTURE, "0米层", "零米层", "轨道层");
        // 商业层别名
        registerAlias(SUBAREA_COMMERCIAL, "高架夹层", "商业夹层");
        // 候车层别名
        registerAlias(SUBAREA_WAITING, "高架层");
    }

    /**
     * 注册区域别名
     *
     * @param canonical 正规区域名称
     * @param aliases   别名列表
     */
    private static void registerAlias(String canonical, String... aliases) {
        if (canonical == null || aliases == null) {
            return;
        }
        for (String alias : aliases) {
            if (alias != null && !alias.isBlank()) {
                ALIAS_TO_CANONICAL.put(alias, canonical);
            }
        }
    }

    /**
     * 根据用户输入内容匹配区域，返回正规区域名称。
     * <p>同时匹配正规名称与别名；命中别名时归一化为对应正规名称。
     * <p>优先匹配名称更长的关键词，避免"出发夹层"被"出发层"子串误匹配。
     *
     * @param input 用户输入内容
     * @return 匹配到的正规区域名称；输入为空或未匹配到返回 null
     */
    public static String matchSubareaName(String input) {
        return matchCanonical(input);
    }

    /**
     * 根据用户输入内容匹配区域，返回对应的 cascade_id。
     * <p>内部复用 {@link #matchCanonical(String)}，不再重复执行匹配逻辑；
     * 匹配到正规名称后直接查表获取 cascadeId。
     * <p>注意：推荐先调用 {@link #matchSubareaName(String)} 拿到正规名称，
     * 再用 {@link #cascadeIdByName(String)} 查 cascadeId，避免对原始输入做两次匹配。
     *
     * @param input 用户输入内容（或已匹配到的正规区域名称）
     * @return 匹配到的 cascade_id；输入为空或未匹配到返回 null
     */
    public static String matchCascadeId(String input) {
        String canonical = matchCanonical(input);
        if (canonical == null) {
            return null;
        }
        return SUBAREA_CASCADE_MAP.get(canonical);
    }

    /**
     * 根据正规区域名称直接查表获取 cascade_id。
     * <p>适用于已通过 {@link #matchSubareaName(String)} 拿到正规名称后的场景，
     * 避免对原始用户输入重复执行匹配逻辑。
     *
     * @param canonicalName 正规区域名称（如 "到达层"、"出发层"）
     * @return 对应的 cascade_id；未找到返回 null
     */
    public static String cascadeIdByName(String canonicalName) {
        if (canonicalName == null || canonicalName.isBlank()) {
            return null;
        }
        return SUBAREA_CASCADE_MAP.get(canonicalName);
    }

    /**
     * 核心：在用户输入中匹配区域（正规名称 + 别名），返回正规名称。
     * <p>将正规名称与别名合并为一个候选集，每个候选都关联到其正规名称，
     * 按候选字符串长度倒序匹配，取首个被 input 包含的候选对应的正规名称。
     *
     * @param input 用户输入内容
     * @return 正规区域名称；未匹配到返回 null
     */
    private static String matchCanonical(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        // 构建候选列表：[候选关键词 -> 正规名称]
        // 正规名称自身的候选指向自身；别名的候选指向其归一化后的正规名称
        List<Map.Entry<String, String>> candidates = new ArrayList<>();
        for (String name : SUBAREA_CASCADE_MAP.keySet()) {
            candidates.add(Map.entry(name, name));
        }
        for (Map.Entry<String, String> alias : ALIAS_TO_CANONICAL.entrySet()) {
            candidates.add(Map.entry(alias.getKey(), alias.getValue()));
        }

        // 按候选关键词长度倒序，避免短词误匹配（如"出发层"命中"出发夹层"）
        return candidates.stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> -e.getKey().length()))
                .filter(e -> input.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
