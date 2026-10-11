package local.mcafk;

import java.util.List;

final class RecordedMacro {
    private final int id;
    private final List<MacroEvent> events;
    private int minSeconds;
    private int maxSeconds;
    private boolean returnView;
    private boolean queued = true;
    private String comment;

    RecordedMacro(int id, int minSeconds, int maxSeconds, List<MacroEvent> events) {
        this(id, minSeconds, maxSeconds, events, false, true, "");
    }

    RecordedMacro(
            int id, int minSeconds, int maxSeconds, List<MacroEvent> events,
            boolean returnView, boolean queued, String comment
    ) {
        this.id = id;
        this.minSeconds = minSeconds;
        this.maxSeconds = maxSeconds;
        this.events = List.copyOf(events);
        this.returnView = returnView;
        this.queued = queued;
        this.comment = comment;
    }

    int id() {
        return id;
    }

    int minSeconds() {
        return minSeconds;
    }

    int maxSeconds() {
        return maxSeconds;
    }

    List<MacroEvent> events() {
        return events;
    }

    boolean returnView() {
        return returnView;
    }

    boolean queued() {
        return queued;
    }

    String comment() {
        return comment;
    }

    void setInterval(int minSeconds, int maxSeconds) {
        this.minSeconds = minSeconds;
        this.maxSeconds = maxSeconds;
    }
}
