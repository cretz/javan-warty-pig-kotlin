package jwp.fuzz

import java.util.stream.Stream

interface Tracer {
    fun startTrace(thread: Thread)
    fun stopTrace(thread: Thread)

    fun branches(): Stream<Branch>
    fun methodName(methodId: Long): String?
    fun declaringClass(methodId: Long): Class<*>?

    fun branchesWithResolvedMethods(): Stream<BranchWithResolvedMethods> {
        val methodNameMap = HashMap<Long, String?>()
        fun cachedName(methodId: Long) = methodNameMap.getOrPut(methodId) { methodName(methodId) }
        return branches().map { branch ->
            BranchWithResolvedMethods(
                declaringClass(branch.fromMethodId),
                cachedName(branch.fromMethodId),
                declaringClass(branch.toMethodId),
                cachedName(branch.toMethodId),
                branch
            )
        }
    }

    fun stableBranchesHash(): Int =
        // Sort the branches, hash each, then hash all together
        branchesWithResolvedMethods().sorted().
            mapToInt(BranchWithResolvedMethods::stableHashCode).toArray().contentHashCode()

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
            "to ${toMethodDeclaringClass?.name}::$toMethodName(${branch.toLocation}) - $stableHashCode"
    }
}