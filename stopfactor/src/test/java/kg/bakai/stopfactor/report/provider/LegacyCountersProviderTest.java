package kg.bakai.stopfactor.report.provider;

import kg.bakai.spr.core.kib.dto.ConsolidationDto;
import kg.bakai.spr.core.kib.dto.GeneralInfoDto;
import kg.bakai.spr.core.kib.dto.IndividualReport;
import kg.bakai.spr.core.ob.dto.TotalDebtReportData;
import kg.bakai.spr.core.ob.dto.response.TotalDebtFinalResponse;
import kg.bakai.stopfactor.report.CreditHistoryEvents.Source;
import kg.bakai.stopfactor.report.LegacyCounters;
import kg.bakai.stopfactor.report.ScoringReports;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OB counters are counted from the OB report with or without a KIB report; without one every KIB counter is 0.
 */
class LegacyCountersProviderTest {

    private static final LocalDate REQUEST_DATE = LocalDate.of(2026, 11, 9);

    private final LegacyCountersProvider provider = new LegacyCountersProvider();

    @Test
    void withoutKibReportOneObCreditChanged3MonthsAgoIsARestructuringOfMonths1To12() {
        LegacyCounters counters = withoutKib(REQUEST_DATE.minusMonths(3));

        assertThat(counters.restructurings(Source.OB, 1, 12)).isEqualTo(OptionalDouble.of(1));
        assertThat(counters.restructurings(Source.OB, 13, 24)).isEqualTo(OptionalDouble.of(0));
        assertThat(counters.restructurings(Source.KIB, 1, 12)).isEqualTo(OptionalDouble.of(0));
    }

    @Test
    void withoutKibReportOneObCreditChanged15MonthsAgoIsARestructuringOfMonths13To24() {
        LegacyCounters counters = withoutKib(REQUEST_DATE.minusMonths(15));

        assertThat(counters.restructurings(Source.OB, 13, 24)).isEqualTo(OptionalDouble.of(1));
        assertThat(counters.restructurings(Source.OB, 1, 12)).isEqualTo(OptionalDouble.of(0));
        assertThat(counters.restructurings(Source.KIB, 13, 24)).isEqualTo(OptionalDouble.of(0));
    }

    @Test
    void withoutKibReportEveryKibCounterIsZero() {
        LegacyCounters counters = withoutKib(REQUEST_DATE.minusMonths(3), REQUEST_DATE.minusMonths(20));

        assertThat(counters).isEqualTo(LegacyCounters.obOnly(1, 1));
        assertThat(counters.bureauRequests(7)).isEqualTo(OptionalDouble.of(0));
        assertThat(counters.newCredits(30)).isEqualTo(OptionalDouble.of(0));
    }

    @Test
    void anIncompleteKibReportCountsTheObCreditsLikeNoKibReport() {
        ScoringReports reports = new ScoringReports(null, new IndividualReport(new GeneralInfoDto(0, 0, 0), List.of()),
                new ConsolidationDto(BigDecimal.ZERO, 0, 0), ob(REQUEST_DATE.minusMonths(2)), REQUEST_DATE);

        assertThat(provider.provide(reports, null)).contains(LegacyCounters.obOnly(1, 0));
    }

    @Test
    void obCreditsWithoutChangesNullOrMissingCountAsNoChanges() {
        assertThat(provide(new TotalDebtFinalResponse(null))).isEqualTo(LegacyCounters.NONE);
        assertThat(provide(new TotalDebtFinalResponse(new ArrayList<>()))).isEqualTo(LegacyCounters.NONE);
        assertThat(provide(new TotalDebtFinalResponse(new ArrayList<>(Arrays.asList(
                null, new TotalDebtReportData(0, null, null)))))).isEqualTo(LegacyCounters.NONE);
    }

    @Test
    void anObCreditChangedMoreThan24MonthsAgoIsNotCounted() {
        assertThat(withoutKib(REQUEST_DATE.minusMonths(25))).isEqualTo(LegacyCounters.NONE);
    }

    private LegacyCounters withoutKib(LocalDate... lastChangeDates) {
        return provide(ob(lastChangeDates));
    }

    private LegacyCounters provide(TotalDebtFinalResponse totalDebt) {
        return provider.provide(new ScoringReports(null, null, null, totalDebt, REQUEST_DATE), null).orElseThrow();
    }

    private static TotalDebtFinalResponse ob(LocalDate... lastChangeDates) {
        return new TotalDebtFinalResponse(new ArrayList<>(Arrays.stream(lastChangeDates)
                .map(date -> new TotalDebtReportData(0, null, date))
                .toList()));
    }
}
