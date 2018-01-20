package jwp.fuzz

import java.util.function.Predicate
import java.util.stream.Stream
import kotlin.streams.asStream

abstract class TraceResult {

    abstract val branchesWithResolvedMethods: List<BranchWithResolvedMethods>

    val stableBranchesHash: Int by lazy {
        // Sort the branches, hash each, then hash all together
        branchesWithResolvedMethods.sorted().
            map(BranchWithResolvedMethods::stableHashCode).toIntArray().contentHashCode()
    }

    fun filtered(pred: Predicate<BranchWithResolvedMethods>): TraceResult = let { orig ->
        object : TraceResult() {
            override val branchesWithResolvedMethods get() = orig.branchesWithResolvedMethods.filter(pred::test)
        }
    }

    // Each branch is a set of 5 longs: methodFrom, locationFrom, methodTo, locationTo,
    // and hit count. Note, some 5-sets of these may be all -1's which means they should not
    // be considered branches and should be ignored
    class LongArrayBranches(val longs: LongArray) : TraceResult() {
        fun branches() = longs.asSequence().chunked(5).mapNotNull {
            (fromMeth, fromLoc, toMeth, toLoc, hits) ->
                if (fromMeth == -1L && fromLoc == -1L) null
                else Branch(fromMeth, fromLoc, toMeth, toLoc, hits.toInt())
        }.asIterable()

        override val branchesWithResolvedMethods by lazy {
            // We'll cache the lookup, though I'm not convinced it improved performance
            val methodInfoMap = HashMap<Long, Pair<Class<*>?, String?>>()
            fun methodInfo(methodId: Long) = methodInfoMap.getOrPut(methodId) {
                if (methodId < 0) Pair(null, null)
                else Pair(JavaUtils.declaringClass(methodId), JavaUtils.methodName(methodId))
            }
            branches().map { branch ->
                val (fromClass, fromName) = methodInfo(branch.fromMethodId)
                val (toClass, toName) = methodInfo(branch.toMethodId)
                BranchWithResolvedMethods(fromClass, fromName, toClass, toName, branch)
            }
        }
    }

    data class Branch(
        val fromMethodId: Long,
        val fromLocation: Long,
        val toMethodId: Long,
        val toLocation: Long,
        val hits: Int
    ) {
        val hitBucket: Int get() = when (hits) {
            1, 2, 3 -> hits
            in 4..7 -> 4
            in 8..15 -> 8
            in 16..31 -> 16
            in 32..127 -> 32
            else -> 128
        }
    }

    data class BranchWithResolvedMethods(
        val fromMethodDeclaringClass: Class<*>?,
        val fromMethodName: String?,
        val toMethodDeclaringClass: Class<*>?,
        val toMethodName: String?,
        val branch: Branch
    ) : Comparable<BranchWithResolvedMethods> {

        val stableHashCode: Int by lazy {
            intArrayOf(
                fromMethodDeclaringClass?.name?.hashCode() ?: 0,
                fromMethodName?.hashCode() ?: 0,
                branch.fromLocation.hashCode(),
                toMethodDeclaringClass?.name?.hashCode() ?: 0,
                toMethodName?.hashCode() ?: 0,
                branch.toLocation.hashCode(),
                branch.hitBucket
            ).contentHashCode()
        }

        // Just compare the stable hashes
        override fun compareTo(other: BranchWithResolvedMethods) = stableHashCode.compareTo(other.stableHashCode)

        override fun toString() =
            "From ${fromMethodDeclaringClass?.name}::$fromMethodName(${branch.fromLocation}) " +
                "to ${toMethodDeclaringClass?.name}::$toMethodName(${branch.toLocation}) - ${branch.hits} hits"
    }
}