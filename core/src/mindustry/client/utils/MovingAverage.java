package mindustry.client.utils;

import arc.struct.*;

/**
 * Like {@link arc.math.WindowedMean} but it still gives values even if it
 * isn't full yet.
 */
public class MovingAverage {
    private final int size;
    private final FloatSeq values;

    public MovingAverage(int size) {
        this.size = size;
        values = new FloatSeq(size + 1);
    }

    public void add(float value) {
        values.insert(0, value);
        if (values.size >= size) {
            values.pop();
        }
    }

    public float getAverage() {
        if (values.size == 0) {
            return 0f;
        }
        return values.sum() / values.size;
    }
}
