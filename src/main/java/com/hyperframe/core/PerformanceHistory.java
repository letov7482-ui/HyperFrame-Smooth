package com.hyperframe.core;

import java.util.Arrays;

public final class PerformanceHistory {

    private static final int HISTORY_SIZE = 120;

    private final PerformanceEvent[] events =
            new PerformanceEvent[HISTORY_SIZE];

    private int size;
    private int index;

    public void record(PerformanceEvent event) {
        if (event == null) {
            return;
        }

        events[index] = event;

        index = (index + 1) % HISTORY_SIZE;

        if (size < HISTORY_SIZE) {
            size++;
        }
    }

    public PerformanceEvent getLatest() {
        if (size == 0) {
            return null;
        }

        int latestIndex =
                (index - 1 + HISTORY_SIZE)
                        % HISTORY_SIZE;

        return events[latestIndex];
    }

    public PerformanceEvent[] getEvents() {
        PerformanceEvent[] result =
                new PerformanceEvent[size];

        for (int i = 0; i < size; i++) {
            int eventIndex =
                    (index - size + i + HISTORY_SIZE)
                            % HISTORY_SIZE;

            result[i] = events[eventIndex];
        }

        return result;
    }

    public int getSize() {
        return size;
    }

    public int getSevereEventCount() {
        int count = 0;

        for (PerformanceEvent event : getEvents()) {
            if (event.isSevere()) {
                count++;
            }
        }

        return count;
    }

    public int getMicrostutterCount() {
        int count = 0;

        for (PerformanceEvent event : getEvents()) {
            if (event.isMicrostutter()) {
                count++;
            }
        }

        return count;
    }

    public double getWorstFrameTimeMs() {
        double worst = 0.0;

        for (PerformanceEvent event : getEvents()) {
            worst = Math.max(
                    worst,
                    event.frameTimeMs()
            );
        }

        return worst;
    }

    public void reset() {
        Arrays.fill(events, null);

        size = 0;
        index = 0;
    }
}
