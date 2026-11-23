package com.sysware.xian.module.entity.resolver;

import com.sysware.xian.module.entity.catalog.EntityCatalogService;
import com.sysware.xian.module.entity.common.EntityAreaNameNormalizer;
import com.sysware.xian.module.entity.common.EntityMentionNormalizer;
import com.sysware.xian.module.entity.common.EntityTextNormalizer;
import com.sysware.xian.module.entity.common.EntityType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自然语言设备查询的区域实体解析器。
 *
 * LLM 只提供区域/位置候选；本服务必须使用区域实体词典（标准名称、业务别名）
 * 和 Elasticsearch Pinyin Analyzer 确认真实区域 ID。只有唯一候选才允许成为硬过滤。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch", name = "enabled", havingValue = "true")
public class EntityAreaResolverService {

    private final EntityCatalogService catalogService;

    public Resolution resolve(String prompt, String areaCandidate, String locationHint,
                              boolean explicitAreaConstraint, List<String> asrAlternatives) {
        List<Map<String, Object>> vocabulary = catalogService.vocabulary(EntityType.AREA, 5000);
        String candidateText = firstGroundedCandidate(prompt, areaCandidate, locationHint,
                EntityMentionNormalizer.explicitAreaNameMention(prompt));

        // 仅核验 LLM/规则从原句中提取出的、且能在原文中找到依据的地点候选；
        // 不扫描整句中的任意子串，避免把设备名称里的普通词误判成区域。
        if (!hasText(candidateText)) {
            if (explicitAreaConstraint) {
                return notFound(null, "用户明确指定了区域范围，但未能提取区域名称", vocabulary.size());
            }
            return notRequested(vocabulary.size());
        }

        CandidateMatch exactCandidate = findExactCandidate(candidateText, vocabulary);
        if (exactCandidate != null) {
            return toResolution(exactCandidate, true, vocabulary.size());
        }

        // 第一选择是原文中真实出现的区域/位置词；Java 不猜测同音字，由 ES 分析器核验全拼。
        Map<String, Map<String, Object>> candidatesById = new LinkedHashMap<>();
        List<Map<String, Object>> pinyinCandidates =
                catalogService.findPinyinAreaCandidates(candidateText, vocabulary);
        addCandidates(candidatesById, pinyinCandidates);

        boolean matchedByAsrAlternative = false;
        if (asrAlternatives != null) {
            for (String alternative : asrAlternatives) {
                if (!hasText(alternative)) continue;
                CandidateMatch exactAlternative = findExactMention(alternative, vocabulary);
                if (exactAlternative != null) {
                    addCandidates(candidatesById, exactAlternative.candidates());
                    matchedByAsrAlternative = true;
                    continue;
                }

                String alternativeLocation = firstGroundedCandidate(alternative,
                        null, EntityMentionNormalizer.genericDeviceLocationMention(alternative),
                        EntityMentionNormalizer.explicitAreaNameMention(alternative));
                if (!hasText(alternativeLocation)) continue;
                List<Map<String, Object>> alternativePinyin =
                        catalogService.findPinyinAreaCandidates(alternativeLocation, vocabulary);
                if (!alternativePinyin.isEmpty()) {
                    addCandidates(candidatesById, alternativePinyin);
                    matchedByAsrAlternative = true;
                }
            }
        }

        if (candidatesById.size() == 1) {
            Map<String, Object> candidate = candidatesById.values().iterator().next();
            String id = value(candidate.get("entityId"));
            String name = value(candidate.get("name"));
            String method = matchedByAsrAlternative && pinyinCandidates.isEmpty()
                    ? "ASR_ALTERNATIVE" : "PINYIN";
            return new Resolution("FOUND", true, true, false, id, name,
                    candidateText, candidateText, method,
                    List.of(candidate), "通过区域全拼或 ASR 备选唯一确认真实区域", vocabulary.size());
        }
        if (candidatesById.size() > 1) {
            List<Map<String, Object>> candidates = new ArrayList<>(candidatesById.values());
            return new Resolution("AMBIGUOUS", true, false, true, null, null,
                    candidateText, candidateText, matchedByAsrAlternative ? "ASR_AND_PINYIN" : "PINYIN",
                    candidates, "区域名称/别名的拼音或 ASR 备选对应多个真实区域，不能自动选择",
                    vocabulary.size());
        }

        return notFound(candidateText,
                "未找到足够可靠的区域标准名称、业务别名或唯一拼音匹配；保留为普通位置线索",
                vocabulary.size());
    }

    private CandidateMatch findExactMention(String source,
                                            List<Map<String, Object>> vocabulary) {
        if (!hasText(source) || vocabulary == null || vocabulary.isEmpty()) return null;
        String normalizedSource = EntityTextNormalizer.normalize(source);
        String normalizedNumberSource =
                EntityAreaNameNormalizer.normalizeNumberedAreaExpression(normalizedSource);
        int longest = 0;
        Map<String, Map<String, Object>> matched = new LinkedHashMap<>();
        String matchedTerm = null;
        String matchedRaw = null;
        String method = null;

        for (Map<String, Object> item : vocabulary) {
            String id = value(item.get("id"));
            String name = value(item.get("name"));
            if (!hasText(id) || !hasText(name)) continue;
            for (String term : terms(item)) {
                String normalizedTerm = EntityTextNormalizer.normalize(term);
                String normalizedNumberTerm =
                        EntityAreaNameNormalizer.normalizeNumberedAreaExpression(normalizedTerm);
                boolean direct = normalizedSource.contains(normalizedTerm);
                boolean numeral = normalizedNumberSource.contains(normalizedNumberTerm);
                if (normalizedTerm.codePointCount(0, normalizedTerm.length()) < 2
                        || (!direct && !numeral)) continue;
                int termLength = normalizedTerm.codePointCount(0, normalizedTerm.length());
                if (termLength < longest) continue;
                String termMethod = numeral && !direct ? "NUMERAL_NORMALIZATION"
                        : (term.equals(name) ? "EXACT_NAME" : "EXACT_ALIAS");
                String raw = rawOccurrence(source, term);
                if (termLength > longest) {
                    longest = termLength;
                    matched.clear();
                    matchedTerm = term;
                    matchedRaw = raw;
                    method = termMethod;
                }
                matched.putIfAbsent(id, Map.of("entityId", id, "name", name));
                if (method == null) method = termMethod;
                if (matchedRaw == null) matchedRaw = raw;
            }
        }
        if (matched.isEmpty()) return null;
        if (matched.size() > 1) {
            return new CandidateMatch(null, null, matchedRaw == null ? matchedTerm : matchedRaw,
                    method, new ArrayList<>(matched.values()), true);
        }
        Map<String, Object> candidate = matched.values().iterator().next();
        return new CandidateMatch(value(candidate.get("entityId")), value(candidate.get("name")),
                matchedRaw == null ? matchedTerm : matchedRaw, method,
                List.of(candidate), false);
    }

    private CandidateMatch findExactCandidate(String candidateText,
                                               List<Map<String, Object>> vocabulary) {
        if (!hasText(candidateText)) return null;
        String normalized = EntityTextNormalizer.normalize(candidateText);
        String normalizedNumber = EntityAreaNameNormalizer.normalizeNumberedAreaExpression(normalized);
        Map<String, Map<String, Object>> matched = new LinkedHashMap<>();
        String method = null;
        String matchedTerm = candidateText;
        for (Map<String, Object> item : vocabulary) {
            String id = value(item.get("id"));
            String name = value(item.get("name"));
            if (!hasText(id) || !hasText(name)) continue;
            for (String term : terms(item)) {
                String normalizedTerm = EntityTextNormalizer.normalize(term);
                String normalizedNumberTerm =
                        EntityAreaNameNormalizer.normalizeNumberedAreaExpression(normalizedTerm);
                boolean direct = normalized.equals(normalizedTerm);
                boolean numeral = normalizedNumber.equals(normalizedNumberTerm);
                if (!direct && !numeral) continue;
                String termMethod = numeral && !direct ? "NUMERAL_NORMALIZATION"
                        : (term.equals(name) ? "EXACT_NAME" : "EXACT_ALIAS");
                if (method == null) method = termMethod;
                matched.putIfAbsent(id, Map.of("entityId", id, "name", name));
            }
        }
        if (matched.isEmpty()) return null;
        if (matched.size() > 1) {
            return new CandidateMatch(null, null, candidateText, method,
                    new ArrayList<>(matched.values()), true);
        }
        Map<String, Object> entity = matched.values().iterator().next();
        return new CandidateMatch(value(entity.get("entityId")), value(entity.get("name")),
                matchedTerm, method, List.of(entity), false);
    }

    private String firstGroundedCandidate(String prompt, String areaCandidate,
                                          String locationHint, String explicitMention) {
        // 明确的“区域内/范围内”表达优先使用规则抽取出的区域纯名称，
        // 避免 LLM 将“区域内”等约束词一并包含进 areaCandidate。
        List<String> orderedCandidates = new ArrayList<>();
        if (hasText(explicitMention)) orderedCandidates.add(explicitMention.trim());
        if (hasText(areaCandidate)) orderedCandidates.add(areaCandidate.trim());
        if (hasText(locationHint)) orderedCandidates.add(locationHint.trim());
        for (String candidate : orderedCandidates) {
            if (!hasText(candidate)) continue;
            String normalizedPrompt = EntityTextNormalizer.normalize(prompt);
            String normalizedCandidate = EntityTextNormalizer.normalize(candidate);
            if (normalizedPrompt.contains(normalizedCandidate)) return candidate.trim();
            String numberedPrompt =
                    EntityAreaNameNormalizer.normalizeNumberedAreaExpression(normalizedPrompt);
            String numberedCandidate =
                    EntityAreaNameNormalizer.normalizeNumberedAreaExpression(normalizedCandidate);
            if (numberedPrompt.contains(numberedCandidate)) return candidate.trim();
        }
        return null;
    }

    private String rawOccurrence(String source, String term) {
        String normalizedSource = EntityTextNormalizer.normalize(source);
        for (String variant : EntityAreaNameNormalizer.positionPhraseVariants(term)) {
            if (normalizedSource.contains(EntityTextNormalizer.normalize(variant))) return variant;
        }
        if (normalizedSource.contains(EntityTextNormalizer.normalize(term))) return term;
        return term;
    }

    private List<String> terms(Map<String, Object> item) {
        Set<String> result = new LinkedHashSet<>();
        String name = value(item.get("name"));
        if (hasText(name)) result.add(name);
        Object aliases = item.get("aliases");
        if (aliases instanceof List<?> values) {
            for (Object alias : values) {
                String text = value(alias);
                if (hasText(text)) result.add(text);
            }
        }
        return new ArrayList<>(result);
    }

    private void addCandidates(Map<String, Map<String, Object>> target,
                               List<Map<String, Object>> candidates) {
        if (candidates == null) return;
        for (Map<String, Object> candidate : candidates) {
            String id = value(candidate.getOrDefault("entityId", candidate.get("id")));
            String name = value(candidate.get("name"));
            if (hasText(id) && hasText(name)) {
                target.putIfAbsent(id, Map.of("entityId", id, "name", name));
            }
        }
    }

    private Resolution toResolution(CandidateMatch match, boolean requested, int vocabularySize) {
        if (match.ambiguous()) {
            return new Resolution("AMBIGUOUS", requested, false, true, null, null,
                    match.matchedText(), match.matchedText(), match.matchMethod(), match.candidates(),
                    "区域标准名称或业务别名对应多个实体，不能自动选择", vocabularySize);
        }
        return new Resolution("FOUND", requested, true, false, match.id(), match.name(),
                match.matchedText(), match.matchedText(), match.matchMethod(), match.candidates(),
                "已通过区域标准名称、业务别名或数字归一化确认真实区域", vocabularySize);
    }

    private Resolution notFound(String candidateText, String message, int vocabularySize) {
        return new Resolution("NOT_FOUND", hasText(candidateText), false, false, null, null,
                candidateText, candidateText, null, List.of(), message, vocabularySize);
    }

    private Resolution notRequested(int vocabularySize) {
        return new Resolution("NOT_REQUESTED", false, false, false, null, null,
                null, null, null, List.of(), "未识别到区域或位置候选", vocabularySize);
    }

    private String value(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record CandidateMatch(String id, String name, String matchedText,
                                  String matchMethod, List<Map<String, Object>> candidates,
                                  boolean ambiguous) { }

    public record Resolution(String status, boolean requested, boolean matched, boolean ambiguous,
                             String id, String name, String candidateText, String matchedText,
                             String matchMethod, List<Map<String, Object>> candidates,
                             String message, int vocabularySize) {
        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", status);
            result.put("confidence", matched ? "HIGH" : (ambiguous ? "AMBIGUOUS" : "NONE"));
            result.put("matchMethod", matchMethod == null ? "" : matchMethod);
            result.put("inputText", candidateText == null ? "" : candidateText);
            result.put("resolvedId", id == null ? "" : id);
            result.put("resolvedName", name == null ? "" : name);
            result.put("candidates", candidates);
            result.put("message", message);
            return result;
        }
    }
}
