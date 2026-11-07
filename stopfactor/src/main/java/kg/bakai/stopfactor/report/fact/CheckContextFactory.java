package kg.bakai.stopfactor.report.fact;

import kg.bakai.stopfactor.report.ScoringReports;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates the {@link CheckContext} of an application with every {@link FactProvider} bean. Checks at start that each
 * fact of {@link Facts} has exactly one provider.
 */
@Component
public class CheckContextFactory {

    private final Map<FactKey<?>, FactProvider<?>> providers = new HashMap<>();

    public CheckContextFactory(List<FactProvider<?>> providers) {
        for (FactProvider<?> provider : providers) {
            if (this.providers.put(provider.key(), provider) != null) {
                throw new IllegalStateException("Для факта «" + provider.key() + "» зарегистрировано больше одного поставщика");
            }
        }
        List<FactKey<?>> missing = Facts.ALL.stream().filter(key -> !this.providers.containsKey(key)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Нет поставщика для фактов " + missing);
        }
    }

    public CheckContext create(long scoringId, Long productId, ScoringReports reports) {
        return new CheckContext(scoringId, productId, reports, providers);
    }
}
