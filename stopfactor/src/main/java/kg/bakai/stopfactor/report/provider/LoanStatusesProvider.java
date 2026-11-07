package kg.bakai.stopfactor.report.provider;

import kg.bakai.spr.core.kib.dto.IndividualReport;
import kg.bakai.spr.core.kib.dto.LoanDto;
import kg.bakai.stopfactor.report.ScoringReports;
import kg.bakai.stopfactor.report.fact.CheckContext;
import kg.bakai.stopfactor.report.fact.FactKey;
import kg.bakai.stopfactor.report.fact.FactProvider;
import kg.bakai.stopfactor.report.fact.Facts;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Statuses of the own KIB loans of the client, in report order; empty without an individual report. */
@Component
public class LoanStatusesProvider implements FactProvider<List<String>> {

    @Override
    public FactKey<List<String>> key() {
        return Facts.LOAN_STATUSES;
    }

    @Override
    public Optional<List<String>> provide(ScoringReports reports, CheckContext context) {
        IndividualReport individualReport = reports.individualReport();
        if (individualReport == null) {
            return Optional.of(List.of());
        }
        return Optional.of(individualReport.loanDtoList().stream()
                .filter(OwnLoans::isOwn)
                .map(LoanDto::getLoanStatus)
                .filter(Objects::nonNull)
                .map(Enum::name)
                .toList());
    }
}
