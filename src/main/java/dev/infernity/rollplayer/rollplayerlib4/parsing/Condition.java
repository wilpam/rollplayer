package dev.infernity.rollplayer.rollplayerlib4.parsing;

import dev.infernity.rollplayer.rollplayerlib4.SpanData;

public sealed interface Condition {
    SpanData range();

    record Comparison(Operator operator, Node.Number value, SpanData range) implements Condition {}

    record RollIndex(Node.Number index, SpanData range) implements Condition {}

    record Range(Node.Number lowerBound, Node.Number upperBound, SpanData range) implements Condition {}

    enum Operator { EQUAL, NOT_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL }
}
