package com.hyperframe.core;

import java.util.Arrays;

/**
 * Stores recent HyperFrame lag events.
 *
 * The history uses a ring buffer so old events are
 * automatically removed when the buffer becomes full.
 */
public final class LagEventHistory {

    private static final int HISTORY_SIZE = 64;

    private final LagEvent[] events =
            new LagEvent[HISTORY_SIZE];

    private int size;
    private int index;

    /**
     * Adds a new lag event to the history.
     */
    public void record(LagEvent event) {
        if (event == null) {
            return;
        }

        events[index] = event;

        index =
                (index + 1) % HISTORY_SIZE;

        if (size < HISTORY_SIZE) {
            size++;
        }
    }

    /**
     * Returns the newest lag event.
     */
    public LagEvent getLatest() {
        if (size == 0) {
            return null;
        }

        int latestIndex =
                (index - 1 + HISTORY_SIZE)
                        % HISTORY_SIZE;

        return events[latestIndex];
    }

    /**
     * Returns all stored events in chronological order.
     */
    public LagEvent[] getEvents() {
        LagEvent[] result =
                new LagEvent[size];

        for (int i = 0; i < size; i++) {
            int eventIndex =
                    (index - size + i + HISTORY_SIZE)
                            % HISTORY_SIZE;

            result[i] = events[eventIndex];
        }

        return result;
    }

    /**
     * Finds an event by its unique ID.
     */
    public LagEvent findById(long id) {
        for (LagEvent event : getEvents()) {
            if (event.id() == id) {
                return event;
            }
        }

        return null;
    }

    /**
     * Finds the event closest to a frame number.
     */
    public LagEvent findNearestFrame(
            long frameNumber
    ) {
        LagEvent[] available =
                getEvents();

        if (available.length == 0) {
            return null;
        }

        LagEvent nearest =
                available[0];

        long nearestDistance =
                Math.abs(
                        nearest.frameNumber()
                                - frameNumber
                );

        for (int i = 1; i < available.length; i++) {
            LagEvent candidate =
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
     * Returns the number of stored events.
     */
    public int getSize() {
        return size;
    }

    /**
     * Returns the maximum history size.
     */
    public int getCapacity() {
        return HISTORY_SIZE;
    }

    /**
     * Returns whether the history is empty.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns whether the history is full.
     */
    public boolean isFull() {
        return size >= HISTORY_SIZE;
    }

    /**
     * Counts severe events.
     */
    public int getSevereCount() {
        int count = 0;

        for (LagEvent event : getEvents()) {
            if (event.isSevere()) {
                count++;
            }
        }

        return count;
    }

    /**
     * Counts microstutter events.
     */
    public int getMicrostutterCount() {
        int count = 0;

        for (LagEvent event : getEvents()) {
            if (event.isMicrostutter()) {
                count++;
            }
        }

        return count;
    }

    /**
     * Returns the worst recorded frame time.
     */
    public double getWorstFrameTimeMs() {
        double worst = 0.0;

        for (LagEvent event : getEvents()) {
            worst = Math.max(
                    worst,
                    event.frameTimeMs()
            );
        }

        return worst;
    }

    /**
     * Returns the event with the worst frame time.
     */
    public LagEvent getWorstEvent() {
        LagEvent worst = null;

        for (LagEvent event : getEvents()) {
            if (worst == null
                    || event.frameTimeMs()
                    > worst.frameTimeMs()) {
                worst = event;
            }
        }

        return worst;
    }

    /**
     * Clears the complete history.
     */
    public void reset() {
        Arrays.fill(events, null);

        size = 0;
        index = 0;
    }
}
