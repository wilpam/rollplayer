package dev.infernity.rollplayer.rollplayerlib4.execution.strategies.numeric;

import dev.infernity.rollplayer.rollplayerlib4.execution.strategies.RollStrategy;
import dev.infernity.rollplayer.rollplayerlib4.execution.Rolls;
import dev.infernity.rollplayer.rollplayerlib4.parsing.Node;

import java.math.BigInteger;

/**
 * Handle most of the "simple" parts of roll strategy.
 * <br />
 * This is in place in case we ever want to add an AnyDice-esque roll calculator, since that'll act VERY different.
 */

public interface SimpleRollingStrategy extends RollStrategy<Double> {
    BigInteger rollFace(BigInteger lowerBound, BigInteger upperBound);

    @Override
    default Double number(String literal) {
        return finite(Double.parseDouble(literal));
    }

    @Override
    default Double unary(Node.UnaryOperator operator, Double operand) {
        return finite(operator == Node.UnaryOperator.MINUS ? -operand : operand);
    }

    @Override
    default Double binary(Node.BinaryOperator operator, Double left, Double right) {
        return finite(switch (operator) {
            case ADD -> left + right;
            case SUBTRACT -> left - right;
            case MULTIPLY -> left * right;
            case DIVIDE -> {
                if (right == 0) throw new ArithmeticException("Division by zero.");
                yield left / right;
            }
            case POWER -> Math.pow(left, right);
        });
    }

    @Override
    default Rolls<Double> roll(Node.Dice dice) {
        return new NumericRoller(this, dice).evaluate();
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Numeric evaluation produced a non-finite value.");
        }
        return value;
    }
}
