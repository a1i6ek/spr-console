package kg.bakai.stopfactor;

import jakarta.persistence.EntityManager;
import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.spr.core.kib.KibService;
import kg.bakai.spr.core.kib.dto.OverdueForStopFactor;
import kg.bakai.spr.core.ob.dto.TotalDebtReportData;
import kg.bakai.spr.core.ob.dto.response.TotalDebtFinalResponse;
import kg.bakai.spr.core.shared.InternalStatusCode;
import kg.bakai.stopfactor.dto.StopFactorDecision;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.StopFactorGroupDto;
import kg.bakai.stopfactor.dto.StopFactorProjection;
import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.report.KibOverdueHistoryReader;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
import kg.bakai.stopfactor.service.StopFactorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static kg.bakai.stopfactor.enums.StopFactorStatus.APPROVED;
import static kg.bakai.stopfactor.enums.StopFactorStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Groups and stop factors as changelogs 1.3 / 1.4 leave them and as the console changes them. Runs against the
 * migrated database, every test is rolled back.
 */
@SpringBootTest
@Transactional
class StopFactorRulesTest {

    private static final LocalDate REQUEST_DATE = LocalDate.of(2026, 11, 3);
    /** "Текущая просрочка": legacy stop factors #9 (days) and #10 (amount). */
    private static final long CURRENT_OVERDUE_GROUP = 4;
    private static final long CURRENT_OVERDUE_DAYS = 9;

    @MockitoBean
    private KibService kibService;
    @MockitoBean
    private KibOverdueHistoryReader overdueHistoryReader;

    @Autowired
    private StopFactorConsoleService consoleService;
    @Autowired
    private StopFactorService stopFactorService;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void reports() {
        when(kibService.getOverdueByPeriodAndCount(any(), any())).thenReturn(OverdueForStopFactor.NONE);
        when(overdueHistoryReader.read(any(), any())).thenReturn(null);
    }

    @Test
    void theLegacyStopFactorsAreInTheirGroupsWithTheirParamsAndThresholds() {
        StopFactorGroupDto restructurings = consoleService.get(2);
        assertThat(restructurings.name()).isEqualTo("Реструктуризации");
        assertThat(restructurings.factors())
                .extracting(f -> f.id() + " " + f.params().get("source") + " " + f.params().get("periodFromMonth"))
                .containsExactly("4 KIB 1", "5 OB 1", "6 KIB 13", "7 OB 13");
        assertThat(restructurings.factors()).allMatch(f -> f.metricTitle().equals("Реструктуризации"));

        StopFactorDto bureau = consoleService.get(3).factors().getFirst();
        assertThat(bureau.params()).isEqualTo(Map.of("days", 7));
        assertThat(bureau.conditions()).containsExactly(
                new kg.bakai.stopfactor.dto.ConditionDto(kg.bakai.stopfactor.enums.StopFactorStatus.REJECTED, ConditionOperator.GT,
                        "14", null, kg.bakai.stopfactor.enums.StopFactorValueTypes.INTEGER));
    }

    @Test
    void theRequirementsLeaveThirtyNineActiveStopFactorsInFiveGroups() {
        List<StopFactorGroupDto> groups = consoleService.getAll(true, null, null, null);

        assertThat(groups).hasSize(5);
        assertThat(groups.stream().flatMap(g -> g.factors().stream())).hasSize(39).allMatch(StopFactorDto::active);
    }

    @Test
    void paramsAreCheckedByTheMetricAndItsCalculator() {
        assertThatThrownBy(() -> consoleService.create(TestGroups.group(TestGroups.overdue(
                Map.of("periodFromMonth", 6, "periodToMonth", 3, "minOverdueDays", 1)))))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("«Период с (месяц)» (6) не может быть больше «Период по (месяц)» (3)");
        assertThatThrownBy(() -> consoleService.create(TestGroups.group(TestGroups.overdue(
                Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 1, "days", 7)))))
                .hasMessageContaining("параметр «days» не задан в метрике «Непрерывная просроченная задолженность»");
    }

    @Test
    void anEditIsANewGroupVersionAndEarlierChecksKeepTheRuleTheyRanWith() {
        // legacy #9: any current overdue rejects with 451
        assertThat(check(-801, 3)).isEqualTo(new StopFactorDecision(REJECTED, InternalStatusCode._451));

        StopFactorGroupDto current = consoleService.get(CURRENT_OVERDUE_GROUP);
        StopFactorDto overdueDays = current.factors().getFirst();
        StopFactorGroupDto updated = consoleService.updateFactor(CURRENT_OVERDUE_GROUP, overdueDays.id(),
                TestGroups.withRejectedValue(overdueDays, "5"), "Порог 5 дней");
        entityManager.flush();

        assertThat(updated.versionNumber()).isEqualTo(2);
        assertThat(check(-802, 3).status()).isEqualTo(APPROVED);
        assertThat(overdueDaysRow(-801)).satisfies(row -> {
            assertThat(row.getStopFactorId()).isEqualTo(CURRENT_OVERDUE_DAYS);
            assertThat(row.getGroupVersionNumber()).isEqualTo(1);
            assertThat(row.getRejectedValue()).isEqualTo("0");
        });
        assertThat(overdueDaysRow(-802)).satisfies(row -> {
            assertThat(row.getStopFactorId()).isEqualTo(updated.factors().getFirst().id());
            assertThat(row.getGroupVersionNumber()).isEqualTo(2);
            assertThat(row.getRejectedValue()).isEqualTo("5");
        });
    }

    @Test
    void anInactiveGroupIsKeptButNotChecked() {
        consoleService.changeStatus(CURRENT_OVERDUE_GROUP, false, "Временно");
        entityManager.flush();

        assertThat(check(-803, 3).status()).isEqualTo(APPROVED);
        assertThat(stopFactorService.findByScoringId(-803L)).extracting(StopFactorProjection::getGroupId)
                .doesNotContain(CURRENT_OVERDUE_GROUP);
        assertThat(consoleService.get(CURRENT_OVERDUE_GROUP).active()).isFalse();
    }

    // ------------------------------------------------------------------------------------------------

    /** A client without a KIB report and with one OB credit of this current overdue. */
    private StopFactorDecision check(long scoringId, long overdueDays) {
        StopFactorDecision decision = stopFactorService.check(scoringId, null, null, null,
                new TotalDebtFinalResponse(new ArrayList<>(List.of(new TotalDebtReportData(overdueDays, null, null)))), REQUEST_DATE);
        entityManager.flush();
        return decision;
    }

    private StopFactorProjection overdueDaysRow(long scoringId) {
        return stopFactorService.findByScoringId(scoringId).stream()
                .filter(row -> row.getGroupId() == CURRENT_OVERDUE_GROUP && row.getMetricId() == TestGroups.CURRENT_OVERDUE_DAYS_METRIC)
                .findFirst().orElseThrow();
    }
}
