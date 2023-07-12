package mindustry.client.utils

import arc.util.*;

//Some code sourced from Arc Ratekeeper
class IndexedRatekeeper<K> {
	private val rateMap = mutableMapOf<K, Pair<Int, Long>>();
	private var globalOccurences: Int = 0;
	private var globalLastTime: Long = 0;

	fun allowGlobal(spacing:Long, cap:Int):Boolean {
		if(Time.timeSinceMillis(globalLastTime) > spacing){
			globalOccurences = 0
			globalLastTime = Time.millis()
		}

		globalOccurences ++
		return globalOccurences <= cap
	}

	fun allow(key:K, spacing:Long, cap:Int, globalSpacing:Long, globalCap:Int):Boolean {
		if(allowGlobal(globalSpacing, globalCap)){
			if(allow(key, spacing, cap)){
				return true
			} else {
				globalOccurences --
			}
		}
		return false
	}
		
	/**
	* @return whether an action is allowed.
	* @param key the key to check the ratelimit for
	* @param spacing the spacing between action chunks in milliseconds
	* @param cap the maximum amount of actions per chunk
	* */
	fun allow(key:K, spacing:Long, cap:Int):Boolean {
		var (occurences, lastTime) = rateMap.getOrPut(
			key, {Pair(0, 0)}
		)

		if(Time.timeSinceMillis(lastTime) > spacing){
			occurences = 0
			lastTime = Time.millis()
		}
		occurences ++

		rateMap.put(key, Pair(occurences, lastTime))

		return occurences <= cap
	}
	
}

