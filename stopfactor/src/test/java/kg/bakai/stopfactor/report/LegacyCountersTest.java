package kg.bakai.stopfactor.report;


import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static kg.bakai.stopfactor.report.CreditHistoryEvents.Source.KIB;
import static kg.bakai.stopfactor.report.CreditHistoryEvents.Source.OB;
import static org.assertj.core.api.Assertions.assertThat;

class LegacyCountersTest {

    private final LegacyCounters counters = new LegacyCounters(1, 2, 3, 4, 7, 30);

    @Test
    void restructuringsHaveTwoPeriodsPerSource() {
        assertThat(counters.restructurings(KIB, 1, 12)).isEqualTo(OptionalDouble.of(1));
        assertThat(counters.restructurings(KIB, 13, 24)).isEqualTo(OptionalDouble.of(2));
        assertThat(counters.restructurings(OB, 1, 12)).isEqualTo(OptionalDouble.of(3));
        assertThat(counters.restructurings(OB, 13, 24)).isEqualTo(OptionalDouble.of(4));
        assertThat(counters.restructurings(KIB, 1, 24)).isEmpty();
        assertThat(counters.restructurings(OB, 1, 6)).isEmpty();
    }

    @Test
    void recentEventsHaveOnePeriod() {
        assertThat(counters.bureauRequests(7)).isEqualTo(OptionalDouble.of(7));
        assertThat(counters.bureauRequests(30)).isEmpty();
        assertThat(counters.newCredits(30)).isEqualTo(OptionalDouble.of(30));
        assertThat(counters.newCredits(7)).isEmpty();
    }
}
