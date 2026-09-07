package com.hyperframe.core;

import java.util.Arrays;

public final class PerformanceSnapshotHistory {

    private static final int HISTORY_SIZE = 120;

    private final PerformanceSnapshot[] snapshots =
            new PerformanceSnapshot[HISTORY_SIZE];

    private int size;
    private int index;

    public void record(PerformanceSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        snapshots[index] = snapshot;

        index = (index + 1) % HISTORY_SIZE;

        if (size < HISTORY_SIZE) {
            size++;
        }
    }

    public PerformanceSnapshot getLatest() {
        if (size == 0) {
            return null;
        }

        int latestIndex =
                (index - 1 + HISTORY_SIZE)
                        % HISTORY_SIZE;

        return snapshots[latestIndex];
    }

    public PerformanceSnapshot[] getSnapshots() {
        PerformanceSnapshot[] result =
                new PerformanceSnapshot[size];

        for (int i = 0; i < size; i++) {
            int snapshotIndex =
                    (index - size + i + HISTORY_SIZE)
                            % HISTORY_SIZE;

            result[i] = snapshots[snapshotIndex];
        }

        return result;
    }

    public int getSize() {
        return size;
    }

    public void reset() {
        Arrays.fill(snapshots, null);

        size = 0;
        index = 0;
    }
}
