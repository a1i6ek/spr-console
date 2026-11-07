package kg.bakai.stopfactor.report.provider;

import kg.bakai.stopfactor.report.CreditHistoryEvents;
import kg.bakai.stopfactor.report.KibOverdueHistoryReader;
import kg.bakai.stopfactor.report.KibOverdueHistoryReader.OverdueHistory;
import kg.bakai.stopfactor.report.ScoringReports;
import kg.bakai.stopfactor.report.fact.CheckContext;
import kg.bakai.stopfactor.report.fact.FactKey;
import kg.bakai.stopfactor.report.fact.FactProvider;
import kg.bakai.stopfactor.report.fact.Facts;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Raw events of the credit history. For now only the overdue months, the other lists stay null and their
 * stop factors use the legacy counters.
 * <ul>
 *     <li>without a full KIB report the client has no credit history: no overdue months, every overdue count
 *     is 0, like the legacy counters;</li>
 *     <li>the overdue months cannot be found in the report (no payment calendars): empty, the overdue stop
 *     factors use the legacy counters;</li>
 *     <li>the payment calendars do not reach the start of a contract: the overdue months are used for the months
 *     they cover only, longer periods use the legacy counters.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CreditHistoryProvider implements FactProvider<CreditHistoryEvents> {

    private final KibOverdueHistoryReader overdueHistoryReader;

    @Override
    public FactKey<CreditHistoryEvents> key() {
        return Facts.CREDIT_HISTORY;
    }

    @Override
    public Optional<CreditHistoryEvents> provide(ScoringReports reports, CheckContext context) {
        if (!reports.hasKibReport()) {
            return Optional.of(CreditHistoryEvents.ofOverdueMonths(List.of()));
        }
        OverdueHistory overdue = overdueHistoryReader.read(reports.customReport(), reports.requestDate().atStartOfDay());
        if (overdue == null) {
            return Optional.empty();
        }
        return Optional.of(new CreditHistoryEvents(List.copyOf(overdue.months()), overdue.completeMonths(), null, null, null));
    }
}
