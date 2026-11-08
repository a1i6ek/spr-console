package kg.bakai.console.test;

import kg.bakai.console.test.TestCheckRequest.CalendarEntry;
import kg.bakai.console.test.TestCheckRequest.Contract;
import kg.bakai.console.test.TestCheckRequest.Kib;
import kg.bakai.console.test.TestCheckRequest.KibPart;
import kg.bakai.console.test.TestCheckRequest.Loan;
import kg.bakai.console.test.TestCheckRequest.ObCredit;
import kg.bakai.spr.core.kib.dto.enums.LoanStatus;
import kg.bakai.spr.core.kib.dto.enums.RoleOfClient;
import kg.bakai.stopfactor.report.CreditHistoryEvents.OverdueMonth;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static kg.bakai.spr.core.shared.InternalStatusCode._451;
import static kg.bakai.stopfactor.enums.StopFactorStatus.*;

/**
 * Test applications for the local database. Scorings 1..5 and their products come from changelog 1.2:
 * 1 Потребительский кредит, 2 Автокредит, 3 Ипотека, 4 Микрокредит (RETAIL), 5 Бизнес-кредит (MSB, changelog 1.5).
 */
final class TestCheckScenarios {

    private static final long CONSUMER = 1, CAR = 2, MORTGAGE = 3, MICRO = 4, BUSINESS = 5;
    private static final LocalDate DATE = LocalDate.of(2026, 11, 4);

    private TestCheckScenarios() {
    }

    static final List<TestCheckScenario> ALL = List.of(
            new TestCheckScenario("full-application",
                    "Все поля заполнены: Потребительский кредит, два кредита в КИБ, две старые просрочки в пределах лимитов, кредит в ОБ",
                    APPROVED, null, new TestCheckRequest(-100L, CONSUMER, DATE,
                    new Kib(0, 0, 1, BigDecimal.ZERO, 1, 2,
                            List.of(new Loan(RoleOfClient.MainDebtor, LoanStatus.Open, 0, 0, 0),
                                    new Loan(RoleOfClient.Guarantor, LoanStatus.FullyPaid, 0, 0, 0)),
                            List.of(new OverdueMonth(2, 5), new OverdueMonth(14, 12)),
                            null, false, null, Set.of()),
                    List.of(new ObCredit(0, BigDecimal.ZERO, LocalDate.of(2023, 5, 10))))),
            new TestCheckScenario("clean-retail",
                    "Чистая кредитная история, Розница",
                    APPROVED, null, request(CONSUMER, kib())),
            new TestCheckScenario("retail-overdue-reject",
                    "6 месяцев с просрочкой 5 дней в 1-3 месяце: у Розницы отказ с 6",
                    REJECTED, null, request(CONSUMER, kib().overdueMonths(Collections.nCopies(6, new OverdueMonth(2, 5))))),
            new TestCheckScenario("msb-same-history-yellow",
                    "Та же история у продукта МСБ: 6..10 месяцев с просрочкой - жёлтая зона",
                    UNDERWRITER, null, request(BUSINESS, kib().overdueMonths(Collections.nCopies(6, new OverdueMonth(2, 5))))),
            new TestCheckScenario("retail-old-overdue-no-limit",
                    "Просрочки 1-5 дней в 7-12 месяце и до 30 дней в 25-36 месяце: без ограничений",
                    APPROVED, null, request(CONSUMER, kib().overdueMonths(List.of(new OverdueMonth(8, 5), new OverdueMonth(30, 30))))),
            new TestCheckScenario("kib-payment-calendars",
                    "Платежные календари КИБ, считаются месяцы с просрочкой по договорам основного заёмщика: просрочка "
                            + "с сентября (4 дня) до октября (12 дней) - 2 месяца (Ок); 25 дней в июне (с 4 по 6 месяц от "
                            + "21 дня - отказ); просрочки поручителя и созаемщика не учитываются",
                    REJECTED, null, request(CONSUMER, kib().contracts(List.of(
                    new Contract(RoleOfClient.MainDebtor, null, List.of(
                            new CalendarEntry(LocalDate.of(2026, 9, 30), 4, 4),
                            new CalendarEntry(LocalDate.of(2026, 10, 31), 12, 0),
                            new CalendarEntry(LocalDate.of(2026, 11, 30), 0, 0))),
                    new Contract(RoleOfClient.MainDebtor, null, List.of(
                            new CalendarEntry(LocalDate.of(2026, 6, 30), 25, 0),
                            new CalendarEntry(LocalDate.of(2026, 7, 31), 0, 0))),
                    new Contract(RoleOfClient.Guarantor, null, List.of(
                            new CalendarEntry(LocalDate.of(2026, 10, 31), 90, 0))),
                    new Contract(RoleOfClient.CoDebtor, null, List.of(
                            new CalendarEntry(LocalDate.of(2026, 10, 31), 40, 0))))))),
            new TestCheckScenario("kib-guarantor-overdue-not-counted",
                    "Просрочка 90 дней только по договору, где клиент поручитель: не учитывается, как в старом коде КИБ",
                    APPROVED, null, request(CONSUMER, kib().contracts(List.of(
                    new Contract(RoleOfClient.MainDebtor, null, List.of(
                            new CalendarEntry(LocalDate.of(2026, 10, 31), 0, 0))),
                    new Contract(RoleOfClient.Guarantor, null, List.of(
                            new CalendarEntry(LocalDate.of(2026, 10, 31), 90, 0))))))),
            new TestCheckScenario("kib-calendar-shorter-than-contract",
                    "Договор с 2020 года, календарь КИБ только за 24 месяца без просрочек: месяцы 25-36 не видны, у правил "
                            + "25-36 месяцев нет старого счётчика - NO_DATA, андеррайтер",
                    UNDERWRITER, null, request(CONSUMER, kib().contracts(List.of(
                    new Contract(RoleOfClient.MainDebtor, LocalDate.of(2020, 1, 10), List.of(
                            new CalendarEntry(LocalDate.of(2024, 12, 31), 0, 0),
                            new CalendarEntry(LocalDate.of(2026, 11, 30), 0, 0))))))),
            new TestCheckScenario("product-limited-stop-factor",
                    "3 открытых кредита по Ипотеке: тестовый стоп-фактор только для Ипотеки и Автокредита (UNDERWRITER > 2)",
                    UNDERWRITER, null, request(MORTGAGE, kib().openCredits(3))),
            new TestCheckScenario("same-credits-other-product",
                    "3 открытых кредита по Потребительскому: тестовый стоп-фактор его не проверяет, общий #12 отказывает только с 8",
                    APPROVED, null, request(CONSUMER, kib().openCredits(3))),
            new TestCheckScenario("microcredit-bureau-requests",
                    "3 запроса в бюро за 7 дней по Микрокредиту: тестовый стоп-фактор UNDERWRITER > 2",
                    UNDERWRITER, null, request(MICRO, kib().bureauRequests(3))),
            new TestCheckScenario("new-credits-limit",
                    "4 новых кредита за 30 дней: #11 отказывает с 4",
                    REJECTED, null, request(CONSUMER, kib().newCredits(4))),
            new TestCheckScenario("current-overdue-451",
                    "Текущая просрочка 5 дней в ОБ: отказ с кодом 451",
                    REJECTED, _451, new TestCheckRequest(CONSUMER, null, DATE, kib().build(),
                    List.of(new ObCredit(5, new BigDecimal("1200.50"), DATE.minusMonths(1))))),
            new TestCheckScenario("refusal-status",
                    "Кредит клиента списан (WrittenOff): отказ по статусам КИБ",
                    REJECTED, null, request(CONSUMER, kib().loan(new Loan(RoleOfClient.MainDebtor, LoanStatus.WrittenOff, 0, 0, 0)))),
            new TestCheckScenario("refusal-status-of-co-debtor",
                    "Списан кредит, где клиент созаемщик: не учитывается",
                    APPROVED, null, request(CONSUMER, kib().loan(new Loan(RoleOfClient.CoDebtor, LoanStatus.WrittenOff, 0, 0, 0)))),
            new TestCheckScenario("transfer-status-underwriter",
                    "Кредит передан в другую организацию: андеррайтер",
                    UNDERWRITER, null, request(CONSUMER, kib().loan(new Loan(RoleOfClient.MainDebtor, LoanStatus.TransferredToOtherReportingInst, 0, 0, 0)))),

            // Missing data
            new TestCheckScenario("missing-kib-report",
                    "Нет отчёта КИБ: проверяются только данные ОБ. Кредит ОБ изменён 2 месяца назад - реструктуризация ОБ "
                            + "за 12 месяцев, отказ",
                    REJECTED, null, new TestCheckRequest(CAR, null, DATE, null,
                    List.of(new ObCredit(0, null, DATE.minusMonths(2))))),
            new TestCheckScenario("partial-kib-report-ob-restructuring",
                    "Отчёт КИБ без отчёта по договорам (CUSTOM_REPORT): сервис считает, что отчёта КИБ нет, и проверяет "
                            + "данные ОБ. Кредит ОБ изменён 2 месяца назад - реструктуризация ОБ за 12 месяцев, отказ",
                    REJECTED, null, new TestCheckRequest(CONSUMER, null, DATE, kib().missing(KibPart.CUSTOM_REPORT).build(),
                    List.of(new ObCredit(0, null, DATE.minusMonths(2))))),
            new TestCheckScenario("partial-kib-report",
                    "Отчёт КИБ без консолидации: 2 активных спора не проверяются, сервис считает, что отчёта КИБ нет",
                    APPROVED, null, request(CONSUMER, kib().activeDisputes(2).missing(KibPart.CONSOLIDATION))),
            new TestCheckScenario("full-kib-report-same-disputes",
                    "Тот же клиент с полным отчётом КИБ: 2 активных спора - отказ",
                    REJECTED, null, request(CONSUMER, kib().activeDisputes(2))),
            new TestCheckScenario("kib-calendars-unavailable",
                    "В отчёте КИБ нет платежных календарей: месяцы просрочки не найдены, новые диапазоны дают NO_DATA, заявка уходит андеррайтеру",
                    UNDERWRITER, null, request(CONSUMER, kib().calendarsUnavailable())),
            new TestCheckScenario("scoring-without-product",
                    "Скоринг без продукта: сегмент RETAIL, ограничения по продуктам не действуют",
                    APPROVED, null, new TestCheckRequest(null, null, DATE, kib().build(), List.of()))
    );

    private static TestCheckRequest request(long scoringId, KibBuilder kib) {
        return new TestCheckRequest(scoringId, null, DATE, kib.build(), List.of());
    }

    private static KibBuilder kib() {
        return new KibBuilder();
    }

    private static final class KibBuilder {
        private int activeDisputes;
        private int bureauRequests;
        private int newCredits;
        private int openCredits;
        private List<Loan> loans = List.of(new Loan(RoleOfClient.MainDebtor, LoanStatus.Open, 0, 0, 0));
        private List<OverdueMonth> overdueMonths = List.of();
        private List<Contract> contracts;
        private boolean calendarsUnavailable;
        private Set<KibPart> missing = Set.of();

        KibBuilder activeDisputes(int count) { activeDisputes = count; return this; }
        KibBuilder bureauRequests(int count) { bureauRequests = count; return this; }
        KibBuilder newCredits(int count) { newCredits = count; return this; }
        KibBuilder openCredits(int count) { openCredits = count; return this; }
        KibBuilder loan(Loan loan) { loans = List.of(loan); return this; }
        KibBuilder overdueMonths(List<OverdueMonth> list) { overdueMonths = list; return this; }
        KibBuilder contracts(List<Contract> list) { contracts = list; return this; }
        KibBuilder calendarsUnavailable() { calendarsUnavailable = true; return this; }
        KibBuilder missing(KibPart part) { missing = Set.of(part); return this; }

        Kib build() {
            return new Kib(activeDisputes, 0, bureauRequests, BigDecimal.ZERO, newCredits, openCredits,
                    loans, overdueMonths, contracts, calendarsUnavailable, null, missing);
        }
    }
}
