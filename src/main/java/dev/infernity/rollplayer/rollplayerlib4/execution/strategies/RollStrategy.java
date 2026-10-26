package dev.infernity.rollplayer.rollplayerlib4.execution.strategies;

import dev.infernity.rollplayer.rollplayerlib4.execution.Rolls;
import dev.infernity.rollplayer.rollplayerlib4.parsing.Node;

import java.util.random.RandomGenerator;

public interface RollStrategy<T> {
    boolean supportsRandom();
    void withRandom(RandomGenerator generator);

    T number(String literal);

    T unary(Node.UnaryOperator operator, T operand);

    T binary(Node.BinaryOperator operator, T left, T right);

    Rolls<T> roll(Node.Dice dice);
}
