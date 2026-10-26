package dev.infernity.rollplayer.rollplayerlib4.execution;

import dev.infernity.rollplayer.rollplayerlib4.Markup;
import dev.infernity.rollplayer.rollplayerlib4.parsing.Node;

import java.util.List;

public record Rolls<T>(Node.Dice source, T total,
                       List<Markup.Group<T>> original, List<Markup.Group<T>> values) {
    public Rolls {
        original = List.copyOf(original);
        values = List.copyOf(values);
    }
}
