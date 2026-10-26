package dev.infernity.rollplayer.rollplayerlib4.lexer;

import dev.infernity.rollplayer.rollplayerlib4.SpanData;
import dev.infernity.rollplayer.rollplayerlib4.Keywords;

public sealed interface Token {
    SpanData range();

    record Error(String message, SpanData range) implements Token {}

    record Number(String value, SpanData range) implements Token {}

    record RangeDeclaration(SpanData range) implements Token {}

    record DiceDeclaration(SpanData range) implements Token {}

    record OpenBracket(SpanData range) implements Token {}
    record CloseBracket(SpanData range) implements Token {}

    record Keyword(Keywords keyword, SpanData range) implements Token {}

    record Minus(SpanData range) implements Token {}

    record Plus(SpanData range) implements Token {}

    record Asterisk(SpanData range) implements Token {}

    record Slash(SpanData range) implements Token {}

    record Caret(SpanData range) implements Token {}

    record OpenParenthesis(SpanData range) implements Token {}
    record CloseParenthesis(SpanData range) implements Token {}

    record Comma(SpanData range) implements Token {}

    record GreaterThan(SpanData range) implements Token {}
    record GreaterThanOrEqual(SpanData range) implements Token {}
    record LessThan(SpanData range) implements Token {}
    record LessThanOrEqual(SpanData range) implements Token {}
    record Equals(SpanData range) implements Token {}
    record NotEquals(SpanData range) implements Token {}

    record TargetDeclaration(SpanData range) implements Token {}

    record RollIndexDeclaration(SpanData range) implements Token {}

    record EndOfInput(SpanData range) implements Token {}
}
