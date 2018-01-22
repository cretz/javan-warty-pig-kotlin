package jwp.fuzz

import java.io.Closeable
import java.util.*
import kotlin.coroutines.experimental.SequenceBuilder
import kotlin.coroutines.experimental.buildSequence

interface ParamProvider : Iterable<Array<Any?>>, Closeable {

    interface WithFeedback {
        fun onResult(result: ExecutionResult)
    }

    abstract class WithFeedbackAndCloseDelegated : ParamProvider, WithFeedback {
        abstract val gens: List<ParamGen<*>>

        override fun onResult(result: ExecutionResult) = gens.forEachIndexed { index, gen ->
            if (gen is ParamGen.WithFeedback) gen.onResult(result, index)
        }
        override fun close() { gens.forEach { if (it is Closeable) it.close() } }
    }

    // All byte arrays change evenly amongst each other. The first few small fixed params are done w/ all
    // permutations across each other, the rest use random single change across each other
    open class Suggested(
        override val gens: List<ParamGen<*>>,
        val randSeed: Long = Random().nextLong()
    ) : WithFeedbackAndCloseDelegated() {
        private val prov by lazy {
            Partitioned(
                gens = gens,
                pred = { it is ByteArrayParamGen },
                stopWhenBothHaveEndedOnce = true,
                trueProvider = { EvenSingleParamChange(it) },
                falseProvider = {
                    SeparateFixedAndUnfixedSize(
                        gens = it,
                        fixedSizeProvider = { AllPermutations(it) },
                        unfixedSizeProvider = { RandomSingleParamChange(it, randSeed) }
                    )
                }
            )
        }

        override fun iterator() = prov.iterator()
    }

    open class SeparateFixedAndUnfixedSize(
        override val gens: List<ParamGen<*>>,
        val fixedSizeProvider: (List<ParamGen<*>>) -> ParamProvider,
        val unfixedSizeProvider: (List<ParamGen<*>>) -> ParamProvider,
        val stopWhenBothHaveEndedOnce: Boolean = true,
        val maxFixedSizeCount: Int = 500,
        val maxFixedSizeParams: Int = 5
    ) : WithFeedbackAndCloseDelegated() {
        override fun iterator(): Iterator<Array<Any?>> {
            var seenFixed = 0
            return Partitioned(gens, fixedSizeProvider, unfixedSizeProvider, stopWhenBothHaveEndedOnce) {
                val consideredFixed =
                    it is Collection<*> && it.size <= maxFixedSizeCount && seenFixed < maxFixedSizeParams
                if (consideredFixed) seenFixed++
                consideredFixed
            }.iterator()
        }
    }

    open class Partitioned(
        override val gens: List<ParamGen<*>>,
        val trueProvider:  (List<ParamGen<*>>) -> ParamProvider,
        val falseProvider:  (List<ParamGen<*>>) -> ParamProvider,
        val stopWhenBothHaveEndedOnce: Boolean,
        val pred: (ParamGen<*>) -> Boolean
    ) : WithFeedbackAndCloseDelegated() {
        override fun iterator(): Iterator<Array<Any?>> {
            val (trueAndIndex, falseAndIndex) = gens.withIndex().partition { (_, value) -> pred(value) }
            if (falseAndIndex.isEmpty()) return trueProvider(gens).iterator()
            if (trueAndIndex.isEmpty()) return falseProvider(gens).iterator()
            return buildSequence {
                val trueIterable = InfiniteRestartingIterable(trueProvider(gens))
                val trueIterator = trueIterable.iterator()
                val falseIterable = InfiniteRestartingIterable(falseProvider(gens))
                val falseIterator = falseIterable.iterator()
                val arr = arrayOfNulls<Any>(gens.size)
                while (!stopWhenBothHaveEndedOnce ||
                        !trueIterable.completedAtLeastOnce ||
                        !falseIterable.completedAtLeastOnce) {
                    trueIterator.next().forEachIndexed { index, param -> arr[trueAndIndex[index].index] = param }
                    falseIterator.next().forEachIndexed { index, param -> arr[falseAndIndex[index].index] = param }
                    yield(arr.copyOf())
                }
            }.iterator()
        }
    }

    open class EvenSingleParamChange(
        override val gens: List<ParamGen<*>>,
        val completeWhenAllCycledAtLeastOnce: Boolean = true
    ) : WithFeedbackAndCloseDelegated() {
        override fun iterator() = buildSequence {
            val iters = gens.map { it.iterator() }.toTypedArray()
            val cycleComplete = BooleanArray(gens.size)
            while (true) {
                val complete = iters.foldIndexed(true) { index, complete, iterator ->
                    if (!iterator.hasNext()) {
                        cycleComplete[index] = true
                        iters[index] = gens[index].iterator()
                    }
                    complete && cycleComplete[index]
                }
                if (completeWhenAllCycledAtLeastOnce && complete) break
                yield(iters.map { it.next() }.toTypedArray())
            }
        }.iterator()
    }

    open class AllPermutations(
        override val gens: List<ParamGen<*>>
    ) : WithFeedbackAndCloseDelegated() {
        override fun iterator() = buildSequence { applyPermutation(0, arrayOfNulls<Any?>(gens.size)) }.iterator()

        private suspend fun SequenceBuilder<Array<Any?>>.applyPermutation(index: Int, workingSet: Array<Any?>) {
            val iter = gens[index].iterator()
            while (iter.hasNext()) {
                workingSet[index] = iter.next()
                if (index == gens.size - 1) yield(workingSet.copyOf())
                else applyPermutation(index + 1, workingSet)
            }
        }
    }

    open class RandomSingleParamChange(
        override val gens: List<ParamGen<*>>,
        val randSeed: Long? = null,
        val hashSetMaxBeforeReset: Int = 20000,
        val maxDupeGenBeforeQuit: Int = 200
    ) : WithFeedbackAndCloseDelegated() {
        override fun iterator() = buildSequence {
            val rand = if (randSeed == null) Random() else Random(randSeed)
            val iters = gens.map { it.iterator() }.toTypedArray()
            val seenParamIndexSets = HashSet<Int>()
            val iterIndices = IntArray(gens.size).also { seenParamIndexSets.add(it.contentHashCode()) }
            fun iterNext(index: Int): Any? {
                if (!iters[index].hasNext()) {
                    iters[index] = gens[index].iterator()
                    iterIndices[index] = 0
                } else iterIndices[index]++
                return iters[index].next()
            }
            val vals = gens.indices.map(::iterNext).toTypedArray()
            mainLoop@ while (true) {
                yield(vals)
                for (i in 0 until maxDupeGenBeforeQuit) {
                    val index = rand.nextInt(gens.size)
                    vals[index] = iterNext(index)
                    if (seenParamIndexSets.add(iterIndices.contentHashCode())) {
                        if (seenParamIndexSets.size >= hashSetMaxBeforeReset) seenParamIndexSets.clear()
                        continue@mainLoop
                    }
                }
                break
            }
        }.iterator()
    }

    open class InfiniteRestartingIterable<T>(val orig: Iterable<T>) : Iterable<T> {
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