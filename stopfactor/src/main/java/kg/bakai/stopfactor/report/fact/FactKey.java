package kg.bakai.stopfactor.report.fact;

/**
 * Typed name of a fact of an application: {@code FactKey<ReportValues>} gives a {@code ReportValues}.
 * Keys are compared by identity, so each one is declared once, as a constant of {@link Facts}.
 *
 * @param <T> type of the fact
 */
public final class FactKey<T> {

    private final String name;

    private FactKey(String name) {
        this.name = name;
    }

    public static <T> FactKey<T> of(String name) {
        return new FactKey<>(name);
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
