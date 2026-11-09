package kg.bakai.stopfactor.report.provider;

import kg.bakai.spr.core.kib.dto.IndividualReport;
import kg.bakai.spr.core.kib.dto.LoanDto;
import kg.bakai.spr.core.kib.dto.enums.LoanPhase;
import kg.bakai.spr.core.kib.dto.enums.RoleOfClient;

import java.util.function.ToIntFunction;

/** The KIB loans of the client: only loans where the client is the main debtor or a guarantor count (not co-debtor). */
final class OwnLoans {

    private OwnLoans() {
    }

    static boolean isOwn(LoanDto loan) {
        return loan.getParticipationType() == RoleOfClient.MainDebtor || loan.getParticipationType() == RoleOfClient.Guarantor;
    }

    /** Sum of a value over the own loans of the client. */
    static int sum(IndividualReport individualReport, ToIntFunction<LoanDto> value) {
        return individualReport.loanDtoList().stream()
                .filter(OwnLoans::isOwn)
                .mapToInt(value)
                .sum();
    }

    /** Maximum of a value over the own loans of the client, at least 0, as in legacy. */
    static int max(IndividualReport individualReport, ToIntFunction<LoanDto> value) {
        int max = individualReport.loanDtoList().stream()
                .filter(OwnLoans::isOwn)
                .mapToInt(value)
                .max()
                .orElse(0);
        return Math.max(0, max);
    }

    /**
     * Current overdue principal and interest of the open loans the client guarantees. The KIB consolidation counts
     * the main debtor loans only, so this is what the current overdue amount adds to it; a loan counts like in the
     * consolidation: open, with overdue principal or interest.
     */
    static double guaranteedOverdueAmount(IndividualReport individualReport) {
        return individualReport.loanDtoList().stream()
                .filter(loan -> loan.getParticipationType() == RoleOfClient.Guarantor)
                .filter(loan -> loan.getPhaseOfLoan() == LoanPhase.Open)
                .filter(loan -> loan.getDelayAmount() > 0 || loan.getOverdueInterest() > 0)
                .mapToDouble(loan -> loan.getDelayAmount() + loan.getOverdueInterest())
                .sum();
    }
}
