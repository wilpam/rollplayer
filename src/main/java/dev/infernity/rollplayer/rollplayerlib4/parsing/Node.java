package dev.infernity.rollplayer.rollplayerlib4.parsing;

import dev.infernity.rollplayer.rollplayerlib4.SpanData;

import java.util.List;

public sealed interface Node {
    SpanData range();

    record Number(String value, SpanData range) implements Node {}

    record Unary(UnaryOperator operator, Node operand, SpanData range) implements Node {}

    record Binary(BinaryOperator operator, Node left, Node right, SpanData range) implements Node {}

    record Group(Node expression, SpanData range) implements Node {}

    record Dice(Number count, Number lowerBound, Number upperBound,
                List<Modifier> modifiers, SpanData range) implements Node {
        public Dice {
            modifiers = List.copyOf(modifiers);
        }
    }

    enum UnaryOperator { PLUS, MINUS }

    enum BinaryOperator { ADD, SUBTRACT, MULTIPLY, DIVIDE, POWER }
}
