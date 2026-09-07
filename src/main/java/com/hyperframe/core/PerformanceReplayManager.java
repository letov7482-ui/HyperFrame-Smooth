package com.hyperframe.core;

/**
 * Manages the lifecycle of performance replays.
 *
 * A replay is not considered complete immediately when
 * a spike is detected. The manager waits for additional
 * frames so the Time Machine can inspect what happened
 * after the spike.
 */
public final class PerformanceReplayManager {

    private static final int DEFAULT_FRAMES_BEFORE = 60;
    private static final int DEFAULT_FRAMES_AFTER = 60;

    private final PerformanceReplay replayStore =
            new PerformanceReplay();

    private PendingReplay pendingReplay;

    /**
     * Starts collecting a replay around a detected event.
     */
    public void start(
            PerformanceEvent event,
            PerformanceTimeline timeline
    ) {
        start(
                event,
                timeline,
                DEFAULT_FRAMES_BEFORE,
                DEFAULT_FRAMES_AFTER
        );
    }

    /**
     * Starts a replay with a custom amount of
     * frames before and after the event.
     */
    public void start(
            PerformanceEvent event,
            PerformanceTimeline timeline,
            int framesBefore,
            int framesAfter
    ) {
        if (event == null || timeline == null) {
            return;
        }

        /*
         * Do not replace an active replay.
         *
         * The first event gets priority while we wait
         * for its post-event frames.
         */
        if (pendingReplay != null) {
            return;
        }

        pendingReplay =
                new PendingReplay(
                        event,
                        Math.max(0, framesBefore),
                        Math.max(0, framesAfter)
                );
    }

    /**
     * Called once per render frame.
     *
     * When enough frames after the event have become
     * available, the replay is finalized.
     */
    public PerformanceReplay.Replay update(
            PerformanceTimeline timeline
    ) {
        if (pendingReplay == null
                || timeline == null) {
            return null;
        }

        FrameSample latest =
                timeline.getLatest();

        if (latest == null) {
            return null;
        }

        long targetFrame =
                pendingReplay.event().frameNumber()
                        + pendingReplay.framesAfter();

        /*
         * We need enough future frames before
         * completing the replay.
         */
        if (latest.frameNumber() < targetFrame) {
            return null;
        }

        PerformanceReplay.Replay completed =
                replayStore.capture(
                        pendingReplay.event(),
                        timeline,
                        pendingReplay.framesBefore(),
                        pendingReplay.framesAfter()
                );

        pendingReplay = null;

        return completed;
    }

    /**
     * Returns whether a replay is currently
     * being collected.
     */
    public boolean isRecording() {
        return pendingReplay != null;
    }

    /**
     * Returns the event currently being collected.
     */
    public PerformanceEvent getPendingEvent() {
        if (pendingReplay == null) {
            return null;
        }

        return pendingReplay.event();
    }

    /**
     * Returns how many future frames are still required.
     */
    public int getRemainingFrames(
            PerformanceTimeline timeline
    ) {
        if (pendingReplay == null
                || timeline == null) {
            return 0;
        }

        FrameSample latest =
                timeline.getLatest();

        if (latest == null) {
            return pendingReplay.framesAfter();
        }

        long targetFrame =
                pendingReplay.event().frameNumber()
                        + pendingReplay.framesAfter();

        long remaining =
                targetFrame
                        - latest.frameNumber();

        return (int) Math.max(
                0,
                remaining
        );
    }

    /**
     * Returns replay collection progress
     * from 0.0 to 1.0.
     */
    public double getProgress(
            PerformanceTimeline timeline
    ) {
        if (pendingReplay == null) {
            return 1.0;
        }

        int total =
                pendingReplay.framesAfter();

        if (total <= 0) {
            return 1.0;
        }

        int remaining =
                getRemainingFrames(timeline);

        return Math.max(
                0.0,
                Math.min(
                        1.0,
                        1.0 - ((double) remaining / total)
                )
        );
    }

    /**
     * Returns the completed replay history.
     */
    public PerformanceReplay getReplayStore() {
        return replayStore;
    }

    /**
     * Returns the latest completed replay.
     */
    public PerformanceReplay.Replay getLatestReplay() {
        return replayStore.getLatest();
    }

    /**
     * Cancels the currently pending replay.
     */
    public void cancel() {
        pendingReplay = null;
    }

    /**
     * Clears both pending and completed replay data.
     */
    public void reset() {
        pendingReplay = null;
        replayStore.reset();
    }

    /**
     * Internal state of a replay that is still
     * collecting post-event frames.
     */
    private record PendingReplay(
            PerformanceEvent event,
            int framesBefore,
            int framesAfter
    ) {
    }
}
