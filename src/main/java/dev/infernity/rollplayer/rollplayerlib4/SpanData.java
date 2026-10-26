package dev.infernity.rollplayer.rollplayerlib4;


import org.jetbrains.annotations.NotNull;

public record SpanData(int cursorStart, int cursorEnd, String originalString) {
    @Override
    public @NotNull String toString() {
        return cursorStart + ".." + cursorEnd;
    }

    public static SpanData merge(SpanData from, SpanData to) {
        return new SpanData(from.cursorStart(), to.cursorEnd(), to.originalString());
    }

    public DebugInfo debugInfo() {
        int row = 1;
        int lineStart = 0;
        for (int i = 0; i < cursorStart; i++) {
            if (originalString.charAt(i) == '\n') {
                row += 1;
                lineStart = i + 1;
            }
        }

        int lineEnd = originalString.indexOf('\n', cursorStart);
        if (lineEnd == -1) lineEnd = originalString.length();
        String linePreview = originalString.substring(lineStart, lineEnd);

        return new DebugInfo(linePreview, cursorStart - lineStart,
                Math.min(cursorEnd, lineEnd) - lineStart, row);
    }

    public record DebugInfo(
            String linePreview,
            int from,
            int to,
            int line
    ) {
        @Override
        public @NotNull String toString() {
            String prefix = linePreview.substring(0, from).replace("\t", "    ");
            String highlighted = linePreview.substring(from, to).replace("\t", "    ");
            return
                    linePreview.replace("\t", "    ") + "\n"
                    + " ".repeat(prefix.length()) + "^".repeat(Math.max(highlighted.length(), 1));
        }
    }
}
