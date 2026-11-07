package kg.bakai.stopfactor.report.fact;

import kg.bakai.stopfactor.report.ScoringReports;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What the calculators read for one application: its facts by key. A fact is made by its {@link FactProvider} the
 * first time it is asked for and kept for the other stop factors, so data no active stop factor needs is never
 * calculated. One context per check, not thread-safe.
 */
public final class CheckContext {

    private final long scoringId;
    private final Long productId;
    private final ScoringReports reports;
    private final Map<FactKey<?>, FactProvider<?>> providers;
    private final Map<FactKey<?>, Optional<?>> facts = new HashMap<>();
    private final Set<FactKey<?>> inProgress = new HashSet<>();

    CheckContext(long scoringId, Long productId, ScoringReports reports, Map<FactKey<?>, FactProvider<?>> providers) {
        this.scoringId = scoringId;
        this.productId = productId;
        this.reports = reports;
        this.providers = providers;
    }

    /** Scoring being checked. */
    public long scoringId() {
        return scoringId;
    }

    /** Its product, null if the scoring has none. */
    public Long productId() {
        return productId;
    }

    /** The fact, or empty if the reports have no such data. */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> find(FactKey<T> key) {
        Optional<?> fact = facts.get(key);
        if (fact == null) {
            fact = provide(key);
            facts.put(key, fact);
        }
        return (Optional<T>) fact;
    }

    /**
     * A fact that is always there (its provider never returns empty).
     *
     * @throws IllegalStateException if the reports have no such data
     */
    public <T> T get(FactKey<T> key) {
        return find(key).orElseThrow(() -> new IllegalStateException(
                "Скоринг " + scoringId + ": нет данных для факта «" + key + "»"));
    }

    // Not computeIfAbsent: a provider may ask for other facts, and HashMap does not allow changes inside it
    @SuppressWarnings("unchecked")
    private <T> Optional<T> provide(FactKey<T> key) {
        FactProvider<T> provider = (FactProvider<T>) providers.get(key);
        if (provider == null) {
            throw new IllegalStateException("Нет поставщика факта «" + key + "»");
        }
        if (!inProgress.add(key)) {
            throw new IllegalStateException("Факт «" + key + "» зависит сам от себя: " + inProgress);
        }
        try {
            return Objects.requireNonNull(provider.provide(reports, this),
                    () -> "Поставщик факта «" + key + "» вернул null вместо Optional.empty()");
        } finally {
            inProgress.remove(key);
        }
    }

    /** A context with given facts and no reports, for tests of calculators. */
    public static Builder builder(long scoringId) {
        return new Builder(scoringId);
    }

    public static final class Builder {

        private final long scoringId;
        private Long productId;
        private final Map<FactKey<?>, Optional<?>> facts = new HashMap<>();

        private Builder(long scoringId) {
            this.scoringId = scoringId;
        }

        public Builder productId(Long productId) {
            this.productId = productId;
            return this;
        }

        public <T> Builder fact(FactKey<T> key, T value) {
            facts.put(key, Optional.of(value));
            return this;
        }

        /** The reports have no such data. */
        public Builder noFact(FactKey<?> key) {
            facts.put(key, Optional.empty());
            return this;
        }

        /** A fact neither given nor marked with {@link #noFact} fails when asked for. */
        public CheckContext build() {
            CheckContext context = new CheckContext(scoringId, productId, null, Map.of());
            context.facts.putAll(facts);
            return context;
        }
    }
}
