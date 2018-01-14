package jwp.agent

import jvmti.*
import kotlinx.cinterop.*

internal class TracerState {
    private var previousMethodId: jmethodID? = null
    private var previousLocation: jlocation = -1

    data class BranchTuple(
        val fromMethodId: Long,
        val fromLocation: Long,
        val toMethodId: Long,
        val toLocation: Long
    )
    // Value of map is hit count
    val branchTuples = HashMap<BranchTuple, Int>()

    fun step(methodId: jmethodID, location: jlocation) {
        // It's a branch if it's a new method or the location is not the next one
        if (previousMethodId != methodId || previousLocation + 1 != location) {
            val tuple = BranchTuple(
                previousMethodId?.toLong() ?: -1,
                previousLocation,
                methodId.toLong(),
                location
            )
            branchTuples[tuple] = (branchTuples[tuple] ?: 0) + 1
        }
        previousMethodId = methodId
        previousLocation = location
    }
}