package mindustry.client.utils

class CustomWindowedMean(private val capacity: Int){
    private val data = mutableListOf(0f)

    fun addValue(dataPoint: Float){
        data.add(dataPoint)
        if(data.size >= capacity){
            data.removeAt(0)
        }
    }

    fun getAverage(): Float{
        return data.average().toFloat()
    }
}
