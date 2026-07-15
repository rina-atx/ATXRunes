package com.atx.runes.rune;

import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RunePlaceholders {
    private static final DecimalFormat FORMAT = new DecimalFormat("0.##");
    private static final Pattern BRACED = Pattern.compile("\\{([a-zA-Z0-9_]+)}");
    private static final Pattern PERCENT = Pattern.compile("%([a-zA-Z0-9_]+)%");

    private RunePlaceholders() {
    }

    public static Map<String, Object> values(RuneType type, RuneInstance rune) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tier", rune.tier());
        values.put("level", rune.tier());
        values.put("rune_tier", rune.tier());
        values.put("rune_level", rune.tier());
        values.put("max_tier", RuneInstance.MAX_TIER);
        values.put("value", type.value(rune.tier()));
        values.put("value_int", Math.max(0, Math.round(type.value(rune.tier()))));
        values.put("base_value", type.baseValue());
        values.put("rune_type", rune.typeId());
        values.put("rune_id", rune.id().toString());

        for (Map.Entry<String, Object> entry : type.placeholders().entrySet()) {
            Object resolved = resolve(entry.getValue(), values);
            values.put(entry.getKey(), resolved);
        }
        return values;
    }

    public static Object resolve(Object raw, RuneType type, RuneInstance rune) {
        return resolve(raw, values(type, rune));
    }

    public static String resolveText(String raw, RuneType type, RuneInstance rune) {
        return String.valueOf(resolveNoMath(raw, values(type, rune)));
    }

    private static Object resolve(Object raw, Map<String, Object> values) {
        if (!(raw instanceof String text)) {
            return raw;
        }
        String replaced = resolveNoMath(text, values);
        Optional<Double> expression = ExpressionParser.tryEvaluate(replaced);
        if (expression.isPresent()) {
            double value = expression.get();
            if (Math.rint(value) == value) {
                return (long) value;
            }
            return value;
        }
        return replaced;
    }

    private static String resolveNoMath(String text, Map<String, Object> values) {
        String resolved = replace(text, BRACED, values);
        resolved = replace(resolved, PERCENT, values);
        return resolved;
    }

    private static String replace(String text, Pattern pattern, Map<String, Object> values) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = values.get(key);
            if (value == null) {
                value = values.get(key.toLowerCase());
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(format(value == null ? matcher.group() : value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String format(Object value) {
        if (value instanceof Number number) {
            return FORMAT.format(number.doubleValue());
        }
        return String.valueOf(value);
    }

    private static final class ExpressionParser {
        private final String input;
        private int index;

        private ExpressionParser(String input) {
            this.input = input.replace("\"", "").replace("'", "");
        }

        static Optional<Double> tryEvaluate(String input) {
            if (!input.matches("[0-9+\\-*/().\\s]+")) {
                return Optional.empty();
            }
            try {
                ExpressionParser parser = new ExpressionParser(input);
                double result = parser.expression();
                parser.skipWhitespace();
                return parser.index == parser.input.length() ? Optional.of(result) : Optional.empty();
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        }

        private double expression() {
            double value = term();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    value += term();
                } else if (match('-')) {
                    value -= term();
                } else {
                    return value;
                }
            }
        }

        private double term() {
            double value = factor();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    value *= factor();
                } else if (match('/')) {
                    value /= factor();
                } else {
                    return value;
                }
            }
        }

        private double factor() {
            skipWhitespace();
            if (match('+')) {
                return factor();
            }
            if (match('-')) {
                return -factor();
            }
            if (match('(')) {
                double value = expression();
                if (!match(')')) {
                    throw new IllegalArgumentException("Missing closing parenthesis");
                }
                return value;
            }
            return number();
        }

        private double number() {
            skipWhitespace();
            int start = index;
            while (index < input.length() && (Character.isDigit(input.charAt(index)) || input.charAt(index) == '.')) {
                index++;
            }
            if (start == index) {
                throw new IllegalArgumentException("Expected number");
            }
            return Double.parseDouble(input.substring(start, index));
        }

        private boolean match(char expected) {
            skipWhitespace();
            if (index < input.length() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }
    }
}
