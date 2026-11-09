package kg.bakai.spr.core.kib.dto;

import kg.bakai.spr.core.kib.dto.enums.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Copy of the SPR class with the same name and package, field for field. The stop factor check reads
 * participationType, loanStatus, phaseOfLoan, daysDelay, delayAmount, overdueInterest and the restructuring counts.
 */
@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoanDto {
    private String subscriber;
    private String source; // Источник
    private LoanStatus loanStatus; // Статус кредита
    private LoanPhase phaseOfLoan; // Стадия кредита
    private LoanType loanType; // Тип кредита
    private LoanType loanTypeV2; // Тип кредита V2
    private boolean isUnsecuredLoan; // Наличие залога (Поля: залоговый / беззалоговый)
    private String collateralsV2; // Наличие залога (Поля: залоговый / беззалоговый) V2
    private String currency; // Валюта
    private LocalDateTime startDate; // Дата выдачи
    private LocalDateTime endDate; // Дата окончания
    private double loanSum; // Сумма кредита в номинале валюты
    private double loanSumInKGS; // Сумма кредита в нац. валюте
    private double ownSum; // Собственная сумма
    private String loanTerm; // Срок кредита (мес)
    private String numberOfInstallments; // Количество взносов в КИБ
    private double principalBalance; // Остаток по основной сумме в номинале валюты
    private double principalBalanceInKGS; // Остаток основной суммы кредита в национальной валюте
    private LocalDateTime actualRepaymentDate; // Фактическая дата погашения
    private RoleOfClient participationType; // Вид участия
    private String numberOfOverdue; // Количество допущенных просрочек за весь период наблюдения
    private String numberOfOverdueDays; // Сумма всех дней просрочек за период – Неправильный подсчет из-за КИБа
    private int maxDelay; // Максим.непрерывное кол-во дней просрочки за весь период наблюдения
    private double remainingInterest; // Остаток по процентам
    private double paymentAmountInKIBReport; // Сумма платежа в отчете КИБ, в номинале валюты
    private double paymentAmount; // Сумма взноса (по формуле аннуитета), в номинале валюты
    private double paymentContributionInNationalCurrency; // Сумма взноса (по формуле аннуитета) в нац. валюте
    private String paymentType; // Тип платежа
    private int daysDelay; // Текущее кол-во дней просрочки
    private double delayAmount; // Сумма просрочки
    private double overdueInterest; // Просроченные проценты
    private double writtenOffAmount; // Списанная сумма
    private double writtenOffInterestAmount; // Списанная Сумма по процентам
    private double writtenOffAmountForAdditionalFees; // Списанная Сумма по доп. сборам
    private double additionalFeesAmount; // Сумма дополнительных сборов
    private String additionalFeesCurrency; // Валюта доп.сборов
    private int days1to6; // [1-6] дней
    private int days7to29; // [7-29] дней
    private int days30to59; // [30-59] дней
    private int days60to89; // [60-89] дней
    private int days90AndMore; // 90 дней и более
    private LocalDateTime prolongationDate; // Дата пролонгаций
    private double prolongationAmount; // Сумма пролонгаций
    private LocalDateTime restructuringDate; // Дата реструктуризации
    private PurposeOfFinancing purposeOfFinancing; // Цель финансирования
    private int maxDelayLast3Months; //  Макс. Просрочка за последние 3 месяца
    private int maxDelayLast24Months; //  Макс. Просрочка за последние 24 месяца
    private int maxDelayLast12Months; //  Макс. Просрочка за последние 12 месяца
    private int maxDelayLastFrom4To6Months; //Максимальная непрерывная просрочка c 4-го по 6-ой месяца
    private int maxDelayLastFrom7To12Months;//Максимальная непрерывная просрочка c 7го по 12ый месяца
    private int maxDelayLastFrom13to24Months;//Максимальная непрерывная просрочка c 13го по 24ый месяц
    private int maxDelayLastFrom25to36Months;//Максимальная непрерывная просрочка c 25го по 36oй месяц
    private int numberOfProlongationsForAllCreditAgreementsLast24Months; // Количество пролонгаций по всем кредитным договорам за последние 24 месяцев
    private double amountOfProlongationsForAllCreditAgreementsLast24Months; // Сумма пролонгаций по всем кредитным договорам за последние 24 месяца
    private double totalWrittenOffAmount; // Общая списанная сумма
    private int numberOfRestructuringsLast12Months; // Количество реструктуризаций за последние 12 месяцев
    private int numberOfRestructuringsFrom12To24Months; // Количество реструктуризаций с 13-ой по 24-ый месяц
    private int numberOfRestructuringsLast24Months; // Количество реструктуризаций за последние 24 месяца
    private int days1to6In12Month; // [1-6] дней - за последние 12 месяцев
    private int days7to29In12Month; // [7-29] дней - за последние 12 месяцев
    private int days30to59In12Month; // [30-59] дней - за последние 12 месяцев
    private int days60to89In12Month; // [60-89] дней - за последние 12 месяцев
    private int days90AndMoreIn12Month; // 90 дней и более - за последние 12 месяцев
    private int days1to6In24Month; // [1-6] дней - за последние 24 месяца
    private int days7to29In24Month; // [7-29] дней - за последние 24 месяца
    private int days30to59In24Month; // [30-59] дней - за последние 24 месяца
    private int days60to89In24Month; // [60-89] дней - за последние 24 месяца
    private int days90AndMoreIn24Month; // 90 дней и более - за последние 24 месяц
    private long remainingLoanTermInMonths; // Остаток срока кредита в месяцах
    private double annuityAmountBasedOnRemainingPrincipalAndTerm; // Сумма аннуитета по остатку ОД и оставшемуся сроку (по формуле аннуитета)
    private int countDelayLast3Months; // Количество просрочек за последние 3 месяца
    private int countDelayFrom4to6Month; // Количество просрочек за последние 4-6 месяца
    private int countDelayFrom7to12Month; // Количество просрочек за последние 7-12 месяца
    private int countDelayFrom13to24Month; // Количество просрочек за последние 13-24 месяца
    private int countDelayFrom25to36Month; // Количество просрочек за последние 25-36 месяца
}
