package kg.bakai.stopfactor.report.provider;

import kg.bakai.spr.core.kib.dto.IndividualReport;
import kg.bakai.spr.core.kib.dto.LoanDto;
import kg.bakai.spr.core.ob.dto.TotalDebtReportData;
import kg.bakai.stopfactor.report.LegacyCounters;
import kg.bakai.stopfactor.report.ScoringReports;
import kg.bakai.stopfactor.report.fact.CheckContext;
import kg.bakai.stopfactor.report.fact.FactKey;
import kg.bakai.stopfactor.report.fact.FactProvider;
import kg.bakai.stopfactor.report.fact.Facts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.MONTHS;

/**
 * The fixed-period counters of the old algorithm.
 * <ul>
 *     <li>OB counters (credits changed in months 1..12 and 13..24) are always counted from the OB report, which SPR
 *     always passes;</li>
 *     <li>without a full KIB report ({@link ScoringReports#hasKibReport()}) every KIB counter is 0. The legacy
 *     algorithm set the OB counters to 0 here too, so the OB restructuring stop factors never fired without a KIB
 *     report; they are counted now.</li>
 * </ul>
 * The overdue month counters of the KIB module are not here: the overdue stop factors count the months of the KIB
 * payment calendars ({@link CreditHistoryProvider}).
 */
@Slf4j
@Component
public class LegacyCountersProvider implements FactProvider<LegacyCounters> {

    @Override
    public FactKey<LegacyCounters> key() {
        return Facts.LEGACY_COUNTERS;
    }

    @Override
    public Optional<LegacyCounters> provide(ScoringReports reports, CheckContext context) {
        ObChanges obChanges = obChanges(reports.totalDebt().getCredits(), reports.requestDate());
        if (!reports.hasKibReport()) {
            log.debug("[#provide] Нет отчёта КИБ: счётчики КИБ = 0, изменённых кредитов ОБ за 1–12 мес.: {}, за 13–24 мес.: {}",
                    obChanges.months1To12(), obChanges.months13To24());
            return Optional.of(LegacyCounters.obOnly(obChanges.months1To12(), obChanges.months13To24()));
        }
        IndividualReport individualReport = reports.individualReport();
        return Optional.of(new LegacyCounters(
                OwnLoans.sum(individualReport, LoanDto::getNumberOfRestructuringsLast12Months),
                OwnLoans.sum(individualReport, LoanDto::getNumberOfRestructuringsFrom12To24Months),
                obChanges.months1To12(),
                obChanges.months13To24(),
                individualReport.generalInfoDto().creditInquiriesLast7Days(),
                reports.consolidation().totalNumberOfNewOpenLoansInTheLast30Days()));
    }

    /**
     * OB credits by the months between their last change and the request; credits never changed are skipped, and an
     * OB report without credits (null or empty) has no changes.
     */
    private static ObChanges obChanges(List<TotalDebtReportData> credits, LocalDate requestDate) {
        int months1To12 = 0;
        int months13To24 = 0;
        for (TotalDebtReportData credit : credits != null ? credits : List.<TotalDebtReportData>of()) {
            if (credit == null || credit.getLastChangeDate() == null) {
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
