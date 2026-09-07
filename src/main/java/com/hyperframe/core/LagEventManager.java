package com.hyperframe.core;

/**
 * Coordinates the creation of complete HyperFrame lag events.
 *
 * A lag event is finalized only after its performance replay
 * has collected enough frames around the detected spike.
 *
 * Flow:
 *
 * Spike detected
 *      ↓
 * Analysis created
 *      ↓
 * Replay starts
 *      ↓
 * Future frames collected
 *      ↓
 * Replay completed
 *      ↓
 * LagEvent created
 *      ↓
 * LagEventHistory
 */
public final class LagEventManager {

    private static final int DEFAULT_FRAMES_BEFORE = 60;
    private static final int DEFAULT_FRAMES_AFTER = 60;

    private final LagEventHistory history =
            new LagEventHistory();

    private final PerformanceReplayManager replayManager =
            new PerformanceReplayManager();

    private long nextEventId = 1L;

    /**
     * Starts tracking a new lag event.
     *
     * The event is not added to history yet.
     * It will only be stored after the replay is complete.
     */
    public void start(
            PerformanceEvent event,
            PerformanceAnalyzer.AnalysisResult analysis,
            PerformanceTimeline timeline
    ) {
        start(
                event,
                analysis,
                timeline,
                DEFAULT_FRAMES_BEFORE,
                DEFAULT_FRAMES_AFTER
        );
    }

    /**
     * Starts tracking a new lag event with custom
     * replay dimensions.
     */
    public void start(
            PerformanceEvent event,
            PerformanceAnalyzer.AnalysisResult analysis,
            PerformanceTimeline timeline,
            int framesBefore,
            int framesAfter
    ) {
        if (event == null
                || analysis == null
                || timeline == null) {
            return;
        }

        /*
         * Do not replace an already active event.
         *
         * The first detected event gets priority while
         * its replay is being collected.
         */
        if (replayManager.isRecording()) {
            return;
        }

        replayManager.start(
                event,
                timeline,
                Math.max(0, framesBefore),
                Math.max(0, framesAfter)
        );

        pendingAnalysis = analysis;
    }

    /**
     * Analysis belonging to the currently pending event.
     */
    private PerformanceAnalyzer.AnalysisResult pendingAnalysis;

    /**
     * Called every render frame.
     *
     * When the replay has enough future frames,
     * the complete LagEvent is created and stored.
     */
    public LagEvent update(
            PerformanceTimeline timeline
    ) {
        if (timeline == null) {
            return null;
        }

        PerformanceReplay.Replay replay =
                replayManager.update(timeline);

        if (replay == null) {
            return null;
        }

        PerformanceEvent event =
                replay.event();

        PerformanceAnalyzer.AnalysisResult analysis =
                pendingAnalysis;

        /*
         * Safety fallback.
         *
         * A replay should always have an analysis because
         * start() requires one, but we do not want a broken
         * state to crash the client.
         */
        if (analysis == null) {
            analysis =
                    new PerformanceAnalyzer.AnalysisResult(
                            PerformanceAnalyzer.Pattern.NONE,
                            "No analysis available",
                            0.0
                    );
        }

        LagEvent lagEvent =
                new LagEvent(
                        nextEventId++,
                        System.nanoTime(),
                        event,
                        analysis,
                        replay
                );

        history.record(lagEvent);

        pendingAnalysis = null;

        return lagEvent;
    }

    /**
     * Returns whether a lag event is currently
     * waiting for replay completion.
     */
    public boolean isRecording() {
        return replayManager.isRecording();
    }

    /**
     * Returns the currently pending performance event.
     */
    public PerformanceEvent getPendingEvent() {
        return replayManager.getPendingEvent();
    }

    /**
     * Returns the analysis of the pending event.
     */
    public PerformanceAnalyzer.AnalysisResult getPendingAnalysis() {
        return pendingAnalysis;
    }

    /**
     * Returns replay collection progress
     * from 0.0 to 1.0.
     */
    public double getProgress(
            PerformanceTimeline timeline
    ) {
        return replayManager.getProgress(
                timeline
        );
    }

    /**
     * Returns how many future frames are still required.
     */
    public int getRemainingFrames(
            PerformanceTimeline timeline
    ) {
        return replayManager.getRemainingFrames(
                timeline
        );
    }

    /**
     * Returns the complete lag-event history.
     */
    public LagEventHistory getHistory() {
        return history;
    }

    /**
     * Returns the latest complete lag event.
     */
    public LagEvent getLatest() {
        return history.getLatest();
    }

    /**
     * Finds a lag event by its unique ID.
     */
    public LagEvent findById(long id) {
        return history.findById(id);
    }

    /**
     * Finds the lag event nearest to a frame number.
     */
    public LagEvent findNearestFrame(
            long frameNumber
    ) {
        return history.findNearestFrame(
                frameNumber
        );
    }

    /**
     * Returns the number of stored lag events.
     */
    public int getEventCount() {
        return history.getSize();
    }

    /**
     * Returns the number of severe events.
     */
    public int getSevereCount() {
        return history.getSevereCount();
    }

    /**
     * Returns the number of microstutter events.
     */
    public int getMicrostutterCount() {
        return history.getMicrostutterCount();
    }

    /**
     * Returns the worst recorded lag event.
     */
    public LagEvent getWorstEvent() {
        return history.getWorstEvent();
    }

    /**
     * Cancels the currently pending event.
     */
    public void cancel() {
        replayManager.cancel();
        pendingAnalysis = null;
    }

    /**
     * Completely clears event and replay data.
     */
    public void reset() {
        replayManager.reset();
        history.reset();

        pendingAnalysis = null;
        nextEventId = 1L;
    }
}
