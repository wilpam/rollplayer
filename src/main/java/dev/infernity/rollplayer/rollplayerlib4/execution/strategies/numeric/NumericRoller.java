package dev.infernity.rollplayer.rollplayerlib4.execution.strategies.numeric;

import dev.infernity.rollplayer.rollplayerlib4.Keywords;
import dev.infernity.rollplayer.rollplayerlib4.Markup;
import dev.infernity.rollplayer.rollplayerlib4.SpanData;
import dev.infernity.rollplayer.rollplayerlib4.execution.EvaluationException;
import dev.infernity.rollplayer.rollplayerlib4.execution.Rolls;
import dev.infernity.rollplayer.rollplayerlib4.parsing.Condition;
import dev.infernity.rollplayer.rollplayerlib4.parsing.Modifier;
import dev.infernity.rollplayer.rollplayerlib4.parsing.Node;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

final class NumericRoller {
    private static final int MAX_DICE = 100;
    private static final int MAX_REPETITIONS = 50;
    private static final int MAX_DRAWS = 10_000;
    private static final int DEFAULT_EXPLOSIONS = 5;

    private final SimpleRollingStrategy strategy;
    private final Node.Dice source;
    private final BigInteger lower;
    private final BigInteger upper;
    private final ArrayList<Markup.Group<Double>> values = new ArrayList<>();
    private int draws;

    NumericRoller(SimpleRollingStrategy strategy, Node.Dice source) {
        this.strategy = strategy;
        this.source = source;
        lower = faceBound(source.lowerBound());
        upper = faceBound(source.upperBound());
        if (lower.compareTo(upper) > 0) {
            throw error("Die lower bound must not exceed its upper bound.", source.range());
        }
    }

    Rolls<Double> evaluate() {
        int count = boundedCount(source.count(), MAX_DICE, "Dice count");
        for (int i = 0; i < count; i++) {
            values.add(new Markup.Group<>(Markup.NONE, draw()));
        }
        var original = List.copyOf(values);
        var modifiers = source.modifiers();
        for (int i = 0; i < modifiers.size(); i++) {
            Modifier modifier = modifiers.get(i);
            // Combine adjacent keeps to preserve both extremes from the original pool.
            if (modifier instanceof Modifier.Ranked first && isKeep(first.keyword())
                    && i + 1 < modifiers.size() && modifiers.get(i + 1) instanceof Modifier.Ranked second
                    && isKeep(second.keyword()) && first.keyword() != second.keyword()) {
                int firstCount = rankCount(first);
                int secondCount = rankCount(second);
                keepRanks(first.keyword() == Keywords.KEEP_HIGHEST ? firstCount : secondCount,
                        first.keyword() == Keywords.KEEP_LOWEST ? firstCount : secondCount);
                i++;
                continue;
            }
            switch (modifier) {
                case Modifier.Ranked ranked -> applyRanked(ranked);
                case Modifier.Conditional conditional -> applyConditional(conditional);
                case Modifier.Targeted targeted -> applyTargeted(targeted);
            }
        }
        double total = 0;
        for (var value : values) {
            total = strategy.binary(Node.BinaryOperator.ADD, total, value.value());
        }
        return new Rolls<>(source, total, original, values);
    }

    private double draw() {
        if (++draws > MAX_DRAWS) {
            throw error("A dice group cannot generate more than " + MAX_DRAWS + " draws.", source.range());
        }
        BigInteger value = strategy.rollFace(lower, upper);
        if (value.compareTo(lower) < 0 || value.compareTo(upper) > 0) {
            throw error("Strategy returned a face outside the die's integral bounds.", source.range());
        }
        double numericValue = value.doubleValue();
        if (!Double.isFinite(numericValue)) {
            throw error("Rolled face cannot be represented as a finite double.", source.range());
        }
        return numericValue;
    }

    private void applyRanked(Modifier.Ranked modifier) {
        int count = rankCount(modifier);
        switch (modifier.keyword()) {
            case KEEP_HIGHEST -> keepRanks(count, 0);
            case KEEP_LOWEST -> keepRanks(0, count);
            case BOLD_HIGHEST -> highlightRank(count, true, Markup.BOLD);
            case BOLD_LOWEST -> highlightRank(count, false, Markup.BOLD);
            case ITALIC_HIGHEST -> highlightRank(count, true, Markup.ITALIC);
            case ITALIC_LOWEST -> highlightRank(count, false, Markup.ITALIC);
            default -> throw error("Expected a keep or highlight rank modifier.", modifier.range());
        }
    }

    private int rankCount(Modifier.Ranked modifier) {
        return boundedCount(modifier.count(), Integer.MAX_VALUE, "Rank count");
    }

    private static boolean isKeep(Keywords keyword) {
        return keyword == Keywords.KEEP_HIGHEST || keyword == Keywords.KEEP_LOWEST;
    }

    private void keepRanks(int high, int low) {
        var indices = IntStream.range(0, values.size()).boxed()
                .sorted(Comparator.comparingDouble(i -> values.get(i).value())).toList();
        var selected = new boolean[values.size()];
        for (int i = 0; i < Math.min(low, indices.size()); i++) {
            selected[indices.get(i)] = true;
        }
        for (int i = 0; i < Math.min(high, indices.size()); i++) {
            selected[indices.get(indices.size() - i - 1)] = true;
        }
        var kept = new ArrayList<Markup.Group<Double>>();
        for (int i = 0; i < values.size(); i++) {
            if (selected[i]) kept.add(values.get(i));
        }
        values.clear();
        values.addAll(kept);
    }

    // Ties share emphasis even when that highlights more than the requested count.
    private void highlightRank(int count, boolean highest, Markup markup) {
        count = Math.min(count, values.size());
        if (count == 0) return;
        var sorted = values.stream().map(Markup.Group::value).sorted().toList();
        double cutoff = sorted.get(highest ? sorted.size() - count : count - 1);
        highlight((index, value) -> highest ? value >= cutoff : value <= cutoff, markup);
    }

    private void applyConditional(Modifier.Conditional modifier) {
        RollPredicate condition = modifier.conditions().isEmpty() && modifier.keyword() == Keywords.EXPLODE
                ? (index, value) -> value == upper.doubleValue() : predicate(modifier.conditions());
        switch (modifier.keyword()) {
            case DROP -> filter(condition, false);
            case KEEP -> filter(condition, true);
            case BOLD_CONDITIONAL -> highlight(condition, Markup.BOLD);
            case ITALIC_CONDITIONAL -> highlight(condition, Markup.ITALIC);
            case REROLL -> reroll(condition, repetitionLimit(modifier, 1));
            case EXPLODE -> explode(condition, repetitionLimit(modifier, DEFAULT_EXPLOSIONS));
            default -> throw error("Expected a conditional modifier.", modifier.range());
        }
    }

    private void filter(RollPredicate condition, boolean keepMatches) {
        var kept = new ArrayList<Markup.Group<Double>>();
        for (int i = 0; i < values.size(); i++) {
            var value = values.get(i);
            if (condition.test(i + 1, value.value()) == keepMatches) kept.add(value);
        }
        values.clear();
        values.addAll(kept);
    }

    private int repetitionLimit(Modifier.Conditional modifier, int defaultLimit) {
        return modifier.limit().map(number -> boundedCount(number, MAX_REPETITIONS, "Repetition limit"))
                .orElse(defaultLimit);
    }

    private void reroll(RollPredicate condition, int limit) {
        for (int i = 0; i < values.size(); i++) {
            var value = values.get(i);
            for (int attempt = 0; attempt < limit && condition.test(i + 1, value.value()); attempt++) {
                value = value.withValue(draw());
            }
            values.set(i, value);
        }
    }

    private void explode(RollPredicate condition, int limit) {
        int initialSize = values.size();
        for (int i = 0; i < initialSize; i++) {
            double value = values.get(i).value();
            int index = i + 1;
            for (int extra = 0; extra < limit && condition.test(index, value); extra++) {
                value = draw();
                values.add(new Markup.Group<>(Markup.NONE, value));
                index = values.size();
            }
        }
    }

    private void highlight(RollPredicate condition, Markup markup) {
        for (int i = 0; i < values.size(); i++) {
            var value = values.get(i);
            if (condition.test(i + 1, value.value())) values.set(i, value.withMarkup(markup));
        }
    }

    private void applyTargeted(Modifier.Targeted modifier) {
        var selected = new boolean[values.size()];
        if (modifier.all()) {
            Arrays.fill(selected, true);
        } else {
            var condition = predicate(modifier.conditions());
            for (int i = 0; i < values.size(); i++) {
                selected[i] = condition.test(i + 1, values.get(i).value());
            }
            for (var number : modifier.indices()) {
                int index = integer(number);
                if (index < 1 || index > values.size()) {
                    throw error("Target index " + index + " is outside the current roll list.", number.range());
                }
                selected[index - 1] = true;
            }
        }
        for (var adjustment : modifier.adjustments()) {
            double operand = literal(adjustment.operand());
            for (int i = 0; i < values.size(); i++) {
                if (!selected[i]) continue;
                var value = values.get(i);
                try {
                    values.set(i, value.withValue(strategy.binary(adjustment.operator(), value.value(), operand)));
                } catch (ArithmeticException failure) {
                    throw error(failure.getMessage(), adjustment.range());
                }
            }
        }
    }

    private RollPredicate predicate(List<Condition> conditions) {
        var alternatives = new ArrayList<RollPredicate>();
        for (var condition : conditions) {
            switch (condition) {
                case Condition.Comparison comparison -> {
                    double bound = literal(comparison.value());
                    alternatives.add((index, value) -> switch (comparison.operator()) {
                        case EQUAL -> value == bound;
                        case NOT_EQUAL -> value != bound;
                        case LESS_THAN -> value < bound;
                        case LESS_THAN_OR_EQUAL -> value <= bound;
                        case GREATER_THAN -> value > bound;
                        case GREATER_THAN_OR_EQUAL -> value >= bound;
                    });
                }
                case Condition.RollIndex rollIndex -> {
                    int target = integer(rollIndex.index());
                    if (target < 1) {
                        throw error("Roll index conditions must use a positive integer.", rollIndex.index().range());
                    }
                    alternatives.add((index, value) -> index == target);
                }
                case Condition.Range range -> {
                    double first = literal(range.lowerBound());
                    double second = literal(range.upperBound());
                    double min = Math.min(first, second);
                    double max = Math.max(first, second);
                    alternatives.add((index, value) -> value >= min && value <= max);
                }
            }
        }
        return (index, value) -> alternatives.stream().anyMatch(condition -> condition.test(index, value));
    }

    @FunctionalInterface
    private interface RollPredicate {
        boolean test(int index, double value);
    }

    private double literal(Node.Number number) {
        try {
            return strategy.number(number.value());
        } catch (ArithmeticException | IllegalArgumentException failure) {
            throw error(failure.getMessage(), number.range());
        }
    }

    private static BigInteger faceBound(Node.Number number) {
        try {
            return new BigDecimal(number.value()).toBigIntegerExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw error("Die face bounds must be integers.", number.range());
        }
    }

    private static int integer(Node.Number number) {
        try {
            return new BigDecimal(number.value()).intValueExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw error("Expected an integer in the 32-bit range.", number.range());
        }
    }

    private static int boundedCount(Node.Number number, int max, String label) {
        int value = integer(number);
        if (value < 0 || value > max) {
            throw error(label + " must be between 0 and " + max + ".", number.range());
        }
        return value;
    }

    private static EvaluationException error(String message, SpanData location) {
        return new EvaluationException(message, location);
    }
}
