package dev.infernity.rollplayer.rollplayerlib4;

import dev.infernity.rollplayer.rollplayerlib4.hash.MurmurHash3;
import dev.infernity.rollplayer.rollplayerlib4.execution.ExecutionResult;
import dev.infernity.rollplayer.rollplayerlib4.execution.Executor;
import dev.infernity.rollplayer.rollplayerlib4.execution.strategies.RollStrategy;
import dev.infernity.rollplayer.rollplayerlib4.execution.strategies.numeric.RandomStrategy;
import dev.infernity.rollplayer.rollplayerlib4.lexer.Lexer;
import dev.infernity.rollplayer.rollplayerlib4.lexer.TokenizationResult;
import dev.infernity.rollplayer.rollplayerlib4.parsing.Parser;
import dev.infernity.rollplayer.rollplayerlib4.parsing.ParsingResult;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public final class RollplayerLib4 {
    private RollplayerLib4(){}

    @SuppressWarnings("SpellCheckingInspection")
    static final RandomGeneratorFactory<RandomGenerator> factory = RandomGeneratorFactory.of("Xoshiro256PlusPlus");

    public static ExecutionResult<Double> evaluate(String roll, String seed) {
        return evaluate(roll, factory.create(MurmurHash3.hash256(seed)));
    }

    public static ExecutionResult<Double> evaluate(String roll) {
        return evaluate(roll, factory.create());
    }

    public static ExecutionResult<Double> evaluate(String roll, RandomGenerator generator) {
        return evaluate(roll, generator, new RandomStrategy());
    }

    public static <T> ExecutionResult<T> evaluate(String roll, RollStrategy<T> strategy) {
        return evaluate(roll, factory.create(), strategy);
    }

    public static <T> ExecutionResult<T> evaluate(String roll, String seed, RollStrategy<T> strategy) {
        return evaluate(roll, factory.create(MurmurHash3.hash256(seed)), strategy);
    }

    public static <T> ExecutionResult<T> evaluate(String roll, RandomGenerator generator, RollStrategy<T> strategy) {
        var tokenization = Lexer.tokenize(roll);
        if (tokenization instanceof TokenizationResult.Error error) {
            return new ExecutionResult.Error<>(error.message(), error.location());
        }
        var tokens = ((TokenizationResult.Success) tokenization).tokens();
        var parsing = Parser.parse(tokens);
        if (parsing instanceof ParsingResult.Error error) {
            return new ExecutionResult.Error<>(error.message(), error.location());
        }
        var nodes = ((ParsingResult.Success) parsing).nodes();
        return new Executor<T>(nodes, generator).executeDetailed(strategy);
    }
}
