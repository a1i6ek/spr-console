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
    void theNextBoxGetsACopyOfEveryStopFactorWithTheSameVersion() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 12, 10, 0);
        StopFactorGroup box1 = StopFactorGroup.first(now.minusDays(1), "Создание");
        StopFactor rule = StopFactor.first(box1, 57L, 3);
        rule.setMetric(metric());
        rule.setName("с 1 по 3 месяц");
        rule.getParams().put("periodFromMonth", 1);
        rule.addCondition(StopFactorStatus.REJECTED, ConditionOperator.GT, "5", null, StopFactorValueTypes.INTEGER);
        rule.addCondition(StopFactorStatus.UNDERWRITER, ConditionOperator.RANGE, "1", "5", StopFactorValueTypes.INTEGER);
        rule.setSegment(RETAIL);
        rule.getProductIds().add(1L);

        StopFactorGroup box2 = box1.next(now, "Другой стоп-фактор изменён");
        StopFactor copy = rule.copyTo(box2);

        assertThat(box2.getVersionNumber()).isEqualTo(2);
        assertThat(box2.getValidFrom()).isEqualTo(now);
        assertThat(box2.isCurrent()).isTrue();
        assertThat(box2.getChangeReason()).isEqualTo("Другой стоп-фактор изменён");
        assertThat(copy).isNotSameAs(rule);
        assertThat(copy.getGroup()).isSameAs(box2);
        assertThat(copy.getFactorId()).isEqualTo(57L);
        assertThat(copy.getVersionNumber()).isEqualTo(1);
        assertThat(copy.getOrderNumber()).isEqualTo(3);
        assertThat(copy.getName()).isEqualTo("с 1 по 3 месяц");
        assertThat(copy.getParams()).isEqualTo(Map.of("periodFromMonth", 1)).isNotSameAs(rule.getParams());
        assertThat(copy.getSegment()).isEqualTo(RETAIL);
        assertThat(copy.getProductIds()).containsExactly(1L).isNotSameAs(rule.getProductIds());
        // the conditions are copied too: new rows of the copy
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
        StopFactor factor = new StopFactor();
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
