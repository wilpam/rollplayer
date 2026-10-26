package dev.infernity.rollplayer.rollplayerlib4.execution.strategies.numeric;

import java.util.random.RandomGenerator;
import java.math.BigInteger;

/** Always rolls the highest value. */
public class BestPossibleStrategy implements SimpleRollingStrategy {
    @Override
    public BigInteger rollFace(BigInteger lowerBound, BigInteger upperBound) {
        return upperBound;
    }

    @Override
    public boolean supportsRandom() {
        return false;
    }

    @Override
    public void withRandom(RandomGenerator generator) {}
}
