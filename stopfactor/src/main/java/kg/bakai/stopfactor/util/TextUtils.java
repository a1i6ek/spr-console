package kg.bakai.stopfactor.util;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Texts shown in the console: stop factor names rendered from a metric's template, and messages to an administrator. */
public final class TextUtils {

    /** {@code {key}} - the value of a param. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");
    /** {@code [ и <= {maxOverdueDays}]} - kept only when every param inside it has a value. */
    private static final Pattern OPTIONAL_SEGMENT = Pattern.compile("\\[([^\\[\\]]*)]");
    private static final Pattern SPACES = Pattern.compile("\\s{2,}");

    private TextUtils() {
    }

    /**
     * A name template with the param values, e.g. "с {periodFromMonth} по {periodToMonth} месяц[ до {maxOverdueDays}]"
     * with {periodFromMonth=7, periodToMonth=12} is "с 7 по 12 месяц". A missing value renders as nothing.
     */
    public static String renderTemplate(String template, Map<String, Object> params) {
        String withSegments = replaceAll(OPTIONAL_SEGMENT, template, segment ->
                placeholders(segment.group(1)).stream().allMatch(key -> params.get(key) != null) ? segment.group(1) : "");
        String rendered = replaceAll(PLACEHOLDER, withSegments, placeholder -> {
            Object value = params.get(placeholder.group(1));
            return value != null ? value.toString() : "";
        });
        return SPACES.matcher(rendered).replaceAll(" ").trim();
    }

    /** Param keys used by a template, in their order. */
    public static Set<String> placeholders(String template) {
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    /**
     * A sentence continued after a colon: "Укажите «Период с»." in "Стоп-фактор 2: укажите «Период с».". A word in
     * capitals (an abbreviation such as КИБ) or a quoted name keeps its case.
     */
    public static String lowerFirst(String sentence) {
        if (sentence.length() < 2 || !Character.isUpperCase(sentence.charAt(0)) || Character.isUpperCase(sentence.charAt(1))) {
            return sentence;
        }
        return Character.toLowerCase(sentence.charAt(0)) + sentence.substring(1);
    }

    private static String replaceAll(Pattern pattern, String text, Function<MatchResult, String> replacement) {
        return pattern.matcher(text).replaceAll(match -> Matcher.quoteReplacement(replacement.apply(match)));
    }
}
