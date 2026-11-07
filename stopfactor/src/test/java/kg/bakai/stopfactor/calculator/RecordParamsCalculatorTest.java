package kg.bakai.stopfactor.calculator;

import kg.bakai.stopfactor.config.CalculatorConfig;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.enums.ValueKind;
import kg.bakai.stopfactor.exception.InvalidParamsException;
import kg.bakai.stopfactor.report.CreditHistoryEvents.Source;
import kg.bakai.stopfactor.report.fact.CheckContext;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordParamsCalculatorTest {

    /** The console form and its checks are the same as when the specs were written by hand. */
    @Test
    void specsComeFromTheRecordsAsTheyWereWrittenByHand() {
        assertThat(new OverdueBucketCountCalculator().paramSpecs()).containsExactly(
                ParamSpec.integer("periodFromMonth", "Период с (месяц)", true, 1),
                ParamSpec.integer("periodToMonth", "Период по (месяц)", true, 1),
                ParamSpec.integer("minOverdueDays", "Максимальная просрочка от (дней)", true, 1),
                ParamSpec.integer("maxOverdueDays", "Максимальная просрочка до (дней)", false, 1));
        assertThat(new RestructuringCountCalculator().paramSpecs()).containsExactly(
                ParamSpec.oneOf("source", "Источник (KIB или OB)", Set.of("KIB", "OB")),
                ParamSpec.integer("periodFromMonth", "Период с (месяц)", true, 1),
                ParamSpec.integer("periodToMonth", "Период по (месяц)", true, 1));
        assertThat(new CalculatorConfig().bureauRequestsCountCalculator().paramSpecs()).containsExactly(
                ParamSpec.integer("days", "Последние N дней", true, 1));
    }

    @Test
    void enumValuesKeepTheirOrderForTheConsoleSelect() {
        assertThat(new RestructuringCountCalculator().paramSpecs().getFirst().allowedValues()).containsExactly("KIB", "OB");
    }

    @Test
    void storedParamsBecomeTheRecord() {
        assertThat(new RestructuringCountCalculator().parseParams(Map.of("source", "OB", "periodFromMonth", 13, "periodToMonth", 24)))
                .isEqualTo(new RestructuringCountCalculator.Window(Source.OB, 13, 24));
        assertThat(new OverdueBucketCountCalculator().parseParams(Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 31)))
                .isEqualTo(new OverdueBucketCountCalculator.Bucket(1, 3, 31, null));
        assertThatThrownBy(() -> new RestructuringCountCalculator().parseParams(Map.of("periodFromMonth", 1, "periodToMonth", 12)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source");
    }

    @Test
    void notAboveChecksPeriodsAndRanges() {
        RestructuringCountCalculator calculator = new RestructuringCountCalculator();

        assertThatThrownBy(() -> calculator.normalizeParams(Map.of("source", "KIB", "periodFromMonth", 13, "periodToMonth", 12)))
                .isInstanceOf(InvalidParamsException.class)
                .hasMessageContaining("«Период с (месяц)» (13) не может быть больше «Период по (месяц)» (12)");
        assertThat(new OverdueBucketCountCalculator().normalizeParams(
                Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 31)))
                .containsEntry("minOverdueDays", 31);    // no maxOverdueDays: nothing to compare with
    }

    // ------------------------------------------------------------------------------------------------
    // Records that cannot be read fail when the calculator is created, i.e. at start
    // ------------------------------------------------------------------------------------------------

    record WithoutAnnotation(@Param(label = "Дни") int days, int other) {
    }

    record WithText(@Param(label = "Текст") String text) {
    }

    record NotAboveUnknown(@Param(label = "С", notAbove = "to") int from) {
    }

    record NotAboveEnum(@Param(label = "С", notAbove = "source") int from, @Param(label = "Источник") Source source) {
    }

    private static <P extends Record> RecordParamsCalculator<P> calculator(Class<P> type) {
        return new RecordParamsCalculator<>(type) {
            @Override
            public CalculatorType type() {
                return CalculatorType.BUREAU_REQUESTS_COUNT;
            }

            @Override
            public ValueKind valueKind() {
                return ValueKind.NUMBER;
            }

            @Override
            public Optional<CheckedValue> calculate(P params, CheckContext context) {
                return Optional.empty();
            }
        };
    }

    @Test
    void aRecordThatCannotBeReadFailsAtStart() {
        assertThatThrownBy(() -> calculator(WithoutAnnotation.class)).hasMessageContaining("other: нет аннотации @Param");
        assertThatThrownBy(() -> calculator(WithText.class)).hasMessageContaining("тип String не поддерживается");
        assertThatThrownBy(() -> calculator(NotAboveUnknown.class)).hasMessageContaining("notAbove = \"to\"");
        assertThatThrownBy(() -> calculator(NotAboveEnum.class)).hasMessageContaining("notAbove = \"source\"");
    }

    @Test
    void aNewCalculatorNeedsOnlyItsRecord() {
        record Range(@Param(label = "От", min = 1, notAbove = "to") int from, @Param(label = "До", min = 1) Integer to) {
        }
        RecordParamsCalculator<Range> calculator = calculator(Range.class);

        assertThat(calculator.paramSpecs()).extracting(ParamSpec::key).isEqualTo(List.of("from", "to"));
        assertThat(calculator.parseParams(calculator.normalizeParams(Map.of("from", "2", "to", 5L)))).isEqualTo(new Range(2, 5));
        assertThatThrownBy(() -> calculator.normalizeParams(Map.of("from", 0)))
                .hasMessageContaining("«От» должно быть не меньше 1");
    }
}
