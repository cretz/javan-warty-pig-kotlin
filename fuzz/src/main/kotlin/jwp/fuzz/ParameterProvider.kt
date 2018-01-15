package jwp.fuzz

import java.util.*
import java.util.function.Predicate
import kotlin.collections.HashSet
import kotlin.coroutines.experimental.SequenceBuilder
import kotlin.coroutines.experimental.buildSequence

// Parameter providers are iterables of nullable object arrays.
object ParameterProvider {

    // All ones with feedback are collected together and only one changed at a time (which one is random). All
    // ones without feedback are separated into "small-fixed" (small fixed boundaries and only so many) and
    // "other". "small-fixed" generated all permutations, "other" is one random param changed per.
    fun suggested(paramGens: Array<Iterable<*>>): Iterable<Array<Any?>> = separateWithAndWithoutFeedback(
        paramGens = paramGens,
        withFeedbackProvider = { randomSingleParamChange(it) },
        withoutFeedbackProvider = {
            separateFixedAndUnfixedSize(
                paramGens = it,
                fixedSizeProvider = ::allPermutations,
                unfixedSizeProvider = { randomSingleParamChange(it) }
            )
        }
    )

    fun separateWithAndWithoutFeedback(
        paramGens: Array<Iterable<*>>,
        withFeedbackProvider: (Array<Iterable<*>>) -> Iterable<Array<Any?>>,
        withoutFeedbackProvider: (Array<Iterable<*>>) -> Iterable<Array<Any?>>,
        stopWhenBothHaveEndedOnce: Boolean = true
    ): Iterable<Array<Any?>> = withFeedback(paramGens, {
        partitioned(paramGens, withFeedbackProvider, withoutFeedbackProvider, stopWhenBothHaveEndedOnce,
            Predicate { it is TypeGen.WithFeedback })
    })

    fun separateFixedAndUnfixedSize(
        paramGens: Array<Iterable<*>>,
        fixedSizeProvider: (Array<Iterable<*>>) -> Iterable<Array<Any?>>,
        unfixedSizeProvider: (Array<Iterable<*>>) -> Iterable<Array<Any?>>,
        stopWhenBothHaveEndedOnce: Boolean = true,
        maxFixedSizeCount: Int = 500,
        maxFixedSizeParams: Int = 5
    ): Iterable<Array<Any?>> {
        var seenFixed = 0
        return partitioned(paramGens, fixedSizeProvider, unfixedSizeProvider, stopWhenBothHaveEndedOnce, Predicate {
            val consideredFixed = it is Collection && it.size <= maxFixedSizeCount && seenFixed < maxFixedSizeParams
            if (consideredFixed) seenFixed++
            consideredFixed
        })
    }

    fun partitioned(
        paramGens: Array<Iterable<*>>,
        trueProvider: (Array<Iterable<*>>) -> Iterable<Array<Any?>>,
        falseProvider: (Array<Iterable<*>>) -> Iterable<Array<Any?>>,
        stopWhenBothHaveEndedOnce: Boolean,
        pred: Predicate<Iterable<*>>
    ) : Iterable<Array<Any?>> {
        require(paramGens.isNotEmpty())
        val (trueAndIndex, falseAndIndex) = paramGens.withIndex().partition { (_, value) -> pred.test(value) }
        if (falseAndIndex.isEmpty()) return trueProvider(paramGens)
        if (trueAndIndex.isEmpty()) return falseProvider(paramGens)
        return buildSequence {
            val trueIterable = InfiniteRestartingIterable(trueProvider(paramGens))
            var trueIterator = trueIterable.iterator()
            val falseIterable = InfiniteRestartingIterable(falseProvider(paramGens))
            val falseIterator = falseIterable.iterator()
            val arr = arrayOfNulls<Any>(paramGens.size)
            while (!stopWhenBothHaveEndedOnce ||
                    !trueIterable.completedAtLeastOnce ||
                    !falseIterable.completedAtLeastOnce) {
                trueIterator.next().forEachIndexed { index, param -> arr[trueAndIndex[index].index] = param }
                falseIterator.next().forEachIndexed { index, param -> arr[falseAndIndex[index].index] = param }
                yield(arr.copyOf())
            }
        }.asIterable()
    }

    fun withFeedback(
        paramGens: Array<Iterable<*>>,
        provider: (Array<Iterable<*>>) -> Iterable<Array<Any?>>
    ) : Iterable<Array<Any?>> = object : WithFeedback, Iterable<Array<Any?>> {
        val iter = provider(paramGens)

        override fun onResult(result: ExecutionResult) {
            paramGens.forEachIndexed { index, paramGen ->
                if (paramGen is TypeGen.WithFeedback) paramGen.onResult(result, index)
            }
        }

        override fun iterator() = iter.iterator()
    }

    fun alwaysChangeEachParamEvenly(
        paramGens: Array<Iterable<*>>,
        completeWhenAllCycledAtLeastOnce: Boolean = true
    ): Iterable<Array<Any?>> = buildSequence {
        val iters = paramGens.map { it.iterator() }.toTypedArray()
        val cycleComplete = BooleanArray(paramGens.size)
        while (true) {
            val complete = iters.foldIndexed(true) { index, complete, iterator ->
                if (!iterator.hasNext()) {
                    cycleComplete[index] = true
                    iters[index] = paramGens[index].iterator()
                }
                complete && cycleComplete[index]
            }
            if (completeWhenAllCycledAtLeastOnce && complete) break
            yield(iters.map { it.next() }.toTypedArray())
        }
    }.asIterable()

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
    ): Iterable<Array<Any?>> = buildSequence {
        val rand = if (randSeed == null) Random() else Random(randSeed)
        val iters = paramGens.map { it.iterator() }.toTypedArray()
        val seenParamIndexSets = HashSet<Int>()
        val iterIndices = IntArray(paramGens.size).also { seenParamIndexSets.add(it.contentHashCode()) }
        fun iterNext(index: Int): Any? {
            if (!iters[index].hasNext()) {
                iters[index] = paramGens[index].iterator()
                iterIndices[index] = 0
            } else iterIndices[index]++
            return iters[index].next()
        }
        val vals = paramGens.indices.map(::iterNext).toTypedArray()
        mainLoop@ while (true) {
            yield(vals)
            for (i in 0 until maxDupeGenBeforeQuit) {
                val index = rand.nextInt(paramGens.size)
                vals[index] = iterNext(index)
                if (seenParamIndexSets.add(iterIndices.contentHashCode())) {
                    if (seenParamIndexSets.size >= hashSetMaxBeforeReset) seenParamIndexSets.clear()
                    continue@mainLoop
                }
            }
            break
        }
    }.asIterable()

    interface WithFeedback {
        fun onResult(result: ExecutionResult)
    }

    class InfiniteRestartingIterable<T>(val orig: Iterable<T>) : Iterable<T> {
        var completedAtLeastOnce = false
        override fun iterator() = object : Iterator<T> {
            var iter = orig.iterator()
            init { require(iter.hasNext()) }
            override fun hasNext() = true
            override fun next(): T {
                if (!iter.hasNext()) {
                    completedAtLeastOnce = true
                    iter = orig.iterator()
                }
                return iter.next()
            }
        }
    }
}