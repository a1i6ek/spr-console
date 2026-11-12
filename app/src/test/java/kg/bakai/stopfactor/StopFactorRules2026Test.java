package kg.bakai.stopfactor;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.spr.core.kib.KibService;
import kg.bakai.spr.core.kib.dto.ConsolidationDto;
import kg.bakai.spr.core.kib.dto.GeneralInfoDto;
import kg.bakai.spr.core.kib.dto.IndividualReport;
import kg.bakai.spr.core.kib.dto.OverdueForStopFactor;
import kg.bakai.spr.core.kib.dto.response.CustomReport;
import kg.bakai.spr.core.ob.dto.response.TotalDebtFinalResponse;
import kg.bakai.stopfactor.dto.StopFactorDecision;
import kg.bakai.stopfactor.service.StopFactorService;
import kg.bakai.stopfactor.service.impl.StopFactorServiceImpl;
import kg.bakai.stopfactor.entity.StopFactor;
import kg.bakai.stopfactor.entity.StopFactorCondition;
import kg.bakai.stopfactor.entity.StopFactorGroup;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.repository.StopFactorCheckRepository;
import kg.bakai.stopfactor.repository.StopFactorGroupRepository;
import kg.bakai.stopfactor.dto.StopFactorProjection;
import kg.bakai.stopfactor.report.CreditHistoryEvents.OverdueMonth;
import kg.bakai.stopfactor.report.KibOverdueHistoryReader.OverdueHistory;
import kg.bakai.stopfactor.report.KibOverdueHistoryReader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static kg.bakai.common.enums.ProductSegment.MSB;
import static kg.bakai.common.enums.ProductSegment.RETAIL;
import static kg.bakai.stopfactor.enums.StopFactorStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Overdue history rules 2026 (changelog 1.4.3) against the migrated database: the stop factors are the ones of the
 * requirements (data/new-stopfactors-products.txt) with their products, every stop factor gives its zone on both sides
 * of every threshold, and each product gets only its stop factors.
 * <p>
 * {@link #RULES} is written from the requirement, not from the changelog, so a typo in either one fails the test.
 * Every test runs in a transaction that is rolled back.
 */
@SpringBootTest
@Transactional
class StopFactorRules2026Test {

    private static final LocalDate REQUEST_DATE = LocalDate.of(2026, 11, 3);

    /** Products of changelog 1.2 (data/products.csv) */
    private static final long RETAIL_PRODUCT = 1;   // Online кредит
    private static final long MSB_PRODUCT = 5;      // Бизнес лимит
    /** A product the requirements do not name: it gets only the stop factors of every product */
    private static final long OTHER_PRODUCT = -13;

    private static final String REQUIREMENTS = "data/new-stopfactors-products.txt";
    private static final List<String> RETAIL_PRODUCTS =
            List.of("Online кредит", "Тунук онлайн", "Онлайн ИФЦ", "Тумар", "Кредитная карта", "BakAi сплит");
    private static final List<String> MSB_PRODUCTS = List.of("Онлайн кредит для ИП", "Бизнес Лимит");

    /**
     * One overdue history stop factor: count of overdue months in the period and overdue days range, UNDERWRITER
     * when the count is greater than underwriterAbove, REJECTED when greater than rejectedAbove. segment null - every
     * product; maxDays / underwriterAbove / rejectedAbove null - none (a range without limit).
     */
    private record Rule(ProductSegment segment, int periodFrom, int periodTo, int minDays, Integer maxDays,
                        Integer underwriterAbove, Integer rejectedAbove) {

        StopFactorStatus zone(int count) {
            if (rejectedAbove != null && count > rejectedAbove) return REJECTED;
            if (underwriterAbove != null && count > underwriterAbove) return UNDERWRITER;
            return APPROVED;
        }

        int maxDaysOrFar() {
            return maxDays != null ? maxDays : minDays + 365;
        }

        @Override
        public String toString() {
            return (segment != null ? segment : "every product") + " months " + periodFrom + ".." + periodTo + ", days " + minDays + ".." + (maxDays != null ? maxDays : "")
                    + ", UNDERWRITER > " + underwriterAbove + ", REJECTED > " + rejectedAbove;
        }
    }

    private static Rule retail(int periodFrom, int periodTo, int minDays, Integer maxDays, Integer underwriterAbove, Integer rejectedAbove) {
        return new Rule(RETAIL, periodFrom, periodTo, minDays, maxDays, underwriterAbove, rejectedAbove);
    }

    private static Rule msb(int periodFrom, int periodTo, int minDays, Integer maxDays, Integer underwriterAbove, Integer rejectedAbove) {
        return new Rule(MSB, periodFrom, periodTo, minDays, maxDays, underwriterAbove, rejectedAbove);
    }

    private static Rule everyProduct(int periodFrom, int periodTo, int minDays, Integer maxDays, Integer underwriterAbove, Integer rejectedAbove) {
        return new Rule(null, periodFrom, periodTo, minDays, maxDays, underwriterAbove, rejectedAbove);
    }

    // The requirement "Блок «Стоп-факторы»", read as: OK up to underwriterAbove, yellow zone up to rejectedAbove, reject
    // above; in the order of data/new-stopfactors-products.txt
    private static final List<Rule> RULES = List.of(
            // Розница и ИФЦ, с 1 по 3 и с 4 по 6 месяц: 1..10 дней <= 5 Ок, 6+ отказ; 11..20: 1 Ок, 2 жёлтая, 3+ отказ; 21+: 0
            retail(1, 3, 1, 10, null, 5), retail(1, 3, 11, 20, 1, 2), retail(1, 3, 21, null, null, 0),
            retail(4, 6, 1, 10, null, 5), retail(4, 6, 11, 20, 1, 2), retail(4, 6, 21, null, null, 0),
            // с 7 по 12: 1..5 без ограничений; 6..10 <= 5 Ок, 6..8 жёлтая, 9+ отказ; 11..20: 1 жёлтая, 2+ отказ; 21+ отказ
            retail(7, 12, 1, 5, null, null),
            retail(7, 12, 6, 10, 5, 8), retail(7, 12, 11, 20, 0, 1), retail(7, 12, 21, null, null, 0),
            // с 13 по 24: 1..10 без ограничений; 11..20 <= 7 Ок, 8..10 жёлтая, 11+ отказ; 21..30: 1 жёлтая, 2+ отказ; 31+ отказ
            retail(13, 24, 1, 10, null, null),
            retail(13, 24, 11, 20, 7, 10), retail(13, 24, 21, 30, 0, 1), retail(13, 24, 31, null, null, 0),

            // с 25 по 36, все продукты: 1..30 без ограничений; 31..60: 1 жёлтая, 2+ отказ; 61+ отказ
            everyProduct(25, 36, 1, 30, null, null), everyProduct(25, 36, 31, 60, 0, 1), everyProduct(25, 36, 61, null, null, 0),

            // МСБ, с 1 по 3 и с 4 по 6 месяц: 1..10 дней <= 5 Ок, 6..10 жёлтая, 11+ отказ; 11..20: 1 Ок, 2 жёлтая, 3+ отказ; 21+: 0
            msb(1, 3, 1, 10, 5, 10), msb(1, 3, 11, 20, 1, 2), msb(1, 3, 21, null, null, 0),
            msb(4, 6, 1, 10, 5, 10), msb(4, 6, 11, 20, 1, 2), msb(4, 6, 21, null, null, 0),
            // с 7 по 12: 1..10 без ограничений; 11..20 <= 7 Ок, 8..10 жёлтая, 11+ отказ; 21..30: 1 жёлтая, 2+ отказ; 31+ отказ
            msb(7, 12, 1, 10, null, null),
            msb(7, 12, 11, 20, 7, 10), msb(7, 12, 21, 30, 0, 1), msb(7, 12, 31, null, null, 0)
    );

    @MockitoBean
    private KibService kibService;
    @MockitoBean
    private KibOverdueHistoryReader overdueHistoryReader;

    @Autowired
    private StopFactorService stopFactorService;
    @Autowired
    private StopFactorGroupRepository groupRepository;
    @Autowired
    private StopFactorCheckRepository checkRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<OverdueMonth> overdueMonths = List.of();
    private long scoringId = -2_000_000;

    /** Stop factor id of every stop factor found in the database. */
    private Map<Rule, Long> factorIds;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("INSERT INTO public.product (id, name, description) VALUES (?, 'Тест другой продукт', 'Тест')",
                OTHER_PRODUCT);

        when(kibService.getOverdueByPeriodAndCount(any(), any())).thenReturn(OverdueForStopFactor.NONE);
        when(overdueHistoryReader.read(any(), any()))
                .thenAnswer(invocation -> overdueMonths != null ? new OverdueHistory(overdueMonths, null) : null);

        factorIds = stopFactorsInDatabase();
    }

    // ------------------------------------------------------------------------------------------------
    // What changelog 1.4 created
    // ------------------------------------------------------------------------------------------------

    @Test
    void theDatabaseHoldsExactlyTheRequiredOverdueStopFactors() {
        assertThat(factorIds.keySet()).containsExactlyInAnyOrderElementsOf(RULES);
    }

    @Test
    void theOverdueHistoryIsOneGroupWithTheStopFactorsAndProductsOfTheRequirements() throws Exception {
        StopFactorGroup overdue = groupRepository.findCurrent(13).orElseThrow();
        List<Requirement> requirements = overdueRequirements();

        assertThat(overdue.getName()).isEqualTo("Непрерывная просроченная задолженность");
        assertThat(overdue.isActive()).isTrue();
        assertThat(overdue.getFactors()).hasSize(27).hasSameSizeAs(requirements);
        for (int i = 0; i < requirements.size(); i++) {
            StopFactor factor = overdue.getFactors().get(i);
            Requirement requirement = requirements.get(i);
            assertThat(factor.getOrderNumber()).isEqualTo(i + 1);
            assertThat(factor.getName()).isEqualTo(requirement.name());
            assertThat(factor.getId()).as(requirement.name()).isEqualTo(factorIds.get(RULES.get(i)));
            if (requirement.products().size() == 8) {
                // every product: no limit, so a product added later is checked too
                assertThat(factor.getProductIds()).as(requirement.name()).isEmpty();
                assertThat(factor.getSegment()).isNull();
            } else {
                assertThat(productNames(factor.getProductIds())).as(requirement.name())
                        .containsExactlyInAnyOrderElementsOf(requirement.products().stream().map(String::toLowerCase).toList());
                assertThat(factor.getSegment()).isEqualTo(requirement.products().equals(MSB_PRODUCTS) ? MSB : RETAIL);
            }
        }
    }

    @Test
    void legacyOverdueStopFactorsAreReplaced() {
        List<StopFactorProjection> rows = check(RETAIL_PRODUCT, 0, 0);

        List<Long> legacy = java.util.stream.LongStream.rangeClosed(13, 34).boxed().toList();
        assertThat(rows).extracting(StopFactorProjection::getFactorId).doesNotContainAnyElementsOf(legacy);
        assertThat(rows).extracting(StopFactorProjection::getFactorId).containsAll(factorIds(RETAIL));
        // the legacy overdue history stays version 1 of group #13, closed by the 2026 rules
        assertThat(groupRepository.findVersions(13)).extracting(StopFactorGroup::getVersionNumber).containsExactly(2, 1);
        assertThat(groupRepository.findVersions(13).get(1)).satisfies(g -> {
            assertThat(g.isCurrent()).isFalse();
            assertThat(g.getFactors()).extracting(StopFactor::getId).containsExactlyInAnyOrderElementsOf(legacy);
        });
    }

    @Test
    void newCreditsRejectFrom4AndOpenCreditsFrom8() {
        assertThat(result(check(RETAIL_PRODUCT, 3, 7), 11)).isEqualTo(APPROVED);
        assertThat(result(check(RETAIL_PRODUCT, 3, 7), 12)).isEqualTo(APPROVED);
        assertThat(result(check(MSB_PRODUCT, 4, 7), 11)).isEqualTo(REJECTED);
        assertThat(result(check(MSB_PRODUCT, 3, 8), 12)).isEqualTo(REJECTED);
    }

    // ------------------------------------------------------------------------------------------------
    // Thresholds and ranges of every stop factor
    // ------------------------------------------------------------------------------------------------

    @Test
    void everyStopFactorGivesItsZoneOnBothSidesOfEachThreshold() {
        for (Rule rule : RULES) {
            Set<Integer> counts = new TreeSet<>(List.of(0, 1, 2));
            if (rule.rejectedAbove() != null) {
                counts.addAll(List.of(rule.rejectedAbove(), rule.rejectedAbove() + 1));
            }
            if (rule.underwriterAbove() != null) {
                counts.addAll(List.of(rule.underwriterAbove(), rule.underwriterAbove() + 1));
            }
            for (int count : counts) {
                // overdue months on the edges of the stop factor: first and last month, fewest and most overdue days
                List<OverdueMonth> inRule = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    inRule.add(new OverdueMonth(i % 2 == 0 ? rule.periodFrom() : rule.periodTo(),
                            i % 3 == 0 ? rule.minDays() : rule.maxDaysOrFar()));
                }
                overdueMonths = inRule;
                StopFactorProjection row = row(check(productOf(rule.segment()), 0, 0), factorIds.get(rule));

                assertThat(row.getCheckResult()).as(rule + ", " + count + " overdue months").isEqualTo(rule.zone(count));
                assertThat(row.getCheckedValue()).as(rule.toString()).isEqualTo(count + ".0");
            }
        }
    }

    @Test
    void overdueMonthsJustOutsideAStopFactorAreNotCounted() {
        for (Rule rule : RULES) {
            List<OverdueMonth> outside = new ArrayList<>();
            outside.add(new OverdueMonth(rule.periodTo() + 1, rule.minDays()));
            if (rule.periodFrom() > 1) outside.add(new OverdueMonth(rule.periodFrom() - 1, rule.minDays()));
            if (rule.minDays() > 1) outside.add(new OverdueMonth(rule.periodFrom(), rule.minDays() - 1));
            if (rule.maxDays() != null) outside.add(new OverdueMonth(rule.periodTo(), rule.maxDays() + 1));
            overdueMonths = outside;

            StopFactorProjection row = row(check(productOf(rule.segment()), 0, 0), factorIds.get(rule));

            assertThat(row.getCheckedValue()).as(rule.toString()).isEqualTo("0.0");
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Segments
    // ------------------------------------------------------------------------------------------------

    @Test
    void eachProductIsCheckedOnlyByItsStopFactors() {
        assertThat(checkedIds(RETAIL_PRODUCT)).containsAll(factorIds(RETAIL)).containsAll(factorIds(null))
                .doesNotContainAnyElementsOf(factorIds(MSB));
        assertThat(checkedIds(MSB_PRODUCT)).containsAll(factorIds(MSB)).containsAll(factorIds(null))
                .doesNotContainAnyElementsOf(factorIds(RETAIL));
    }

    @Test
    void aProductTheRequirementsDoNotNameGetsOnlyTheStopFactorsOfEveryProduct() {
        assertThat(checkedIds(OTHER_PRODUCT)).containsAll(factorIds(null))
                .doesNotContainAnyElementsOf(factorIds(RETAIL)).doesNotContainAnyElementsOf(factorIds(MSB));
    }

    @Test
    void scoringWithoutProductIsRetail() {
        assertThat(checkedIds(null)).containsAll(factorIds(RETAIL)).containsAll(factorIds(null))
                .doesNotContainAnyElementsOf(factorIds(MSB));
    }

    @Test
    void sameHistoryGetsTheDecisionOfTheSegment() {
        // 6 overdue months of 5 days in months 1..3: Retail rejects from 6, MSB has a yellow zone 6..10
        overdueMonths = Collections.nCopies(6, new OverdueMonth(2, 5));

        assertThat(decision(RETAIL_PRODUCT)).isEqualTo(new StopFactorDecision(REJECTED, null));
        assertThat(decision(MSB_PRODUCT)).isEqualTo(new StopFactorDecision(UNDERWRITER, null));
    }

    @Test
    void cleanHistoryIsApprovedInBothSegments() {
        // 1..5 days (MSB 1..10) in months 7..12, 1..10 days in months 13..24 and 1..30 days in 25..36 have no limit
        overdueMonths = List.of(new OverdueMonth(8, 5), new OverdueMonth(15, 10), new OverdueMonth(30, 30));

        assertThat(decision(RETAIL_PRODUCT)).isEqualTo(new StopFactorDecision(APPROVED, null));
        assertThat(decision(MSB_PRODUCT)).isEqualTo(new StopFactorDecision(APPROVED, null));
    }

    // ------------------------------------------------------------------------------------------------
    // Without the overdue overdue months of SPR
    // ------------------------------------------------------------------------------------------------

    @Test
    void withoutOverdueMonthsFromSprANewRangeGoesToTheUnderwriter() {
        overdueMonths = null;
        Rule newRange = retail(1, 3, 11, 20, 1, 2);   // no legacy counter for it

        StopFactorProjection row = row(check(RETAIL_PRODUCT, 0, 0), factorIds.get(newRange));

        assertThat(row.getCheckResult()).isEqualTo(UNDERWRITER);
        assertThat(row.getCheckedValue()).isEqualTo(StopFactorServiceImpl.NO_DATA);
    }

    // ------------------------------------------------------------------------------------------------

    private Map<Rule, Long> stopFactorsInDatabase() {
        List<StopFactorGroup> overdue = groupRepository.findCurrentActiveWithFactorsAndMetric().stream()
                .filter(g -> g.getFactors().stream().anyMatch(f -> f.getMetric().getCalculator() == CalculatorType.OVERDUE_BUCKET_COUNT))
                .toList();

        Map<Rule, Long> stopFactors = new HashMap<>();
        for (StopFactorGroup g : overdue) {
            for (StopFactor stored : g.getFactors()) {
                Map<StopFactorStatus, Integer> values = stored.getConditions().stream()
                        .collect(Collectors.toMap(StopFactorCondition::getResult, c -> Integer.parseInt(c.getValue())));
                Map<String, Object> params = stored.getParams();
                Rule rule = new Rule(stored.getSegment(),
                        (Integer) params.get("periodFromMonth"), (Integer) params.get("periodToMonth"),
                        (Integer) params.get("minOverdueDays"), (Integer) params.get("maxOverdueDays"),
                        values.get(UNDERWRITER), values.get(REJECTED));
                assertThat(stopFactors.put(rule, stored.getId())).as("duplicate stop factor " + rule).isNull();
            }
        }
        return stopFactors;
    }

    private List<Long> factorIds(ProductSegment segment) {
        return factorIds.entrySet().stream().filter(e -> e.getKey().segment() == segment).map(Map.Entry::getValue).toList();
    }

    private static long productOf(ProductSegment segment) {
        return segment == MSB ? MSB_PRODUCT : RETAIL_PRODUCT;
    }

    /** A stop factor of the requirements file: its name and the names of its products. */
    private record Requirement(String name, List<String> products) {
    }

    /** The overdue history stop factors of the requirements file, in its order (the first 12 are the general ones). */
    private static List<Requirement> overdueRequirements() throws Exception {
        String text;
        try (var in = StopFactorRules2026Test.class.getClassLoader().getResourceAsStream(REQUIREMENTS)) {
            text = new String(Objects.requireNonNull(in, REQUIREMENTS).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        List<Requirement> all = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^(.+?)\\s+\"\\n(.*?)\"", java.util.regex.Pattern.DOTALL).matcher(text);
        while (m.find()) {
            all.add(new Requirement(m.group(1).trim(), m.group(2).lines()
                    .map(line -> line.replace("•", "").trim()).filter(line -> !line.isEmpty()).toList()));
        }
        assertThat(all).hasSize(39);
        return all.subList(12, all.size());
    }

    /** Names of the products, in lower case (the requirements write "Бизнес Лимит", SPR "Бизнес лимит"). */
    private List<String> productNames(Set<Long> productIds) {
        return productIds.stream()
                .map(id -> jdbcTemplate.queryForObject("SELECT name FROM public.product WHERE id = ?", String.class, id).toLowerCase())
                .toList();
    }

    private List<Long> checkedIds(Long productId) {
        return check(productId, 0, 0).stream().map(StopFactorProjection::getFactorId).toList();
    }

    private StopFactorDecision decision(long productId) {
        long scoring = newScoring(productId);
        return stopFactorService.check(scoring, new CustomReport(), new IndividualReport(new GeneralInfoDto(0, 0, 0), List.of()),
                new ConsolidationDto(BigDecimal.ZERO, 0, 0), new TotalDebtFinalResponse(List.of()), REQUEST_DATE);
    }

    /** Checks an application of this product (null - scoring without product) with a full KIB report. */
    private List<StopFactorProjection> check(Long productId, int newCreditsLast30Days, int openCredits) {
        long scoring = newScoring(productId);
        stopFactorService.check(scoring, new CustomReport(), new IndividualReport(new GeneralInfoDto(0, 0, 0), List.of()),
                new ConsolidationDto(BigDecimal.ZERO, newCreditsLast30Days, openCredits),
                new TotalDebtFinalResponse(List.of()), REQUEST_DATE);
        checkRepository.flush();
        return stopFactorService.findByScoringId(scoring);
    }

    private long newScoring(Long productId) {
        long scoring = scoringId--;
        if (productId != null) {
            jdbcTemplate.update("INSERT INTO public.scoring (id, product_id) VALUES (?, ?)", scoring, productId);
        }
        return scoring;
    }

    /** The result of a stop factor. */
    private static StopFactorProjection row(List<StopFactorProjection> rows, long factorId) {
        return rows.stream().filter(p -> p.getFactorId() == factorId).findFirst()
                .orElseThrow(() -> new AssertionError("stop factor " + factorId + " was not checked"));
    }

    /** The result of a group with one stop factor. */
    private static StopFactorStatus result(List<StopFactorProjection> rows, long groupId) {
        return rows.stream().filter(p -> p.getStopFactorId() == groupId).findFirst()
                .orElseThrow(() -> new AssertionError("group " + groupId + " was not checked"))
                .getCheckResult();
    }
}
