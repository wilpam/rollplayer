package dev.infernity.rollplayer.rollplayerlib4.parsing;

import dev.infernity.rollplayer.rollplayerlib4.SpanData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public sealed interface ParsingResult {
    record Success(List<Node> nodes) implements ParsingResult {
        public Success {
            nodes = List.copyOf(nodes);
        }

        /** All dice groups in source order, including those nested in arithmetic. */
        public List<Node.Dice> rolls() {
            var rolls = new ArrayList<Node.Dice>();
            for (Node node : nodes) {
                var pending = new ArrayDeque<Node>();
                pending.push(node);
                while (!pending.isEmpty()) {
                    switch (pending.pop()) {
                        case Node.Dice dice -> rolls.add(dice);
                        case Node.Binary binary -> {
                            pending.push(binary.right());
                            pending.push(binary.left());
                        }
                        case Node.Unary unary -> pending.push(unary.operand());
                        case Node.Group group -> pending.push(group.expression());
                        case Node.Number ignored -> {}
                    }
                }
            }
            return List.copyOf(rolls);
        }
    }

    record Error(String message, SpanData location) implements ParsingResult {}
}
