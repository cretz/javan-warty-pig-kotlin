package jwp.fuzz

import java.util.*
import java.util.function.Function
import java.util.function.Predicate
import kotlin.coroutines.experimental.SequenceBuilder
import kotlin.coroutines.experimental.buildSequence

interface ParamProvider : Iterable<Array<Any?>> {

    interface WithFeedback {
        fun onResult(result: ExecutionResult)
    }

    abstract class WithFeedbackDelegated : ParamProvider, WithFeedback {
        abstract val gens: List<ParamGen<*>>

        override fun onResult(result: ExecutionResult) = gens.forEachIndexed { index, gen ->
            if (gen is ParamGen.WithFeedback) gen.onResult(result, index)
        }
    }

    // All byte arrays change evenly amongst each other. The first few small fixed params are done w/ all
    // permutations across each other, the rest use random single change across each other
    open class Suggested(
        override val gens: List<ParamGen<*>>,
        val randSeed: Long = Random().nextLong()
    ) : WithFeedbackDelegated() {
        private val prov by lazy {
            Partitioned(
                gens = gens,
                pred = Predicate { it is ByteArrayParamGen },
                stopWhenBothHaveEndedOnce = true,
                trueProvider = Function { EvenSingleParamChange(it) },
                falseProvider = Function {
                    SeparateFixedAndUnfixedSize(
                        gens = it,
                        fixedSizeProvider = Function { AllPermutations(it) },
                        unfixedSizeProvider = Function { RandomSingleParamChange(it, randSeed) }
                    )
                }
            )
        }

        override fun iterator() = prov.iterator()
    }

    open class SeparateFixedAndUnfixedSize(
        override val gens: List<ParamGen<*>>,
        val fixedSizeProvider: Function<List<ParamGen<*>>, ParamProvider>,
        val unfixedSizeProvider: Function<List<ParamGen<*>>, ParamProvider>,
        val stopWhenBothHaveEndedOnce: Boolean = true,
        val maxFixedSizeCount: Int = 500,
        val maxFixedSizeParams: Int = 5
    ) : WithFeedbackDelegated() {
        override fun iterator(): Iterator<Array<Any?>> {
            var seenFixed = 0
            return Partitioned(gens, fixedSizeProvider, unfixedSizeProvider, stopWhenBothHaveEndedOnce, Predicate {
                val consideredFixed =
                    it is Collection<*> && it.size <= maxFixedSizeCount && seenFixed < maxFixedSizeParams
                if (consideredFixed) seenFixed++
                consideredFixed
            }).iterator()
        }
    }

    open class Partitioned(
        override val gens: List<ParamGen<*>>,
        val trueProvider:  Function<List<ParamGen<*>>, ParamProvider>,
        val falseProvider:  Function<List<ParamGen<*>>, ParamProvider>,
        val stopWhenBothHaveEndedOnce: Boolean,
        val pred: Predicate<ParamGen<*>>
    ) : WithFeedbackDelegated() {
        override fun iterator(): Iterator<Array<Any?>> {
            val (trueAndIndex, falseAndIndex) = gens.withIndex().partition { (_, value) -> pred.test(value) }
            if (falseAndIndex.isEmpty()) return trueProvider.apply(gens).iterator()
            if (trueAndIndex.isEmpty()) return falseProvider.apply(gens).iterator()
            return buildSequence {
                val trueIterable = InfiniteRestartingIterable(trueProvider.apply(gens))
                val trueIterator = trueIterable.iterator()
                val falseIterable = InfiniteRestartingIterable(falseProvider.apply(gens))
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
    ) : WithFeedbackDelegated() {
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
    ) : WithFeedbackDelegated() {
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
    ) : WithFeedbackDelegated() {
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