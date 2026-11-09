package kg.bakai.spr.core.kib.dto;

import kg.bakai.spr.core.kib.dto.enums.LoanPhase;
import kg.bakai.spr.core.kib.dto.enums.LoanStatus;
import kg.bakai.spr.core.kib.dto.enums.RoleOfClient;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DUMMY of the SPR class with the same name and package. Only the getters the stop factor check uses, with the types
 * of SPR (KibIndividualReportMapper fills delayAmount and overdueInterest from Amount.value()).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoanDto {
    private RoleOfClient participationType;
    private LoanStatus loanStatus;
    private int numberOfRestructuringsLast12Months;
    private int numberOfRestructuringsFrom12To24Months;
    private int daysDelay;
    private LoanPhase phaseOfLoan;
    /** Overdue principal. */
    private double delayAmount;
    /** Overdue interest. */
    private double overdueInterest;

    /** An open loan without overdue amounts, for tests and the test check. */
    public LoanDto(RoleOfClient participationType, LoanStatus loanStatus, int numberOfRestructuringsLast12Months,
                   int numberOfRestructuringsFrom12To24Months, int daysDelay) {
        this(participationType, loanStatus, numberOfRestructuringsLast12Months, numberOfRestructuringsFrom12To24Months,
                daysDelay, LoanPhase.Open, 0, 0);
    }
}
