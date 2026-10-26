package dev.infernity.rollplayer.rollplayerlib4.parsing;

import dev.infernity.rollplayer.rollplayerlib4.Keywords;
import dev.infernity.rollplayer.rollplayerlib4.SpanData;

import java.util.List;
import java.util.Optional;

public sealed interface Modifier {
    SpanData range();

    /** Empty EXPLODE conditions select the die's upper bound. */
    record Conditional(Keywords keyword, List<Condition> conditions,
                       Optional<Node.Number> limit, SpanData range) implements Modifier {
        public Conditional {
            conditions = List.copyOf(conditions);
        }
    }

    record Ranked(Keywords keyword, Node.Number count, SpanData range) implements Modifier {}

    /**
     * Indices are one-based. Selection is fixed before the adjustment chain.
     */
    record Targeted(boolean all, List<Node.Number> indices, List<Condition> conditions,
                    List<Adjustment> adjustments, SpanData range) implements Modifier {
        public Targeted {
            indices = List.copyOf(indices);
            conditions = List.copyOf(conditions);
            adjustments = List.copyOf(adjustments);
        }

        public Targeted(boolean all, List<Node.Number> indices, List<Adjustment> adjustments, SpanData range) {
            this(all, indices, List.of(), adjustments, range);
        }
    }

    /** Targeted adjustments execute in list order, rather than arithmetic precedence. */
    record Adjustment(Node.BinaryOperator operator, Node.Number operand, SpanData range) {}
}
