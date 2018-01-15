package jwp.fuzz

import java.util.*
import kotlin.collections.HashSet
import kotlin.coroutines.experimental.SequenceBuilder
import kotlin.coroutines.experimental.buildSequence

// Parameter providers are iterables of nullable object arrays
object ParameterProvider {
    fun allPermutations(paramGens: Array<Iterable<*>>): Iterable<Array<Any?>> = buildSequence {
        applyPermutation(paramGens, 0, arrayOfNulls<Any?>(paramGens.size))
    }.asIterable()

    private suspend fun SequenceBuilder<Array<Any?>>.applyPermutation(
        paramGens: Array<Iterable<*>>,
        index: Int,
        workingSet: Array<Any?>
    ) {
        val iter = paramGens[index].iterator()
        while (iter.hasNext()) {
            workingSet[index] = iter.next()
            if (index == paramGens.size - 1) yield(workingSet.copyOf())
            else applyPermutation(paramGens, index + 1, workingSet)
        }
    }

    fun randomSingleParamChange(
        paramGens: Array<Iterable<*>>,
        randSeed: Long? = null,
        hashSetMaxBeforeReset: Int = 20000,
        maxDupeGenBeforeQuit: Int = 200
    ): Iterable<Array<Any?>> = object : Iterable<Array<Any?>> {
        override fun iterator() =
            RandomSingleParamChangeIterator(paramGens, randSeed, hashSetMaxBeforeReset, maxDupeGenBeforeQuit)
    }

    private class RandomSingleParamChangeIterator(
        paramGens: Array<Iterable<*>>,
        randSeed: Long? = null,
        val hashSetMaxBeforeReset: Int = 20000,
        val maxDupeGenBeforeQuit: Int = 200
    ) : Iterator<Array<Any?>> {

        val rand = if (randSeed == null) Random() else Random(randSeed)
        val params = paramGens.map(::Param)
        // This is just going to change a single param making sure that we haven't seen it before.
        val paramSet = arrayOfNulls<Any>(params.size)
        var paramSetInited = false
        val paramIndexSet = IntArray(params.size)
        val seenParamIndexSets = HashSet<Int>()

        var hasNext = true
        var nextAlreadyRan = false

        override fun hasNext() =
            if (nextAlreadyRan) hasNext
            else (nextParamSet() != null).also { hasNext = it; nextAlreadyRan = true }

        override fun next() =
            if (nextAlreadyRan) paramSet.also { nextAlreadyRan = false }
            else nextParamSet() ?: error("No next")

        private fun nextParamSet(): Array<Any?>? {
            if (!paramSetInited) {
                params.forEachIndexed { index, param -> paramSet[index] = param.next() }
                paramSetInited = true
                seenParamIndexSets.add(paramIndexSet.contentHashCode())
                return paramSet
            }
            if (seenParamIndexSets.size >= hashSetMaxBeforeReset) seenParamIndexSets.clear()
            for (i in 0 until maxDupeGenBeforeQuit) {
                val paramIndexToChange = rand.nextInt(params.size)
                paramSet[paramIndexToChange] = params[paramIndexToChange].next()
                paramIndexSet[paramIndexToChange] = params[paramIndexToChange].index
                val indexSetHash = paramIndexSet.contentHashCode()
                if (seenParamIndexSets.add(indexSetHash)) return paramSet
            }
            return null
        }

        class Param(val gen: Iterable<*>) {
            var index = -1
            var iter = gen.iterator()

            fun next(): Any? {
                if (!iter.hasNext()) {
                    iter = gen.iterator()
                    index = -1
                }
                index++
                return iter.next()
            }
        }
    }
}