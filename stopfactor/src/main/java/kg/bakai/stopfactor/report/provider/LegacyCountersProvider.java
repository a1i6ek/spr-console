package kg.bakai.stopfactor.report.provider;

import kg.bakai.spr.core.kib.KibService;
import kg.bakai.spr.core.kib.dto.IndividualReport;
import kg.bakai.spr.core.kib.dto.LoanDto;
import kg.bakai.spr.core.ob.dto.TotalDebtReportData;
import kg.bakai.stopfactor.report.LegacyCounters;
import kg.bakai.stopfactor.report.LegacyOverdueCounters;
import kg.bakai.stopfactor.report.ScoringReports;
import kg.bakai.stopfactor.report.fact.CheckContext;
import kg.bakai.stopfactor.report.fact.FactKey;
import kg.bakai.stopfactor.report.fact.FactProvider;
import kg.bakai.stopfactor.report.fact.Facts;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.MONTHS;

/**
 * The fixed-period counters of the old algorithm. Without a full KIB report the client has no credit history: every
 * counter is 0 and the KIB module is not asked.
 */
@Component
@RequiredArgsConstructor
public class LegacyCountersProvider implements FactProvider<LegacyCounters> {

    private final KibService kibService;

    @Override
    public FactKey<LegacyCounters> key() {
        return Facts.LEGACY_COUNTERS;
    }

    @Override
    public Optional<LegacyCounters> provide(ScoringReports reports, CheckContext context) {
        if (!reports.hasKibReport()) {
            return Optional.of(LegacyCounters.NONE);
        }
        IndividualReport individualReport = reports.individualReport();
        ObChanges obChanges = obChanges(reports.totalDebt().getCredits(), reports.requestDate());
        return Optional.of(new LegacyCounters(
                OwnLoans.sum(individualReport, LoanDto::getNumberOfRestructuringsLast12Months),
                OwnLoans.sum(individualReport, LoanDto::getNumberOfRestructuringsFrom12To24Months),
                obChanges.months1To12(),
                obChanges.months13To24(),
                individualReport.generalInfoDto().creditInquiriesLast7Days(),
                reports.consolidation().totalNumberOfNewOpenLoansInTheLast30Days(),
                LegacyOverdueCounters.from(kibService.getOverdueByPeriodAndCount(reports.customReport(),
                        reports.requestDate().atStartOfDay()))));
    }

    /** OB credits by the months between their last change and the request; credits never changed are skipped. */
    private static ObChanges obChanges(List<TotalDebtReportData> credits, LocalDate requestDate) {
        int months1To12 = 0;
        int months13To24 = 0;
        for (TotalDebtReportData credit : credits) {
            if (credit.getLastChangeDate() == null) {
                continue;
            }
            long months = MONTHS.between(credit.getLastChangeDate(), requestDate);
            if (months <= 12) {
                months1To12++;
            } else if (months <= 24) {
                months13To24++;
            }
        }
        return new ObChanges(months1To12, months13To24);
    }

    private record ObChanges(int months1To12, int months13To24) {
    }
}
