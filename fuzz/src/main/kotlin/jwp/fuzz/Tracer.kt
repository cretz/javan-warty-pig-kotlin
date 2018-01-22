package jwp.fuzz

interface Tracer {
    // Actually, this thread is not nullable but we don't want Kotlin
    // checking it at runtime.
    fun startTrace(thread: Thread?)
    // Actually, this thread is not nullable but we don't want Kotlin
    // checking it at runtime.
    fun stopTrace(thread: Thread?): TraceResult

    open class JvmtiTracer(
        val branchClassExcluder: BranchClassExcluder? =
            BranchClassExcluder.ByQualifiedClassNamePrefix(*defaultExcludedClassPrefixes.toTypedArray())
    ) : Tracer {
        override fun startTrace(thread: Thread?) = JavaUtils.startJvmtiTrace(thread)

        override fun stopTrace(thread: Thread?): TraceResult {
            val branches = JavaUtils.stopJvmtiTrace(thread)
            // This puts -1 in the place of non-branches
            JavaUtils.markNonBranches(branches)
            val result = TraceResult.LongArrayBranches(branches)
            if (branchClassExcluder == null) return result
            return result.filtered { branch ->
                !branchClassExcluder.excludeBranch(branch.fromMethodDeclaringClass, branch.toMethodDeclaringClass)
            }
        }

        companion object {
            val defaultExcludedClassPrefixes =
                listOf("java.", "jdk.internal.", "jwp.fuzz.", "kotlin.", "scala.", "sun.")
        }
    }

    interface BranchClassExcluder {
        fun excludeBranch(fromClass: Class<*>?, toClass: Class<*>?): Boolean

        open class ByQualifiedClassNamePrefix(vararg val prefixes: String) : BranchClassExcluder {
            override fun excludeBranch(fromClass: Class<*>?, toClass: Class<*>?) =
                fromClass != null && toClass != null &&
                    prefixes.any { fromClass.name.startsWith(it) || toClass.name.startsWith(it) }
        }
    }
}