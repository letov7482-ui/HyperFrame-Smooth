package com.hyperframe.core;

import java.util.Arrays;

/**
 * Stores a lightweight timeline of recent frame performance.
 *
 * This is the foundation of HyperFrame's
 * "Performance Time Machine".
 *
 * The timeline keeps recent frame samples in chronological order
 * and allows us to inspect what happened before and after
 * a performance event.
 */
public final class PerformanceTimeline {

    private static final int HISTORY_SIZE = 1200;

    private final FrameSample[] frames =
            new FrameSample[HISTORY_SIZE];

    private int size;
    private int index;

    /**
     * Adds a frame to the timeline.
     */
    public void record(FrameSample sample) {
        if (sample == null) {
            return;
        }

        frames[index] = sample;

        index = (index + 1) % HISTORY_SIZE;

        if (size < HISTORY_SIZE) {
            size++;
        }
    }

    /**
     * Returns the newest frame in the timeline.
     */
    public FrameSample getLatest() {
        if (size == 0) {
            return null;
        }

        int latestIndex =
                (index - 1 + HISTORY_SIZE)
                        % HISTORY_SIZE;

        return frames[latestIndex];
    }

    /**
     * Returns all stored frames in chronological order.
     */
    public FrameSample[] getFrames() {
        FrameSample[] result =
                new FrameSample[size];

        for (int i = 0; i < size; i++) {
            int frameIndex =
                    (index - size + i + HISTORY_SIZE)
                            % HISTORY_SIZE;

            result[i] = frames[frameIndex];
        }

        return result;
    }

    /**
     * Returns frames around a specific frame number.
     *
     * Example:
     *
     * frameNumber = 500
     * before = 30
     * after = 30
     *
     * Result:
     * frames 470 -> 530
     *
     * If some frames are not available because they
     * have already left the ring buffer, only the
     * available frames are returned.
     */
    public FrameSample[] getFramesAround(
            long frameNumber,
            int before,
            int after
    ) {
        if (size == 0) {
            return new FrameSample[0];
        }

        int safeBefore =
                Math.max(0, before);

        int safeAfter =
                Math.max(0, after);

        long start =
                frameNumber - safeBefore;

        long end =
                frameNumber + safeAfter;

        FrameSample[] available =
                getFrames();

        int count = 0;

        for (FrameSample frame : available) {
            if (frame.frameNumber() >= start
                    && frame.frameNumber() <= end) {
                count++;
            }
        }

        FrameSample[] result =
                new FrameSample[count];

        int resultIndex = 0;

        for (FrameSample frame : available) {
            if (frame.frameNumber() >= start
                    && frame.frameNumber() <= end) {

                result[resultIndex++] = frame;
            }
        }

        return result;
    }

    /**
     * Finds the frame with the closest frame number
     * to the requested position.
     */
    public FrameSample findNearest(
            long frameNumber
    ) {
        FrameSample[] available =
                getFrames();

        if (available.length == 0) {
            return null;
        }

        FrameSample nearest =
                available[0];

        long nearestDistance =
                Math.abs(
                        nearest.frameNumber()
                                - frameNumber
                );

        for (int i = 1; i < available.length; i++) {
            FrameSample candidate =
                    available[i];

            long distance =
                    Math.abs(
                            candidate.frameNumber()
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
     * Finds the most expensive frame inside
     * a specified range.
     */
    public FrameSample findWorst(
            long startFrame,
            long endFrame
    ) {
        FrameSample worst = null;

        for (FrameSample frame : getFrames()) {
            if (frame.frameNumber() < startFrame
                    || frame.frameNumber() > endFrame) {
                continue;
            }

            if (worst == null
                    || frame.frameTimeMs()
                    > worst.frameTimeMs()) {
                worst = frame;
            }
        }

        return worst;
    }

    /**
     * Returns the number of stored frames.
     */
    public int getSize() {
        return size;
    }

    /**
     * Returns the maximum number of frames
     * this timeline can hold.
     */
    public int getCapacity() {
        return HISTORY_SIZE;
    }

    /**
     * Returns whether the timeline is full.
     */
    public boolean isFull() {
        return size >= HISTORY_SIZE;
    }

    /**
     * Clears the timeline.
     */
    public void reset() {
        Arrays.fill(frames, null);

        size = 0;
        index = 0;
    }
}
