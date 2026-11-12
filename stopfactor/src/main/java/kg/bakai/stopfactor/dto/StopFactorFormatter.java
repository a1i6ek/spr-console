package kg.bakai.stopfactor.dto;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The one-line description of a stop factor's rule, generated from the metric title and the param labels with their
 * values: it cannot diverge from the params the way a hand-typed name can. Admins edit the name freely; consumers
 * that must see what a rule actually is (check results, version history) show the summary.
 */
public final class StopFactorFormatter {

    private StopFactorFormatter() {
    }

    /** "Просроченная задолженность: Период с (месяц) 1, Период по (месяц) 6"; params left out (null values) are skipped. */
    public static String summary(String metricTitle, List<MetricParam> paramDefs, Map<String, Object> values) {
        String params = paramDefs.stream()
                .filter(param -> values.get(param.key()) != null)
                .map(param -> param.label() + " " + values.get(param.key()))
                .collect(Collectors.joining(", "));
        return params.isEmpty() ? metricTitle : metricTitle + ": " + params;
    }
}
