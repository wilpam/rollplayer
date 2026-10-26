package dev.infernity.rollplayer.rollplayerlib4.lexer;

import dev.infernity.rollplayer.rollplayerlib4.Keywords;
import dev.infernity.rollplayer.rollplayerlib4.SpanData;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.math.BigDecimal;

public final class Lexer {
    private static final Map<String, Keywords> KEYWORDS = Map.ofEntries(
            Map.entry("drop", Keywords.DROP),
            Map.entry("dr", Keywords.DROP),
            Map.entry("reroll", Keywords.REROLL),
            Map.entry("rr", Keywords.REROLL),
            Map.entry("R", Keywords.REROLL),
            Map.entry("keep", Keywords.KEEP),
            Map.entry("kh", Keywords.KEEP_HIGHEST),
            Map.entry("kl", Keywords.KEEP_LOWEST),
            Map.entry("bold", Keywords.BOLD_CONDITIONAL),
            Map.entry("ital", Keywords.ITALIC_CONDITIONAL),
            Map.entry("explode", Keywords.EXPLODE),
            Map.entry("bohi", Keywords.BOLD_HIGHEST),
            Map.entry("ithi", Keywords.ITALIC_HIGHEST),
            Map.entry("bolo", Keywords.BOLD_LOWEST),
            Map.entry("itlo", Keywords.ITALIC_LOWEST)
    );

    private final String source;
    private int cursor;

    private Lexer(String source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public static TokenizationResult tokenize(String source) {
        return new Lexer(source).tokenizeLoop();
    }

    private TokenizationResult tokenizeLoop() {
        var tokens = new ArrayList<Token>();
        while (true) {
            Token token = tokenizeOnce();
            if (token instanceof Token.Error error) {
                return new TokenizationResult.Error(error.message(), error.range());
            }
            tokens.add(token);
            if (token instanceof Token.EndOfInput) {
                return new TokenizationResult.Success(tokens);
            }
        }
    }

    private Token tokenizeOnce() {
        while (canRead() && Character.isWhitespace(source.charAt(cursor))) {
            cursor++;
        }

        int start = cursor;
        if (!canRead()) {
            return new Token.EndOfInput(createSpan(start));
        }

        // Prefer the longest keyword, especially "drop"/"dr" over dice "d".
        String keyword = null;
        for (String spelling : KEYWORDS.keySet()) {
            if (source.startsWith(spelling, cursor)
                    && (keyword == null || spelling.length() > keyword.length())) {
                keyword = spelling;
            }
        }
        if (keyword != null) {
            cursor += keyword.length();
            return new Token.Keyword(KEYWORDS.get(keyword), createSpan(start));
        }

        if (source.startsWith("..", cursor)) {
            cursor += 2;
            return new Token.RangeDeclaration(createSpan(start));
        }

        char ch = source.charAt(cursor);
        if (isDigit(ch) || ch == '.') {
            return readNumber();
        }

        cursor++;
        return switch (ch) {
            case 'd', 'D' -> new Token.DiceDeclaration(createSpan(start));
            case ':' -> new Token.RangeDeclaration(createSpan(start));
            case '{' -> new Token.OpenBracket(createSpan(start));
            case '}' -> new Token.CloseBracket(createSpan(start));
            case '(' -> new Token.OpenParenthesis(createSpan(start));
            case ')' -> new Token.CloseParenthesis(createSpan(start));
            case '+' -> new Token.Plus(createSpan(start));
            case '-' -> new Token.Minus(createSpan(start));
            case '*' -> new Token.Asterisk(createSpan(start));
            case '/' -> new Token.Slash(createSpan(start));
            case '^' -> new Token.Caret(createSpan(start));
            case ',' -> new Token.Comma(createSpan(start));
            case '=' -> new Token.Equals(createSpan(start));
            case '>' -> match('=')
                    ? new Token.GreaterThanOrEqual(createSpan(start))
                    : new Token.GreaterThan(createSpan(start));
            case '<' -> match('=')
                    ? new Token.LessThanOrEqual(createSpan(start))
                    : new Token.LessThan(createSpan(start));
            case '!' -> match('=')
                    ? new Token.NotEquals(createSpan(start))
                    : new Token.Keyword(Keywords.EXPLODE, createSpan(start));
            case 'i' -> new Token.TargetDeclaration(createSpan(start));
            case '#' -> new Token.RollIndexDeclaration(createSpan(start));
            default -> new Token.Error("Unrecognized character '" + ch + "'.", createSpan(start));
        };
    }

    private Token readNumber() {
        int start = cursor;
        while (canRead()) {
            char ch = source.charAt(cursor);
            // A double dot belongs to a range, so if we get e.g. 1..3 we need to stop.
            if (source.startsWith("..", cursor) || !(isDigit(ch) || ch == '.' || ch == '_')) {
                break;
            }
            cursor++;
        }
        String value = source.substring(start, cursor).replace("_", "");
        try {
            new BigDecimal(value);
            return new Token.Number(value, createSpan(start));
        } catch (NumberFormatException e) {
            return new Token.Error("Invalid number '" + source.substring(start, cursor) + "'.", createSpan(start));
        }
    }

    private boolean canRead() {
        return cursor < source.length();
    }

    private boolean match(char expected) {
        if (canRead() && source.charAt(cursor) == expected) {
            cursor++;
            return true;
        }
        return false;
    }

    private static boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    private SpanData createSpan(int start) {
        return new SpanData(start, cursor, source);
    }
}
