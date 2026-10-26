package dev.infernity.rollplayer.rollplayerlib4.execution.strategies.numeric;

import java.util.random.RandomGenerator;
import java.util.Objects;
import java.math.BigInteger;

/** The bit that actually does the randomness. */
public class RandomStrategy implements SimpleRollingStrategy {
    private RandomGenerator generator;

    @Override
    public boolean supportsRandom() {
        return true;
    }

    @Override
    public void withRandom(RandomGenerator generator) {
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    @Override
    public BigInteger rollFace(BigInteger lowerBound, BigInteger upperBound) {
        if (generator == null) {
            throw new IllegalStateException("RandomStrategy needs a random generator before execution.");
        }
        if (lowerBound.compareTo(upperBound) > 0) {
            throw new IllegalArgumentException("Die lower bound must not exceed its upper bound.");
        }
        // If we can keep it simple, do so
        if (lowerBound.bitLength() < 64 && upperBound.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) < 0) {
            return BigInteger.valueOf(generator.nextLong(lowerBound.longValueExact(), upperBound.longValueExact() + 1));
        }
        // Otherwise, we need to use BigIntegers so we can roll all possible sides.
        BigInteger width = upperBound.subtract(lowerBound).add(BigInteger.ONE);
        int bits = width.subtract(BigInteger.ONE).bitLength();
        if (bits == 0) return lowerBound;
        byte[] bytes = new byte[(bits - 1) / 8 + 1];
        int excessBits = bytes.length * 8 - bits;
        BigInteger offset;
        do {
            generator.nextBytes(bytes);
            bytes[0] &= (byte) (0xff >>> excessBits);
            offset = new BigInteger(1, bytes);
        } while (offset.compareTo(width) >= 0);
        return lowerBound.add(offset);
    }
}
