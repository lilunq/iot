package com.sysware.xian.module.entity.resolver;

import com.sysware.xian.module.entity.catalog.EntityCatalogService;
import com.sysware.xian.module.entity.common.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityAreaResolverServiceTest {

    @Mock
    private EntityCatalogService catalogService;

    @InjectMocks
    private EntityAreaResolverService areaResolverService;

    private List<Map<String, Object>> areas;

    @BeforeEach
    void setUp() {
        areas = List.of(
                Map.of("id", "area-5", "name", "5站台",
                        "aliases", List.of("五站台", "第五站台")),
                Map.of("id", "area-hall", "name", "候车大厅",
                        "aliases", List.of("候车厅"))
        );
        when(catalogService.vocabulary(EntityType.AREA, 5000)).thenReturn(areas);
    }

    @Test
    void resolvesStandardAreaNameFromOriginalTextWithoutExplicitAreaWording() {
        EntityAreaResolverService.Resolution result = areaResolverService.resolve(
                "查询5站台的设备", null, "5站台", false, List.of());

        assertEquals("FOUND", result.status());
        assertTrue(result.matched());
        assertEquals("area-5", result.id());
        assertEquals("5站台", result.name());
        assertEquals("EXACT_NAME", result.matchMethod());
    }

    @Test
    void resolvesAreaBusinessAliasBeforePinyinFallback() {
        EntityAreaResolverService.Resolution result = areaResolverService.resolve(
                "查询五站台的空调", "五站台", "五站台", false, List.of());

        assertEquals("FOUND", result.status());
        assertEquals("area-5", result.id());
        assertEquals("EXACT_ALIAS", result.matchMethod());
    }

    @Test
    void normalizesChineseOrdinalNumberToArabicAreaName() {
        EntityAreaResolverService.Resolution result = areaResolverService.resolve(
                "查询第五站台的设备", null, "第五站台", false, List.of());

        assertEquals("FOUND", result.status());
        assertEquals("area-5", result.id());
        assertEquals("NUMERAL_NORMALIZATION", result.matchMethod());
        assertEquals("第五站台", result.matchedText());
    }

    @Test
    void usesPinyinOnlyAfterNormalNameAndAliasMatchingMisses() {
        when(catalogService.findPinyinAreaCandidates(eq("武站台"), anyList()))
                .thenReturn(List.of(Map.of("id", "area-5", "name", "5站台")));

        EntityAreaResolverService.Resolution result = areaResolverService.resolve(
                "查询武站台的设备", "武站台", "武站台", false, List.of());

        assertEquals("FOUND", result.status());
        assertEquals("area-5", result.id());
        assertEquals("PINYIN", result.matchMethod());
    }

    @Test
    void doesNotChooseBetweenMultiplePinyinAreaCandidates() {
        when(catalogService.findPinyinAreaCandidates(eq("武站台"), anyList()))
                .thenReturn(List.of(
                        Map.of("id", "area-5", "name", "5站台"),
                        Map.of("id", "area-15", "name", "15站台")
                ));

        EntityAreaResolverService.Resolution result = areaResolverService.resolve(
                "查询武站台的设备", "武站台", "武站台", false, List.of());

        assertEquals("AMBIGUOUS", result.status());
        assertFalse(result.matched());
        assertTrue(result.ambiguous());
        assertEquals(2, result.candidates().size());
    }

    @Test
    void keepsUnresolvedImplicitPlaceAsLocationHintInsteadOfBlockingSearch() {
        EntityAreaResolverService.Resolution result = areaResolverService.resolve(
                "查询指挥中心的空调", "指挥中心", "指挥中心", false, List.of());

        assertEquals("NOT_FOUND", result.status());
        assertTrue(result.requested());
        assertFalse(result.matched());
        assertEquals("指挥中心", result.candidateText());
    }

    @Test
    void unresolvedExplicitAreaConstraintCanBeDistinguishedFromImplicitLocation() {
        EntityAreaResolverService.Resolution result = areaResolverService.resolve(
                "查询指挥中心区域内的空调", "指挥中心", "指挥中心", true, List.of());

        assertEquals("NOT_FOUND", result.status());
        assertTrue(result.requested());
        assertFalse(result.matched());
    }

    @Test
    void asrAlternativeCanConfirmOneRealAreaWhenPrimaryTranscriptIsHomophonic() {
        EntityAreaResolverService.Resolution result = areaResolverService.resolve(
                "查询武站台的设备", "武站台", "武站台", false,
                List.of("查询五站台的设备"));

        assertEquals("FOUND", result.status());
        assertEquals("area-5", result.id());
        assertEquals("ASR_ALTERNATIVE", result.matchMethod());
    }
}
