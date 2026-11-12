package kg.bakai.stopfactor.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TextUtilsTest {

    private static final String OVERDUE = "Просрочка с {periodFromMonth} по {periodToMonth} месяц, где максимальная просрочка "
            + ">= {minOverdueDays}[ и <= {maxOverdueDays}]";

    @Nested
    class RenderTemplate {

        @Test
        void optionalSegmentIsKeptWhenItsParamsAreSet() {
            assertThat(TextUtils.renderTemplate(OVERDUE,
                    Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 1, "maxOverdueDays", 10)))
                    .isEqualTo("Просрочка с 1 по 3 месяц, где максимальная просрочка >= 1 и <= 10");
        }

        @Test
        void optionalSegmentIsDroppedWhenAParamIsMissing() {
            assertThat(TextUtils.renderTemplate(OVERDUE, Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 16)))
                    .isEqualTo("Просрочка с 1 по 3 месяц, где максимальная просрочка >= 16");
        }

        @Test
        void optionalSegmentIsDroppedWhenAParamIsNull() {
            Map<String, Object> params = new HashMap<>(Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 16));
            params.put("maxOverdueDays", null);

            assertThat(TextUtils.renderTemplate(OVERDUE, params))
                    .isEqualTo("Просрочка с 1 по 3 месяц, где максимальная просрочка >= 16");
        }

        @Test
        void everyOptionalSegmentIsDecidedOnItsOwn() {
            String template = "Запросы[ за {days} дней][ из {source}]";

            assertThat(TextUtils.renderTemplate(template, Map.of("source", "KIB"))).isEqualTo("Запросы из KIB");
            assertThat(TextUtils.renderTemplate(template, Map.of("days", 7))).isEqualTo("Запросы за 7 дней");
            assertThat(TextUtils.renderTemplate(template, Map.of())).isEqualTo("Запросы");
        }

        @Test
        void aRequiredPlaceholderWithoutValueRendersAsNothingAndSpacesAreJoined() {
            assertThat(TextUtils.renderTemplate("Реструктуризации c {periodFromMonth} по {periodToMonth} месяц {source}",
                    Map.of("periodFromMonth", 1, "periodToMonth", 12)))
                    .isEqualTo("Реструктуризации c 1 по 12 месяц");
        }

        @Test
        void valuesAreInsertedAsTheyAre() {
            assertThat(TextUtils.renderTemplate("Источник {source}", Map.of("source", "$1 \\KIB")))
                    .isEqualTo("Источник $1 \\KIB");
        }

        @Test
        void paramsTheTemplateDoesNotUseAreIgnored() {
            assertThat(TextUtils.renderTemplate("Открытые кредиты", Map.of("days", 30))).isEqualTo("Открытые кредиты");
        }
    }

    @Nested
    class Placeholders {

        @Test
        void areListedOnceInTheirOrder() {
            assertThat(TextUtils.placeholders(OVERDUE))
                    .containsExactly("periodFromMonth", "periodToMonth", "minOverdueDays", "maxOverdueDays");
            assertThat(TextUtils.placeholders("{days} и снова {days}")).containsExactly("days");
        }

        @Test
        void aTemplateWithoutPlaceholdersHasNone() {
            assertThat(TextUtils.placeholders("Общее число открытых кредитов")).isEmpty();
            assertThat(TextUtils.placeholders("")).isEmpty();
        }
    }

    @Nested
    class LowerFirst {

        @Test
        void aSentenceContinuesAfterAColon() {
            assertThat(TextUtils.lowerFirst("Укажите «Период с».")).isEqualTo("укажите «Период с».");
        }

        @Test
        void anAbbreviationKeepsItsCase() {
            assertThat(TextUtils.lowerFirst("КИБ не ответил.")).isEqualTo("КИБ не ответил.");
        }

        @Test
        void aQuoteDigitOrLowerCaseStartIsKept() {
            assertThat(TextUtils.lowerFirst("«Период с» не задан.")).isEqualTo("«Период с» не задан.");
            assertThat(TextUtils.lowerFirst("2 условия совпадают.")).isEqualTo("2 условия совпадают.");
            assertThat(TextUtils.lowerFirst("уже с маленькой.")).isEqualTo("уже с маленькой.");
        }

        @Test
        void shortTextIsKept() {
            assertThat(TextUtils.lowerFirst("")).isEmpty();
            assertThat(TextUtils.lowerFirst("А")).isEqualTo("А");
        }
    }
}
