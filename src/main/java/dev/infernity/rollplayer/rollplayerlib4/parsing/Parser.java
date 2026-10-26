package dev.infernity.rollplayer.rollplayerlib4.parsing;

import dev.infernity.rollplayer.rollplayerlib4.Keywords;
import dev.infernity.rollplayer.rollplayerlib4.SpanData;
import dev.infernity.rollplayer.rollplayerlib4.lexer.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Parser {
    private final List<Token> tokens;
    private int index;
    private int depth;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static ParsingResult parse(List<Token> tokens) {
        if (tokens.isEmpty()) {
            return new ParsingResult.Error("Expected an expression.", new SpanData(0, 0, ""));
        }
        if (!(tokens.getLast() instanceof Token.EndOfInput)) {
            var last = tokens.getLast().range();
            return new ParsingResult.Error("Expected an end-of-input token.",
                    new SpanData(last.cursorEnd(), last.cursorEnd(), last.originalString()));
        }
        try {
            return new Parser(List.copyOf(tokens)).parseProgram();
        } catch (ParseFailure failure) {
            return new ParsingResult.Error(failure.getMessage(), failure.location);
        }
    }

    private ParsingResult parseProgram() {
        var nodes = new ArrayList<Node>();
        if (peek() instanceof Token.EndOfInput) {
            throw fail("Expected an expression.");
        }
        while (true) {
            nodes.add(parseExpression());
            if (peek() instanceof Token.EndOfInput) {
                if (index != tokens.size() - 1) {
                    throw fail("Unexpected tokens after end of input.");
                }
                return new ParsingResult.Success(nodes);
            }
            // The lexer skips whitespace, but source spans retain expression boundaries.
            if (!startsExpression(peek()) || !hasGapBeforeCurrent()) {
                throw fail("Expected an operator or whitespace between expressions.");
            }
        }
    }

    private Node parseExpression() {
        Node left = parseProduct();
        while (peek() instanceof Token.Plus || peek() instanceof Token.Minus) {
            var operator = binaryOperator(take());
            Node right = parseProduct();
            left = new Node.Binary(operator, left, right, SpanData.merge(left.range(), right.range()));
        }
        return left;
    }

    private Node parseProduct() {
        Node left = parseUnary();
        while (peek() instanceof Token.Asterisk || peek() instanceof Token.Slash) {
            var operator = binaryOperator(take());
            Node right = parseUnary();
            left = new Node.Binary(operator, left, right, SpanData.merge(left.range(), right.range()));
        }
        return left;
    }

    private Node parseUnary() {
        if (++depth > 256) {
            throw fail("Expression nesting is too deep.");
        }
        try {
            if (peek() instanceof Token.Plus || peek() instanceof Token.Minus) {
                Token sign = take();
                Node operand = parseUnary();
                var operator = sign instanceof Token.Plus ? Node.UnaryOperator.PLUS : Node.UnaryOperator.MINUS;
                return new Node.Unary(operator, operand, SpanData.merge(sign.range(), operand.range()));
            }
            return parsePower();
        } finally {
            depth--;
        }
    }

    // Powers associate right-to-left and bind more tightly than a leading sign.
    private Node parsePower() {
        Node left = parseDice();
        if (peek() instanceof Token.Caret) {
            take();
            Node right = parseUnary();
            return new Node.Binary(Node.BinaryOperator.POWER, left, right,
                    SpanData.merge(left.range(), right.range()));
        }
        return left;
    }

    private Node parseDice() {
        SpanData start = peek().range();
        Node count = peek() instanceof Token.DiceDeclaration ? implicitOne(start) : parsePrimary();
        if (!(peek() instanceof Token.DiceDeclaration)) {
            return count;
        }
        if (!(count instanceof Node.Number number)) {
            throw fail("Dice count must be a number.");
        }
        take();
        Node.Number firstBound = readSignedNumber();
        Node.Number lower = implicitOne(firstBound.range());
        Node.Number upper = firstBound;
        if (peek() instanceof Token.RangeDeclaration) {
            take();
            lower = firstBound;
            upper = readSignedNumber();
        } else if (firstBound.value().startsWith("-")) {
            // d-6 is the negative counterpart of d6: faces -6 through -1.
            lower = firstBound;
            upper = new Node.Number("-1", implicitOne(firstBound.range()).range());
        }
        var modifiers = new ArrayList<Modifier>();
        while (peek() instanceof Token.Keyword || peek() instanceof Token.TargetDeclaration) {
            modifiers.add(parseModifier());
        }
        return new Node.Dice(number, lower, upper, modifiers, throughPrevious(start));
    }

    private Node parsePrimary() {
        if (peek() instanceof Token.Number number) {
            take();
            return new Node.Number(number.value(), number.range());
        }
        if (peek() instanceof Token.OpenParenthesis) {
            var open = take();
            Node expression = parseExpression();
            require(Token.CloseParenthesis.class, "Expected ')' to close the expression.");
            return new Node.Group(expression, throughPrevious(open.range()));
        }
        throw fail("Expected a number, dice roll, or parenthesized expression.");
    }

    private Modifier parseModifier() {
        if (peek() instanceof Token.TargetDeclaration) {
            return parseTargeted();
        }
        var token = (Token.Keyword) take();
        Keywords keyword = token.keyword();
        return switch (keyword) {
            case KEEP_HIGHEST, KEEP_LOWEST, BOLD_HIGHEST, BOLD_LOWEST, ITALIC_HIGHEST, ITALIC_LOWEST -> {
                // A spaced "2d6" starts another roll rather than supplying a rank count.
                boolean nextRoll = hasGapBeforeCurrent() && peek() instanceof Token.Number
                        && index + 1 < tokens.size() && tokens.get(index + 1) instanceof Token.DiceDeclaration;
                Node.Number count = peek() instanceof Token.Number && !nextRoll
                        ? readUnsignedNumber() : implicitOne(token.range());
                yield new Modifier.Ranked(keyword, count, throughPrevious(token.range()));
            }
            case DROP, KEEP, REROLL, BOLD_CONDITIONAL, ITALIC_CONDITIONAL, EXPLODE -> {
                List<Condition> conditions = keyword == Keywords.EXPLODE && !(peek() instanceof Token.OpenBracket)
                        ? List.of() : parseConditions();
                Optional<Node.Number> limit = Optional.empty();
                if ((keyword == Keywords.REROLL || keyword == Keywords.EXPLODE)
                        && peek() instanceof Token.RangeDeclaration) {
                    take();
                    limit = Optional.of(readUnsignedNumber());
                }
                yield new Modifier.Conditional(keyword, conditions, limit, throughPrevious(token.range()));
            }
        };
    }

    private List<Condition> parseConditions() {
        require(Token.OpenBracket.class, "Expected '{' before modifier conditions.");
        var conditions = new ArrayList<Condition>();
        conditions.add(parseCondition());
        while (peek() instanceof Token.Comma) {
            take();
            conditions.add(parseCondition());
        }
        require(Token.CloseBracket.class, "Expected ',' or '}' after a condition.");
        return conditions;
    }

    private Condition parseCondition() {
        SpanData start = peek().range();
        if (peek() instanceof Token.RollIndexDeclaration) {
            take();
            var index = readUnsignedNumber();
            return new Condition.RollIndex(index, throughPrevious(start));
        }
        Condition.Operator operator = switch (peek()) {
            case Token.Equals ignored -> Condition.Operator.EQUAL;
            case Token.NotEquals ignored -> Condition.Operator.NOT_EQUAL;
            case Token.LessThan ignored -> Condition.Operator.LESS_THAN;
            case Token.LessThanOrEqual ignored -> Condition.Operator.LESS_THAN_OR_EQUAL;
            case Token.GreaterThan ignored -> Condition.Operator.GREATER_THAN;
            case Token.GreaterThanOrEqual ignored -> Condition.Operator.GREATER_THAN_OR_EQUAL;
            default -> null;
        };
        if (operator != null) {
            take();
            var value = readSignedNumber();
            return new Condition.Comparison(operator, value, throughPrevious(start));
        }
        var first = readSignedNumber();
        if (peek() instanceof Token.RangeDeclaration) {
            take();
            var second = readSignedNumber();
            return new Condition.Range(first, second, throughPrevious(start));
        }
        return new Condition.Comparison(Condition.Operator.EQUAL, first, throughPrevious(start));
    }

    private Modifier.Targeted parseTargeted() {
        var start = take().range();
        boolean all = peek() instanceof Token.Asterisk;
        var indices = new ArrayList<Node.Number>();
        List<Condition> conditions = List.of();
        if (all) {
            take();
        } else if (peek() instanceof Token.OpenBracket) {
            conditions = parseConditions();
        } else {
            indices.add(readUnsignedNumber());
            while (peek() instanceof Token.Comma) {
                take();
                indices.add(readUnsignedNumber());
            }
        }
        require(Token.RangeDeclaration.class, "Expected ':' after targeted roll selection.");
        var adjustments = new ArrayList<Modifier.Adjustment>();
        while (true) {
            var operation = peek();
            var operator = binaryOperator(operation);
            if (operator == null) {
                throw fail("Expected '+', '-', '*', '/', or '^' in a targeted modifier.");
            }
            take();
            var operand = readSignedNumber();
            adjustments.add(new Modifier.Adjustment(operator, operand, throughPrevious(operation.range())));
            if (!(peek() instanceof Token.RangeDeclaration)) {
                break;
            }
            take();
        }
        return new Modifier.Targeted(all, indices, conditions, adjustments, throughPrevious(start));
    }

    private Node.Number readSignedNumber() {
        var start = peek().range();
        String sign = "";
        if (peek() instanceof Token.Plus || peek() instanceof Token.Minus) {
            sign = take() instanceof Token.Minus ? "-" : "+";
        }
        var number = readUnsignedNumber();
        return new Node.Number(sign + number.value(), SpanData.merge(start, number.range()));
    }

    private Node.Number readUnsignedNumber() {
        var number = require(Token.Number.class, "Expected a number.");
        return new Node.Number(number.value(), number.range());
    }

    private static Node.Number implicitOne(SpanData at) {
        return new Node.Number("1", new SpanData(at.cursorStart(), at.cursorStart(), at.originalString()));
    }

    private static Node.BinaryOperator binaryOperator(Token token) {
        return switch (token) {
            case Token.Plus ignored -> Node.BinaryOperator.ADD;
            case Token.Minus ignored -> Node.BinaryOperator.SUBTRACT;
            case Token.Asterisk ignored -> Node.BinaryOperator.MULTIPLY;
            case Token.Slash ignored -> Node.BinaryOperator.DIVIDE;
            case Token.Caret ignored -> Node.BinaryOperator.POWER;
            default -> null;
        };
    }

    private static boolean startsExpression(Token token) {
        return token instanceof Token.Number || token instanceof Token.DiceDeclaration
                || token instanceof Token.OpenParenthesis;
    }

    private boolean hasGapBeforeCurrent() {
        return index > 0 && peek().range().cursorStart() > tokens.get(index - 1).range().cursorEnd();
    }

    private SpanData throughPrevious(SpanData start) {
        return SpanData.merge(start, tokens.get(index - 1).range());
    }

    private Token peek() {
        Token token = tokens.get(index);
        if (token instanceof Token.Error error) {
            throw new ParseFailure(error.message(), error.range());
        }
        return token;
    }

    private Token take() {
        Token token = peek();
        index++;
        return token;
    }

    private <T extends Token> T require(Class<T> type, String message) {
        if (!type.isInstance(peek())) {
            throw fail(message);
        }
        return type.cast(take());
    }

    private ParseFailure fail(String message) {
        return new ParseFailure(message, peek().range());
    }

    private static final class ParseFailure extends RuntimeException {
        private final SpanData location;

        private ParseFailure(String message, SpanData location) {
            super(message);
            this.location = location;
        }
    }
}
