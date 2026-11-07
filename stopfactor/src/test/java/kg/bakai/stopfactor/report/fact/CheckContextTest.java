package kg.bakai.stopfactor.report.fact;

import kg.bakai.spr.core.ob.dto.response.TotalDebtFinalResponse;
import kg.bakai.stopfactor.report.ScoringReports;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckContextTest {

    private static final FactKey<Integer> NUMBER = FactKey.of("number");
    private static final FactKey<Integer> DOUBLED = FactKey.of("doubled");
    private static final ScoringReports REPORTS =
            new ScoringReports(null, null, null, new TotalDebtFinalResponse(new ArrayList<>()), LocalDate.of(2026, 11, 7));

    private static <T> FactProvider<T> provider(FactKey<T> key, BiFunction<ScoringReports, CheckContext, Optional<T>> provide) {
        return new FactProvider<>() {
            @Override
            public FactKey<T> key() {
                return key;
            }

            @Override
            public Optional<T> provide(ScoringReports reports, CheckContext context) {
                return provide.apply(reports, context);
            }
        };
    }

    /** Every fact of {@link Facts} has a provider, so the factory starts; the test facts are added to them. */
    private static CheckContextFactory factory(FactProvider<?>... providers) {
        List<FactProvider<?>> all = new ArrayList<>(List.of(providers));
        Facts.ALL.forEach(key -> all.add(provider(key, (reports, context) -> Optional.empty())));
        return new CheckContextFactory(all);
    }

    @Test
    void aFactIsMadeOnlyWhenAskedForAndOnlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        CheckContext context = factory(provider(NUMBER, (reports, ctx) -> Optional.of(calls.incrementAndGet() * 21)))
                .create(1L, null, REPORTS);

        assertThat(calls).hasValue(0);
        assertThat(context.get(NUMBER)).isEqualTo(21);
        assertThat(context.get(NUMBER)).isEqualTo(21);
        assertThat(calls).hasValue(1);
    }

    @Test
    void aFactCanBeMadeFromAnotherFact() {
        CheckContext context = factory(
                provider(NUMBER, (reports, ctx) -> Optional.of(21)),
                provider(DOUBLED, (reports, ctx) -> Optional.of(ctx.get(NUMBER) * 2)))
                .create(1L, null, REPORTS);

        assertThat(context.get(DOUBLED)).isEqualTo(42);
    }

    @Test
    void anEmptyFactIsKeptAndGetFailsWithTheScoring() {
        AtomicInteger calls = new AtomicInteger();
        CheckContext context = factory(provider(NUMBER, (reports, ctx) -> {
            calls.incrementAndGet();
            return Optional.empty();
        })).create(7L, null, REPORTS);

        assertThat(context.find(NUMBER)).isEmpty();
        assertThat(context.find(NUMBER)).isEmpty();
        assertThat(calls).hasValue(1);
        assertThatThrownBy(() -> context.get(NUMBER)).hasMessageContaining("Скоринг 7").hasMessageContaining("number");
    }

    @Test
    void aFactWithoutProviderOrDependingOnItselfFails() {
        CheckContext context = factory(provider(DOUBLED, (reports, ctx) -> Optional.of(ctx.get(DOUBLED) * 2)))
                .create(1L, null, REPORTS);

        assertThatThrownBy(() -> context.find(NUMBER)).hasMessageContaining("Нет поставщика факта «number»");
        assertThatThrownBy(() -> context.find(DOUBLED)).hasMessageContaining("зависит сам от себя");
    }

    @Test
    void theFactoryNeedsExactlyOneProviderOfEveryFact() {
        assertThatThrownBy(() -> new CheckContextFactory(List.of()))
                .hasMessageContaining("Нет поставщика для фактов");
        assertThatThrownBy(() -> factory(provider(Facts.LOAN_STATUSES, (reports, ctx) -> Optional.of(List.of()))))
                .hasMessageContaining("больше одного поставщика");
    }

    @Test
    void aBuiltContextHasOnlyItsGivenFacts() {
        CheckContext context = CheckContext.builder(1L).productId(5L).fact(NUMBER, 3).noFact(DOUBLED).build();

        assertThat(context.productId()).isEqualTo(5L);
        assertThat(context.get(NUMBER)).isEqualTo(3);
        assertThat(context.find(DOUBLED)).isEmpty();
        assertThatThrownBy(() -> context.find(Facts.REPORT_VALUES)).hasMessageContaining("Нет поставщика");
    }
}
