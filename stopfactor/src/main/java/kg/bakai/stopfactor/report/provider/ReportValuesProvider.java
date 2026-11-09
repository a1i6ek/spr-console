package kg.bakai.stopfactor.report.provider;

import kg.bakai.spr.core.kib.dto.ConsolidationDto;
import kg.bakai.spr.core.kib.dto.GeneralInfoDto;
import kg.bakai.spr.core.kib.dto.IndividualReport;
import kg.bakai.spr.core.kib.dto.LoanDto;
import kg.bakai.spr.core.ob.dto.TotalDebtReportData;
import kg.bakai.stopfactor.report.ReportValues;
import kg.bakai.stopfactor.report.ScoringReports;
import kg.bakai.stopfactor.report.fact.CheckContext;
import kg.bakai.stopfactor.report.fact.FactKey;
import kg.bakai.stopfactor.report.fact.FactProvider;
import kg.bakai.stopfactor.report.fact.Facts;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The values of the legacy StopFactorServiceImpl.initializeStopFactorIntegerValueDto, calculated the same way
 * (LegacyStopFactorParityTest), with one difference: the current overdue amount includes the loans the client
 * guarantees, like the current overdue days (the legacy took the KIB consolidation, which counts the main debtor
 * loans only). Without a full KIB report only the current overdue from OB is known, all other values are 0.
 */
@Component
public class ReportValuesProvider implements FactProvider<ReportValues> {

    @Override
    public FactKey<ReportValues> key() {
        return Facts.REPORT_VALUES;
    }

    @Override
    public Optional<ReportValues> provide(ScoringReports reports, CheckContext context) {
        List<TotalDebtReportData> obCredits = reports.totalDebt().getCredits();
        long obOverdueDays = maxOverdueDays(obCredits);
        double obOverdueAmount = overdueAmount(obCredits);
        if (!reports.hasKibReport()) {
            return Optional.of(ReportValues.withoutKibReport(obOverdueDays, obOverdueAmount));
        }

        IndividualReport individualReport = reports.individualReport();
        ConsolidationDto consolidation = reports.consolidation();
        GeneralInfoDto generalInfo = individualReport.generalInfoDto();
        return Optional.of(new ReportValues(
                generalInfo.numberOfActiveDisputes(),
                generalInfo.numberOfUnjustifiedDisputesInPast(),
                Math.max(OwnLoans.max(individualReport, LoanDto::getDaysDelay), obOverdueDays),
                consolidation.totalOverduePrincipalOrInterestOnAllOpenCreditContracts().doubleValue()
                        + OwnLoans.guaranteedOverdueAmount(individualReport) + obOverdueAmount,
                consolidation.totalOpenCreditContracts()));
    }

    private static long maxOverdueDays(List<TotalDebtReportData> credits) {
        return credits.stream().mapToLong(TotalDebtReportData::getCurrentOverdueDaysCount).max().orElse(0);
    }

    // A credit without an overdue sum counts as 0
    private static double overdueAmount(List<TotalDebtReportData> credits) {
        return credits.stream()
                .map(TotalDebtReportData::getCurrentOverdueSum)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .doubleValue();
    }
}
