package mindustry.game

import kotlin.math.sqrt

/**
 * Modified WindowedMean that doesn't need to be full to get values
 * It's not actually better per say but I couldn't think of another name
 */
class BetterWindowedMean(windowSize: Int) {
    private var values: arc.struct.FloatArray = arc.struct.FloatArray(true, windowSize)
    private var valueCount = 0
    private var lastValue = 0
    private var mean = 0f
    private var dirty = true

    /** @return whether the value returned will be meaningful
     */
    private fun hasEnoughData(): Boolean {
        return valueCount >= values.size
    }

    /** clears this WindowedMean. The class will only return meaningful values after enough data has been added again.  */
    fun clear() {
        valueCount = 0
        lastValue = 0
        for (i in 0 until values.size) values[i] = 0f
        dirty = true
    }

    /**
     * adds a new sample to this mean. In case the window is full the oldest value will be replaced by this new value.
     * @param value The value to add
     */
    fun addValue(value: Float) {
        if (valueCount < values.size) valueCount++
        values.add(value)
        //        values.set(lastValue++, value);
        if (lastValue > values.size - 1) lastValue = 0
        dirty = true
    }

    /**
     * returns the mean of the samples added to this instance. Only returns meaningful results when at least window_size samples
     * as specified in the constructor have been added.
     * @return the mean
     */
    fun getMean(): Float {
        if (dirty) {
            var mean = 0f
            for (i in 0 until values.size) mean += values[i]
            this.mean = mean / values.size
            dirty = false
        }
        return mean
    }

    /** @return the oldest value in the window
     */
    val oldest: Float
        get() = if (valueCount < values.size) values[0] else values[lastValue]

    /** @return the value last added
     */
    val latest: Float
        get() = values[if (lastValue - 1 == -1) values.size - 1 else lastValue - 1]

    /** @return The standard deviation
     */
    fun standardDeviation(): Float {
        if (!hasEnoughData()) return 0f
        val mean = getMean()
        var sum = 0f
        for (i in 0 until values.size) {
            sum += (values[i] - mean) * (values[i] - mean)
        }
        return sqrt(sum / values.size.toDouble()).toFloat()
    }

    val lowest: Float
        get() {
            var lowest: Float = Float.MAX_VALUE
            for (i in 0 until values.size) lowest = lowest.coerceAtMost(values[i])
            return lowest
        }

    val highest: Float
        get() {
            var lowest = java.lang.Float.MIN_NORMAL
            for (i in 0 until values.size) lowest = lowest.coerceAtLeast(values[i])
            return lowest
        }

    val windowSize: Int
        get() = values.size

    /**
     * @return A new `float[]` containing all values currently in the window of the stream, in order from oldest to
     * latest. The length of the array is smaller than the window size if not enough data has been added.
     */
    val windowValues: FloatArray
        get() {
            val windowValues = FloatArray(valueCount)
            if (hasEnoughData()) {
                for (i in windowValues.indices) {
                    windowValues[i] = values[(i + lastValue) % values.size]
                }
            } else {
                System.arraycopy(values.toArray(), 0, windowValues, 0, valueCount)
            }
            return windowValues
        }

}