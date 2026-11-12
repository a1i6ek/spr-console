package kg.bakai.stopfactor.entity;

import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static kg.bakai.common.enums.ProductSegment.MSB;
import static kg.bakai.common.enums.ProductSegment.RETAIL;
import static org.assertj.core.api.Assertions.assertThat;

class StopFactorTest {

    @Test
    void stopFactorWithoutSegmentAndProductsAppliesToEveryApplication() {
        StopFactor factor = factor();

        assertThat(factor.appliesTo(7L, RETAIL)).isTrue();
        assertThat(factor.appliesTo(7L, MSB)).isTrue();
        assertThat(factor.appliesTo(null, RETAIL)).isTrue();
    }

    @Test
    void segmentStopFactorAppliesOnlyToItsSegment() {
        StopFactor msb = factor();
        msb.setSegment(MSB);

        assertThat(msb.appliesTo(7L, MSB)).isTrue();
        assertThat(msb.appliesTo(7L, RETAIL)).isFalse();
        assertThat(msb.appliesTo(null, RETAIL)).isFalse();
    }

    @Test
    void aStopFactorLimitedToProductsChecksOnlyThoseProducts() {
        StopFactor factor = factor();
        factor.setSegment(RETAIL);
        factor.getProductIds().add(5L);

        assertThat(factor.appliesTo(5L, RETAIL)).isTrue();
        assertThat(factor.appliesTo(6L, RETAIL)).isFalse();
        assertThat(factor.appliesTo(6L, MSB)).isFalse();
    }

    @Test
    void aLinkedProductStaysCheckedWhenSprMovesItToAnotherSegment() {
        StopFactor factor = factor();
        factor.setSegment(RETAIL);
        factor.getProductIds().add(5L);

        assertThat(factor.appliesTo(5L, MSB)).isTrue();
    }

    @Test
    void aScoringWithoutProductIgnoresProductLimitsAndIsCheckedBySegment() {
        StopFactor retail = factor();
        retail.setSegment(RETAIL);
        retail.getProductIds().add(5L);
        StopFactor msb = factor();
        msb.setSegment(MSB);
        msb.getProductIds().add(6L);

        assertThat(retail.appliesTo(null, RETAIL)).isTrue();
        assertThat(msb.appliesTo(null, RETAIL)).isFalse();
    }

    @Test
    void aNewVersionCopiesEveryStopFactorAndKeepsTheGroupId() {
        StopFactorGroup v1 = new StopFactorGroup();
        v1.setId(5L);
        v1.setName("История просрочек");
        v1.setOrderNumber(3);
        StopFactor rule = v1.addFactor();
        rule.setMetric(metric());
        rule.setName("с 1 по 3 месяц");
        rule.getParams().put("periodFromMonth", 1);
        rule.addCondition(StopFactorStatus.REJECTED, ConditionOperator.GT, "5", null, StopFactorValueTypes.INTEGER);
        rule.addCondition(StopFactorStatus.UNDERWRITER, ConditionOperator.RANGE, "1", "5", StopFactorValueTypes.INTEGER);
        rule.setSegment(RETAIL);
        rule.getProductIds().add(1L);
        LocalDateTime now = LocalDateTime.of(2026, 11, 12, 10, 0);

        StopFactorGroup v2 = v1.newVersion(now, "Новый порог");
        v2.setId(40L);
        StopFactorGroup v3 = v2.newVersion(now.plusDays(1), "Ещё раз");
        StopFactor copy = v2.getFactors().getFirst();

        assertThat(v2.getGroupId()).isEqualTo(5L);
        assertThat(v3.getRootId()).isEqualTo(5L);   // always the first version, never the previous one
        assertThat(v3.getVersionNumber()).isEqualTo(3);
        assertThat(v2.getValidFrom()).isEqualTo(now);
        assertThat(v2.getChangeReason()).isEqualTo("Новый порог");
        assertThat(v2.getName()).isEqualTo("История просрочек");
        assertThat(v2.getOrderNumber()).isEqualTo(3);
        assertThat(copy).isNotSameAs(rule);
        assertThat(copy.getGroup()).isSameAs(v2);
        assertThat(copy.getName()).isEqualTo("с 1 по 3 месяц");
        assertThat(copy.getParams()).isEqualTo(Map.of("periodFromMonth", 1)).isNotSameAs(rule.getParams());
        assertThat(copy.getSegment()).isEqualTo(RETAIL);
        assertThat(copy.getProductIds()).containsExactly(1L).isNotSameAs(rule.getProductIds());
        // the conditions are copied too: new rows of the new stop factor
        assertThat(copy.getConditions()).extracting(c -> c.getResult() + " " + c.getOperator() + " " + c.getValue() + " " + c.getValueTo())
                .containsExactly("REJECTED GT 5 null", "UNDERWRITER RANGE 1 5");
        assertThat(copy.getConditions()).allMatch(c -> c.getStopFactor() == copy)
                .noneMatch(c -> rule.getConditions().contains(c));
    }

    @Test
    void aStopFactorWithoutConditionsDecidesNothing() {
        StopFactor factor = factor();
        assertThat(factor.hasConditions()).isFalse();

        factor.addCondition(StopFactorStatus.UNDERWRITER, ConditionOperator.GT, "0", null, StopFactorValueTypes.INTEGER);
        assertThat(factor.hasConditions()).isTrue();
    }

    private static StopFactor factor() {
        StopFactor factor = new StopFactorGroup().addFactor();
        factor.setMetric(metric());
        factor.setName("Запросы в кредитное бюро");
        factor.getParams().put("days", 30);
        return factor;
    }

    private static StopFactorMetric metric() {
        StopFactorMetric metric = new StopFactorMetric();
        metric.setTitle("Запросы в кредитное бюро");
        metric.setCalculator(CalculatorType.BUREAU_REQUESTS_COUNT);
        return metric;
    }
}
