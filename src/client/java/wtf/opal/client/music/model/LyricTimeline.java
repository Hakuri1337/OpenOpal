package wtf.opal.client.music.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricTimeline {
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final LyricTimeline EMPTY = new LyricTimeline(List.of());

    private final List<Line> lines;

    private LyricTimeline(final List<Line> lines) {
        this.lines = lines;
    }

    public static LyricTimeline empty() {
        return EMPTY;
    }

    public static LyricTimeline parse(final LyricDocument document) {
        if (document == null) return EMPTY;
        final Map<Long, String> translated = parseLines(document.translated());
        final Map<Long, String> original = parseLines(document.original());
        if (original.isEmpty()) return EMPTY;

        final List<Line> lines = new ArrayList<>();
        original.forEach((time, text) -> {
            if (!text.isBlank()) lines.add(new Line(time, text, translated.getOrDefault(time, "")));
        });
        lines.sort(Comparator.comparingLong(Line::timeMillis));
        return lines.isEmpty() ? EMPTY : new LyricTimeline(List.copyOf(lines));
    }

    public Line lineAt(final long positionMillis) {
        if (lines.isEmpty()) return null;
        int low = 0;
        int high = lines.size() - 1;
        int result = -1;
        while (low <= high) {
            final int middle = (low + high) >>> 1;
            if (lines.get(middle).timeMillis <= positionMillis) {
                result = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return result < 0 ? null : lines.get(result);
    }

    private static Map<Long, String> parseLines(final String raw) {
        final Map<Long, String> result = new HashMap<>();
        if (raw == null || raw.isBlank()) return result;
        for (final String line : raw.split("\\R")) {
            final Matcher matcher = TIMESTAMP.matcher(line);
            final List<Long> times = new ArrayList<>();
            int textStart = 0;
            while (matcher.find()) {
                final long minutes = Long.parseLong(matcher.group(1));
                final long seconds = Long.parseLong(matcher.group(2));
                final String fraction = matcher.group(3);
                final long millis = fraction == null ? 0L
                        : fraction.length() == 1 ? Long.parseLong(fraction) * 100L
                        : fraction.length() == 2 ? Long.parseLong(fraction) * 10L
                        : Long.parseLong(fraction.substring(0, Math.min(3, fraction.length())));
                times.add((minutes * 60L + seconds) * 1000L + millis);
                textStart = matcher.end();
            }
            final String text = line.substring(Math.min(textStart, line.length())).trim();
            for (final long time : times) result.put(time, text);
        }
        return result;
    }

    public record Line(long timeMillis, String text, String translated) {
    }
}
