package dev.infernity.rollplayer.rollplayerlib4;

public enum Markup {
    NONE,
    ITALIC,
    BOLD,
    BOLD_ITALIC;

    public boolean bold() {
        return this == BOLD || this == BOLD_ITALIC;
    }

    public boolean italic() {
        return this == ITALIC || this == BOLD_ITALIC;
    }

    public Markup combine(Markup other) {
        boolean bold = bold() || other.bold();
        boolean italic = italic() || other.italic();
        return bold ? (italic ? BOLD_ITALIC : BOLD) : (italic ? ITALIC : NONE);
    }

    public record Group<V>(Markup highlight, V value) {
        public Group<V> withValue(V value) {
            return new Group<>(highlight, value);
        }

        public Group<V> withMarkup(Markup markup) {
            return new Group<>(highlight.combine(markup), value);
        }
    }
}
