package dev.infernity.rollplayer.rollplayerlib4.execution;

import dev.infernity.rollplayer.rollplayerlib4.SpanData;

public final class EvaluationException extends RuntimeException {
    private final SpanData location;

    public EvaluationException(String message, SpanData location) {
        super(message);
        this.location = location;
    }

    public SpanData location() {
        return location;
    }
}
