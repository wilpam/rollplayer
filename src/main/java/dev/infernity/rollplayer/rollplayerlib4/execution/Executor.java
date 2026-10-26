package dev.infernity.rollplayer.rollplayerlib4.execution;

import dev.infernity.rollplayer.rollplayerlib4.Markup;
import dev.infernity.rollplayer.rollplayerlib4.execution.strategies.RollStrategy;
import dev.infernity.rollplayer.rollplayerlib4.parsing.Node;

import java.util.List;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class Executor<T> {
    private final List<Node> nodes;
    private final RandomGenerator randomGenerator;

    public Executor(List<Node> nodes, RandomGenerator randomGenerator) {
        this.nodes = List.copyOf(nodes);
        this.randomGenerator = Objects.requireNonNull(randomGenerator, "randomGenerator");
    }

    /** One result per top-level expression */
    public List<Markup.Group<T>> execute(RollStrategy<T> strategy) {
        return switch (executeDetailed(strategy)) {
            case ExecutionResult.Success<T> success -> success.values().stream()
                    .map(value -> new Markup.Group<>(Markup.NONE, value)).toList();
            case ExecutionResult.Error<T> error -> throw new EvaluationException(error.message(), error.location());
        };
    }

    public ExecutionResult<T> executeDetailed(RollStrategy<T> strategy) {
        Objects.requireNonNull(strategy, "strategy");
        if (strategy.supportsRandom()) {
            strategy.withRandom(randomGenerator);
        }
        var expressions = new ArrayList<ExecutionResult.Expression<T>>();
        try {
            for (Node node : nodes) {
                var rolls = new ArrayList<Rolls<T>>();
                T value = evaluate(node, strategy, rolls);
                expressions.add(new ExecutionResult.Expression<>(node, value, rolls));
            }
            return new ExecutionResult.Success<>(expressions);
        } catch (EvaluationException failure) {
            return new ExecutionResult.Error<>(failure.getMessage(), failure.location());
        }
    }

    private T evaluate(Node root, RollStrategy<T> strategy, List<Rolls<T>> rolls) {
        var pending = new ArrayDeque<Frame>();
        var values = new ArrayList<T>();
        pending.push(new Frame(root, false));
        while (!pending.isEmpty()) {
            var frame = pending.pop();
            Node node = frame.node();
            if (!frame.ready()) {
                switch (node) {
                    case Node.Binary binary -> {
                        pending.push(new Frame(node, true));
                        pending.push(new Frame(binary.right(), false));
                        pending.push(new Frame(binary.left(), false));
                        continue;
                    }
                    case Node.Unary unary -> {
                        pending.push(new Frame(node, true));
                        pending.push(new Frame(unary.operand(), false));
                        continue;
                    }
                    case Node.Group group -> {
                        pending.push(new Frame(group.expression(), false));
                        continue;
                    }
                    default -> {}
                }
            }
            try {
                T value = switch (node) {
                    case Node.Number number -> strategy.number(number.value());
                    case Node.Dice dice -> {
                        var result = strategy.roll(dice);
                        rolls.add(result);
                        yield result.total();
                    }
                    case Node.Unary unary -> strategy.unary(unary.operator(), values.removeLast());
                    case Node.Binary binary -> {
                        T right = values.removeLast();
                        T left = values.removeLast();
                        yield strategy.binary(binary.operator(), left, right);
                    }
                    case Node.Group ignored -> throw new IllegalStateException("Group was not unwrapped.");
                };
                values.add(value);
            } catch (ArithmeticException | IllegalArgumentException | UnsupportedOperationException failure) {
                throw new EvaluationException(failure.getMessage(), node.range());
            }
        }
        return values.getFirst();
    }

    private record Frame(Node node, boolean ready) {}
}
