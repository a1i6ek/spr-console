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
    void groupWithoutSegmentAndProductsAppliesToEveryApplication() {
        StopFactorGroup group = new StopFactorGroup();

        assertThat(group.appliesTo(7L, RETAIL)).isTrue();
        assertThat(group.appliesTo(7L, MSB)).isTrue();
        assertThat(group.appliesTo(null, RETAIL)).isTrue();
    }

    @Test
    void segmentGroupAppliesOnlyToItsSegment() {
        StopFactorGroup msb = new StopFactorGroup();
        msb.setSegment(MSB);

        assertThat(msb.appliesTo(7L, MSB)).isTrue();
        assertThat(msb.appliesTo(7L, RETAIL)).isFalse();
        assertThat(msb.appliesTo(null, RETAIL)).isFalse();
    }

    @Test
    void productLimitsApplyTogetherWithTheSegment() {
        StopFactorGroup group = new StopFactorGroup();
        group.setSegment(RETAIL);
        group.getProductIds().add(5L);

        assertThat(group.appliesTo(5L, RETAIL)).isTrue();
        assertThat(group.appliesTo(6L, RETAIL)).isFalse();
        assertThat(group.appliesTo(5L, MSB)).isFalse();
        assertThat(group.appliesTo(null, RETAIL)).isTrue();   // scoring without product ignores product limits
    }

    @Test
    void newVersionKeepsTheSegment() {
        StopFactorGroup first = new StopFactorGroup();
        first.setId(40L);
        StopFactor factor = first.addFactor();
        factor.setMetric(bureauRequestsMetric());
        factor.setParams(Map.of("days", 30));
        first.setSegment(MSB);
        first.getProductIds().add(5L);

        StopFactorGroup next = first.newVersion(LocalDateTime.now(), "reason");

        assertThat(next.getSegment()).isEqualTo(MSB);
        assertThat(next.getRootId()).isEqualTo(40L);
        assertThat(next.getProductIds()).containsExactly(5L);
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

    private static StopFactorMetric bureauRequestsMetric() {
        StopFactorMetric metric = new StopFactorMetric();
        metric.setTitle("Запросы в кредитное бюро");
        metric.setParams(List.of(new MetricParamSchema("days", "Последние N дней", true)));
        return metric;
    }
}
