package mindustry.game;

import arc.struct.*;

/**
 * Modified WindowedMean that doesn't need to be full to get values
 * It's not actually better per say but I couldn't think of another name
 */

public final class BetterWindowedMean{
    FloatArray values;
    int addedValues = 0;
    int lastValue;
    float mean = 0;
    boolean dirty = true;

    /**
     * constructor, windowSize specifies the number of samples we will continuously get the mean and variance from. the class
     * will only return meaning full values if at least windowSize values have been added.
     * @param windowSize size of the sample window
     */
    public BetterWindowedMean(int windowSize){
        values = new FloatArray(true, windowSize);
    }

    /** @return whether the value returned will be meaningful */
    public boolean hasEnoughData(){
        return addedValues >= values.size;
    }

    /** clears this WindowedMean. The class will only return meaningful values after enough data has been added again. */
    public void clear(){
        addedValues = 0;
        lastValue = 0;
        for(int i = 0; i < values.size; i++)
            values.set(i, 0);
        dirty = true;
    }

    /**
     * adds a new sample to this mean. In case the window is full the oldest value will be replaced by this new value.
     * @param value The value to add
     */
    public void addValue(float value){
        if(addedValues < values.size) addedValues++;
        values.add(value);
//        values.set(lastValue++, value);
        if(lastValue > values.size - 1) lastValue = 0;
        dirty = true;
    }

    /**
     * returns the mean of the samples added to this instance. Only returns meaningful results when at least window_size samples
     * as specified in the constructor have been added.
     * @return the mean
     */
    public float getMean(){
        if(dirty){
            float mean = 0;
            for(int i = 0; i < values.size; i++)
                mean += values.get(i);

            this.mean = mean / values.size;
            dirty = false;
        }
        return this.mean;
    }

    /** @return the oldest value in the window */
    public float getOldest(){
        return addedValues < values.size ? values.get(0) : values.get(lastValue);
    }

    /** @return the value last added */
    public float getLatest(){
        return values.get(lastValue - 1 == -1 ? values.size - 1 : lastValue - 1);
    }

    /** @return The standard deviation */
    public float standardDeviation(){
        if(!hasEnoughData()) return 0;

        float mean = getMean();
        float sum = 0;
        for(int i = 0; i < values.size; i++){
            sum += (values.get(i) - mean) * (values.get(i) - mean);
        }

        return (float)Math.sqrt(sum / values.size);
    }

    public float getLowest(){
        float lowest = Float.MAX_VALUE;
        for(int i = 0; i < values.size; i++)
            lowest = Math.min(lowest, values.get(i));
        return lowest;
    }

    public float getHighest(){
        float lowest = Float.MIN_NORMAL;
        for(int i = 0; i < values.size; i++)
            lowest = Math.max(lowest, values.get(i));
        return lowest;
    }

    public int getValueCount(){
        return addedValues;
    }

    public int getWindowSize(){
        return values.size;
    }

    /**
     * @return A new <code>float[]</code> containing all values currently in the window of the stream, in order from oldest to
     * latest. The length of the array is smaller than the window size if not enough data has been added.
     */
    public float[] getWindowValues(){
        float[] windowValues = new float[addedValues];
        if(hasEnoughData()){
            for(int i = 0; i < windowValues.length; i++){
                windowValues[i] = values.get((i + lastValue) % values.size);
            }
        }else{
            System.arraycopy(values.toArray(), 0, windowValues, 0, addedValues);
        }
        return windowValues;
    }
}
