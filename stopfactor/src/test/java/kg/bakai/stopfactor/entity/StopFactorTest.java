package kg.bakai.stopfactor.entity;

import kg.bakai.stopfactor.enums.ConditionOperator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static kg.bakai.common.enums.ProductSegment.MSB;
import static kg.bakai.common.enums.ProductSegment.RETAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static kg.bakai.stopfactor.enums.StopFactorStatus.REJECTED;
import static kg.bakai.stopfactor.enums.StopFactorStatus.UNDERWRITER;
import static kg.bakai.stopfactor.enums.StopFactorValueTypes.INTEGER;

class StopFactorTest {

    @Test
    void stopFactorWithoutSegmentAndProductsAppliesToEveryApplication() {
        StopFactor factor = factorOf(bureauRequestsMetric());

        assertThat(factor.appliesTo(7L, RETAIL)).isTrue();
        assertThat(factor.appliesTo(7L, MSB)).isTrue();
        assertThat(factor.appliesTo(null, RETAIL)).isTrue();
    }

    @Test
    void segmentStopFactorAppliesOnlyToItsSegment() {
        StopFactor msb = factorOf(bureauRequestsMetric());
        msb.setSegment(MSB);

        assertThat(msb.appliesTo(7L, MSB)).isTrue();
        assertThat(msb.appliesTo(7L, RETAIL)).isFalse();
        assertThat(msb.appliesTo(null, RETAIL)).isFalse();
    }

    @Test
    void aStopFactorLimitedToProductsChecksOnlyThoseProducts() {
        StopFactor factor = factorOf(bureauRequestsMetric());
        factor.setSegment(RETAIL);
        factor.getProductIds().add(5L);

        assertThat(factor.appliesTo(5L, RETAIL)).isTrue();
        assertThat(factor.appliesTo(6L, RETAIL)).isFalse();
        assertThat(factor.appliesTo(6L, MSB)).isFalse();
    }

    @Test
    void aLinkedProductStaysCheckedWhenSprMovesItToAnotherSegment() {
        StopFactor factor = factorOf(bureauRequestsMetric());
        factor.setSegment(RETAIL);
        factor.getProductIds().add(5L);

        assertThat(factor.appliesTo(5L, MSB)).isTrue();
    }

    @Test
    void aScoringWithoutProductIgnoresProductLimitsAndIsCheckedBySegment() {
        StopFactor retail = factorOf(bureauRequestsMetric());
        retail.setSegment(RETAIL);
        retail.getProductIds().add(5L);
        StopFactor msb = factorOf(bureauRequestsMetric());
        msb.setSegment(MSB);
        msb.getProductIds().add(6L);

        assertThat(retail.appliesTo(null, RETAIL)).isTrue();
        assertThat(msb.appliesTo(null, RETAIL)).isFalse();
    }

    @Test
    void stopFactorsOfOneGroupHaveTheirOwnLimits() {
        StopFactorGroup group = new StopFactorGroup();
        StopFactor retail = group.addFactor();
        retail.setSegment(RETAIL);
        StopFactor msbProduct = group.addFactor();
        msbProduct.setSegment(MSB);
        msbProduct.getProductIds().add(5L);

        assertThat(group.getFactors()).filteredOn(f -> f.appliesTo(5L, MSB)).containsExactly(msbProduct);
        assertThat(group.getFactors()).filteredOn(f -> f.appliesTo(6L, RETAIL)).containsExactly(retail);
        assertThat(group.getFactors()).filteredOn(f -> f.appliesTo(6L, MSB)).isEmpty();
    }

    @Test
    void newVersionKeepsTheSegmentAndProductsOfEveryStopFactor() {
        StopFactorGroup first = new StopFactorGroup();
        first.setId(40L);
        StopFactor factor = first.addFactor();
        factor.setMetric(bureauRequestsMetric());
        factor.setParams(Map.of("days", 30));
        factor.setSegment(MSB);
        factor.getProductIds().add(5L);

        StopFactorGroup next = first.newVersion(LocalDateTime.now(), "reason");

        assertThat(next.getRootId()).isEqualTo(40L);
        StopFactor copy = next.getFactors().getFirst();
        assertThat(copy.getSegment()).isEqualTo(MSB);
        assertThat(copy.getProductIds()).containsExactly(5L);
        // the copy has its own products: a change of the new version does not touch the closed one
        copy.getProductIds().add(6L);
        assertThat(factor.getProductIds()).containsExactly(5L);
    }

    @Test
    void newVersionCopiesEveryStopFactorWithItsParamsAndConditions() {
        StopFactorGroup first = new StopFactorGroup();
        StopFactor week = first.addFactor();
        week.setMetric(bureauRequestsMetric());
        week.setName("7 дней");
        week.setParams(Map.of("days", 7));
        week.addCondition(REJECTED, INTEGER, "14", ConditionOperator.GT, null);
        StopFactor month = first.addFactor();
        month.setMetric(bureauRequestsMetric());
        month.setName("30 дней");
        month.setParams(Map.of("days", 30));
        month.addCondition(UNDERWRITER, INTEGER, "20", ConditionOperator.GT, null);

        StopFactorGroup next = first.newVersion(LocalDateTime.now(), "reason");

        assertThat(next.getFactors()).extracting(StopFactor::getOrderNumber, StopFactor::getName, StopFactor::getParams)
                .containsExactly(tuple(1, "7 дней", Map.of("days", 7)), tuple(2, "30 дней", Map.of("days", 30)));
        assertThat(next.getFactors()).allSatisfy(factor -> assertThat(factor.getGroup()).isSameAs(next));
        assertThat(next.getFactors().get(1).getConditions()).singleElement()
                .satisfies(c -> {
                    assertThat(c.getStopFactor()).isSameAs(next.getFactors().get(1));
                    assertThat(c.getResult()).isEqualTo(UNDERWRITER);
                    assertThat(c.getValue()).isEqualTo("20");
                });
        // the copies are new rows: the first version keeps its own
        assertThat(next.getFactors().getFirst()).isNotSameAs(week);
    }

    @Test
    void paramsAreTypedByTheMetricAndInItsOrder() {
        StopFactorMetric metric = new StopFactorMetric();
        metric.setParams(List.of(new MetricParamSchema("source", "Источник", true),
                new MetricParamSchema("periodFromMonth", "Период с", true), new MetricParamSchema("periodToMonth", "Период по", true)));
        StopFactor factor = factorOf(metric);

        factor.setParams(Map.of("periodToMonth", 12, "source", "KIB", "periodFromMonth", 1));

        assertThat(factor.getParams()).containsExactly(
                Map.entry("source", "KIB"), Map.entry("periodFromMonth", 1), Map.entry("periodToMonth", 12));
    }

    @Test
    void aParamOutsideTheMetricIsRefused() {
        StopFactor factor = factorOf(bureauRequestsMetric());

        assertThatThrownBy(() -> factor.setParams(Map.of("months", 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Параметр «months» не задан в метрике");
    }

    private static StopFactor factorOf(StopFactorMetric metric) {
        StopFactorGroup group = new StopFactorGroup();
        StopFactor factor = group.addFactor();
        factor.setMetric(metric);
        return factor;
    }

    @Test
    void aNewMetricVersionKeepsTheMetricIdAndTheDefinition() {
        StopFactorMetric first = bureauRequestsMetric();
        first.setId(5L);
        first.setNameTemplate("Запросы за {days} дней");
        LocalDateTime now = LocalDateTime.of(2026, 11, 12, 10, 0);

        StopFactorMetric second = first.newVersion(now, "Переименована");
        second.setId(40L);
        StopFactorMetric third = second.newVersion(now.plusDays(1), "Ещё раз");

        assertThat(first.getMetricId()).isEqualTo(5L);
        assertThat(second.getMetricId()).isEqualTo(5L);
        assertThat(third.getRootId()).isEqualTo(5L);   // always the first version, never the previous one
        assertThat(third.getVersionNumber()).isEqualTo(3);
        assertThat(second.getValidFrom()).isEqualTo(now);
        assertThat(second.getChangeReason()).isEqualTo("Переименована");
        assertThat(second.isCurrent()).isTrue();
        assertThat(second.getTitle()).isEqualTo(first.getTitle());
        assertThat(second.getNameTemplate()).isEqualTo(first.getNameTemplate());
        assertThat(second.getParams()).isEqualTo(first.getParams()).isNotSameAs(first.getParams());
    }

    @Test
    void aGroupIsNamedLikeItsOnlyStopFactorOrByItsMetrics() {
        StopFactorMetric overdue = metric("Непрерывная просроченная задолженность");
        StopFactorMetric disputes = metric("Активные споры КИБ");
        StopFactorGroup group = new StopFactorGroup();
        StopFactor first = group.addFactor();
        first.setMetric(overdue);
        first.setName("с 1 по 3 месяц");

        assertThat(group.getName()).isEqualTo("с 1 по 3 месяц");

        group.addFactor().setMetric(overdue);
        assertThat(group.getName()).isEqualTo("Непрерывная просроченная задолженность");

        group.addFactor().setMetric(disputes);
        group.addFactor().setMetric(overdue);
        assertThat(group.getName()).isEqualTo("Непрерывная просроченная задолженность, Активные споры КИБ");
    }

    @Test
    void aGroupMovesOnlyTheStopFactorsOfAChangedMetricVersion() {
        StopFactorMetric overdue = metric("Непрерывная просроченная задолженность");
        overdue.setId(10L);
        StopFactorMetric disputes = metric("Активные споры КИБ");
        disputes.setId(2L);
        StopFactorGroup group = new StopFactorGroup();
        group.addFactor().setMetric(overdue);
        group.addFactor().setMetric(disputes);

        StopFactorMetric renamed = overdue.newVersion(LocalDateTime.now(), "Переименована");
        renamed.setId(50L);
        renamed.setTitle("Просрочки");
        group.useMetric(overdue, renamed);

        assertThat(group.getFactors()).extracting(StopFactor::getMetric).containsExactly(renamed, disputes);
    }

    private static StopFactorMetric metric(String title) {
        StopFactorMetric metric = new StopFactorMetric();
        metric.setTitle(title);
        return metric;
    }

    private static StopFactorMetric bureauRequestsMetric() {
        StopFactorMetric metric = new StopFactorMetric();
        metric.setTitle("Запросы в кредитное бюро");
        metric.setParams(List.of(new MetricParamSchema("days", "Последние N дней", true)));
        return metric;
    }
}
