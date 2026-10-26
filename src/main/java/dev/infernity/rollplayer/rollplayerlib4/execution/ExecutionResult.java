package dev.infernity.rollplayer.rollplayerlib4.execution;

import dev.infernity.rollplayer.rollplayerlib4.SpanData;
import dev.infernity.rollplayer.rollplayerlib4.parsing.Node;

import java.util.List;

public sealed interface ExecutionResult<T> {
    record Expression<T>(Node source, T value, List<Rolls<T>> rolls) {
        public Expression {
            rolls = List.copyOf(rolls);
        }
    }

    record Success<T>(List<Expression<T>> expressions) implements ExecutionResult<T> {
        public Success {
            expressions = List.copyOf(expressions);
        }

        public List<T> values() {
            return expressions.stream().map(Expression::value).toList();
        }

        /** Dice groups for when I eventually add raw view. */
        public List<Rolls<T>> rolls() {
            return expressions.stream().flatMap(expression -> expression.rolls().stream()).toList();
        }
    }

    record Error<T>(String message, SpanData location) implements ExecutionResult<T> {
        public String pretty() {
            return message + "\n" + location.debugInfo();
        }
    }
}
