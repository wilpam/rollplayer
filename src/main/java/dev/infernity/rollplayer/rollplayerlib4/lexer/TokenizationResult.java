package dev.infernity.rollplayer.rollplayerlib4.lexer;

import dev.infernity.rollplayer.rollplayerlib4.SpanData;

import java.util.List;

public sealed interface TokenizationResult {
    record Success(List<Token> tokens) implements TokenizationResult {
        public Success {
            tokens = List.copyOf(tokens);
        }

        public String pretty() {
            var result = new StringBuilder().append(tokens.size()).append(" tokens:");
            for (Token token : tokens) {
                result.append('\n').append(token);
            }
            return result.toString();
        }
    }

    record Error(String message, SpanData location) implements TokenizationResult {}
}
