package com.hyperframe.core;

import java.util.Arrays;

/**
 * Stores a replayable performance event.
 *
 * A replay contains the frame history around a detected
 * performance problem. It does not record video.
 *
 * It stores only lightweight performance data.
 */
public final class PerformanceReplay {

    private static final int MAX_REPLAYS = 32;

    private static final int DEFAULT_FRAMES_BEFORE = 60;
    private static final int DEFAULT_FRAMES_AFTER = 60;

    private final Replay[] replays =
            new Replay[MAX_REPLAYS];

    private int size;
    private int index;

    /**
     * Creates a replay from the current performance timeline.
     *
     * The current event becomes the center of the replay.
     */
    public Replay capture(
            PerformanceEvent event,
            PerformanceTimeline timeline
    ) {
        return capture(
                event,
                timeline,
                DEFAULT_FRAMES_BEFORE,
                DEFAULT_FRAMES_AFTER
        );
    }

    /**
     * Creates a replay with a custom amount of
     * frames before and after the event.
     */
    public Replay capture(
            PerformanceEvent event,
            PerformanceTimeline timeline,
            int framesBefore,
            int framesAfter
    ) {
        if (event == null || timeline == null) {
            return null;
        }

        int safeBefore =
                Math.max(0, framesBefore);

        int safeAfter =
                Math.max(0, framesAfter);

        /*
         * At the moment of capture we can only have
         * frames before the event.
         *
         * Future frames will be attached later by
         * completeReplay().
         */
        FrameSample[] surrounding =
                timeline.getFramesAround(
                        event.frameNumber(),
                        safeBefore,
                        safeAfter
                );

        Replay replay =
                new Replay(
                        event,
                        surrounding,
                        safeBefore,
                        safeAfter
                );

        store(replay);

        return replay;
    }

    /**
     * Stores a replay in the ring buffer.
     */
    private void store(Replay replay) {
        replays[index] = replay;

        index =
                (index + 1) % MAX_REPLAYS;

        if (size < MAX_REPLAYS) {
            size++;
        }
    }

    /**
     * Returns the latest replay.
     */
    public Replay getLatest() {
        if (size == 0) {
            return null;
        }

        int latestIndex =
                (index - 1 + MAX_REPLAYS)
                        % MAX_REPLAYS;

        return replays[latestIndex];
    }

    /**
     * Returns all stored replays chronologically.
     */
    public Replay[] getReplays() {
        Replay[] result =
                new Replay[size];

        for (int i = 0; i < size; i++) {
            int replayIndex =
                    (index - size + i + MAX_REPLAYS)
                            % MAX_REPLAYS;

            result[i] = replays[replayIndex];
        }

        return result;
    }

    /**
     * Returns a replay for a specific frame number.
     *
     * If no exact match exists, the nearest replay
     * is returned.
     */
    public Replay findNearest(
            long frameNumber
    ) {
        Replay[] available =
                getReplays();

        if (available.length == 0) {
            return null;
        }

        Replay nearest =
                available[0];

        long nearestDistance =
                Math.abs(
                        nearest.event().frameNumber()
                                - frameNumber
                );

        for (int i = 1; i < available.length; i++) {
            Replay candidate =
                    available[i];

            long distance =
                    Math.abs(
                            candidate.event().frameNumber()
                                    - frameNumber
                    );

            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }

        return nearest;
    }

    /**
     * Returns the number of stored replays.
     */
    public int getSize() {
        return size;
    }

    /**
     * Returns the maximum replay history size.
     */
    public int getCapacity() {
        return MAX_REPLAYS;
    }

    /**
     * Clears every stored replay.
     */
    public void reset() {
        Arrays.fill(replays, null);

        size = 0;
        index = 0;
    }

    /**
     * A replay of a performance event.
     */
    public record Replay(
            PerformanceEvent event,
            FrameSample[] frames,
            int framesBefore,
            int framesAfter
    ) {

        public Replay {
            frames =
                    frames == null
                            ? new FrameSample[0]
                            : frames.clone();
        }

        /**
         * Returns the frame at the center of the replay.
         */
        public FrameSample getCenterFrame() {
            long eventFrame =
                    event.frameNumber();

            for (FrameSample frame : frames) {
                if (frame.frameNumber()
                        == eventFrame) {
                    return frame;
                }
            }

            return null;
        }

        /**
         * Returns the worst frame in the replay.
         */
        public FrameSample getWorstFrame() {
            if (frames.length == 0) {
                return null;
            }

            FrameSample worst =
                    frames[0];

            for (int i = 1; i < frames.length; i++) {
                FrameSample frame =
                        frames[i];

                if (frame.frameTimeMs()
                        > worst.frameTimeMs()) {
                    worst = frame;
                }
            }

            return worst;
        }

        /**
         * Returns the average frame time
         * inside this replay.
         */
        public double getAverageFrameTimeMs() {
            if (frames.length == 0) {
                return 0.0;
            }

            double total = 0.0;

            for (FrameSample frame : frames) {
                total += frame.frameTimeMs();
            }

            return total / frames.length;
        }

        /**
         * Returns the frame time immediately before
         * the event, if available.
         */
        public FrameSample getPreviousFrame() {
            FrameSample previous = null;

            for (FrameSample frame : frames) {
                if (frame.frameNumber()
                        < event.frameNumber()) {
                    previous = frame;
                }
            }

            return previous;
        }

        /**
         * Returns the first frame after the event.
         */
        public FrameSample getNextFrame() {
            for (FrameSample frame : frames) {
                if (frame.frameNumber()
                        > event.frameNumber()) {
                    return frame;
                }
            }

            return null;
        }

        /**
         * Returns the number of frames
         * currently available in the replay.
         */
        public int getFrameCount() {
            return frames.length;
        }

        /**
         * Returns whether the replay contains
         * the actual event frame.
         */
        public boolean containsEventFrame() {
            return getCenterFrame() != null;
        }

        /**
         * Returns how many milliseconds the event
         * exceeded its baseline.
         */
        public double excessFrameTimeMs() {
            return Math.max(
                    0.0,
                    event.frameTimeMs()
                            - event.baselineMs()
            );
        }

        /**
         * Returns the event's relative cost.
         */
        public double relativeCost() {
            return event.relativeCost();
        }

        /**
         * Returns the event type.
         */
        public SpikeDetector.SpikeType type() {
            return event.type();
        }
     }
  }
