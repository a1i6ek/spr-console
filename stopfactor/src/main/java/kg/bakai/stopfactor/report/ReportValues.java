package kg.bakai.stopfactor.report;

/**
 * Values of the KIB / OB reports of one application. The fixed-period counters of the old algorithm are a fact of
 * their own ({@link LegacyCounters}), made only when a calculator needs them.
 *
 * @param activeDisputes       active disputes in KIB
 * @param unjustifiedDisputes  unjustified disputes in the past in KIB
 * @param currentOverdueDays   largest current overdue in days, KIB loans of the client and OB credits
 * @param currentOverdueAmount current overdue amount, KIB consolidation and OB credits
 * @param openCredits          open credit contracts in KIB
 */
public record ReportValues(
        double activeDisputes,
        double unjustifiedDisputes,
        double currentOverdueDays,
        double currentOverdueAmount,
        double openCredits
) {

    /** Without a KIB report only the current overdue from OB is known; everything else is 0. */
    public static ReportValues withoutKibReport(double currentOverdueDays, double currentOverdueAmount) {
        return new ReportValues(0, 0, currentOverdueDays, currentOverdueAmount, 0);
    }
}
