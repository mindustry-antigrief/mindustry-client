@file:Suppress("NAME_SHADOWING")

package mindustry.client.utils

import arc.math.*
import kotlin.math.*

object BiasedLevenshtein {
    @JvmOverloads @JvmStatic
    fun biasedLevenshtein(x: String, y: String, caseSensitive: Boolean = false, lengthIndependent: Boolean = false): Float {
        var x = x
        var y = y
        if (!caseSensitive) {
            x = x.lowercase()
            y = y.lowercase()
        }
        if (lengthIndependent) return biasedLevenshteinLengthIndependent(x, y)

        val dp = Array(x.length + 1) { IntArray(y.length + 1) }
        for (i in 0..x.length) {
            for (j in 0..y.length) {
                if (i == 0) {
                    dp[i][j] = j
                } else if (j == 0) {
                    dp[i][j] = i
                } else {
                    dp[i][j] = minOf(
                        (dp[i - 1][j - 1] + if (x[i - 1] == y[j - 1]) 0 else 1),
                        (dp[i - 1][j] + 1),
                        (dp[i][j - 1] + 1)
                    )
                }
            }
        }
        val output = dp[x.length][y.length]
        if (y.startsWith(x) || x.startsWith(y)) {
            return output / 3f
        }
        return if (y.contains(x) || x.contains(y)) {
            output / 1.5f
        } else output.toFloat()
    }

    // FINISHME: This should be merged with the function above. I can't be bothered to figure out what the differences between the two are now though
    private fun biasedLevenshteinLengthIndependent(x: String, y: String): Float {
        var x = x
        var y = y
        if (x.length > y.length) x = y.apply { y = x } // Y will be the longer of the two

        val xl = x.length
        val yl = y.length
        val yw = yl + 1
        val dp = IntArray(2 * yw)
        for (j in 0..yl) dp[j] = 0 // Insertions at the beginning are free
        var prev = yw
        var curr = 0
        var temp: Int
        for (i in 1..xl) {
            temp = prev
            prev = curr
            curr = temp
            dp[curr] = i
            for (j in 1..yl) {
                dp[curr + j] = minOf(
                    dp[prev + j - 1] + Mathf.num(x[i - 1] != y[j - 1]),
                    dp[prev + j] + 1,
                    dp[curr + j - 1] + 1,
                )
            }
        }

        // startsWith
        if (dp[curr + xl] == 0) return 0f
        // Disregard insertions at the end - if it made it it made it
        var output = xl
        for (i in curr until curr + yl) {
            output = min(output, dp[i])
        }
        // contains
        return if (output == 0) 0.5f else output.toFloat() // Prefer startsWith
    }
}
