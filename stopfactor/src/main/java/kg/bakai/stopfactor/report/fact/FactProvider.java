package kg.bakai.stopfactor.report.fact;

import kg.bakai.stopfactor.report.ScoringReports;

import java.util.Optional;

/**
 * Makes one fact of an application from its reports. Every provider is a Spring bean; {@link CheckContextFactory}
 * finds it by its {@link #key()}. A new kind of data for the calculators is a new key in {@link Facts} and a new
 * provider: the other providers and calculators do not change.
 *
 * @param <T> type of the fact
 */
public interface FactProvider<T> {

    FactKey<T> key();

    /**
     * Called at most once per application, and only when a calculator asks for the fact.
     *
     * @param reports the reports of the application
     * @param context the other facts, for a fact made from them
     * @return the fact, or empty if the reports have no such data
     */
    Optional<T> provide(ScoringReports reports, CheckContext context);
}
