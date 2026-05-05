package ir.buddy.mint.util;

@FunctionalInterface
public interface ScheduledTaskHandle {

    ScheduledTaskHandle NOOP = () -> { };

    void cancel();
}
