package jwp.fuzz

import net.openhft.chronicle.queue.ChronicleQueue
import net.openhft.chronicle.set.ChronicleSet
import net.openhft.chronicle.wire.ValueOut
import java.util.function.BiConsumer

object PersistChronicle {
    abstract class TrackUniqueBranches(
        val set: ChronicleSet<Int>,
        includeHitCounts: Boolean = true,
        val closeSetOnClose: Boolean = true
    ) : Fuzzer.PostSubmissionHandler.TrackUniqueBranches(
        includeHitCounts = includeHitCounts,
        backingSet = set
    ) {
        override fun close() { if (closeSetOnClose) set.close() }
    }

    open class BranchesHashCache(
        val set: ChronicleSet<Int>,
        val closeSetOnClose: Boolean = true
    ) : ByteArrayParamGen.BranchesHashCache.SetBacked(set) {
        override fun close() { if (closeSetOnClose) set.close() }
    }

    open class ByteArrayInputQueue(
        val queue: ChronicleQueue,
        val closeQueueOnClose: Boolean = true
    ) : ByteArrayParamGen.ByteArrayInputQueue {

        private val inMemory = queue.createTailer().let { tailer ->
            val queue = ArrayList<ByteArrayParamGen.TestCase>()
            while (tailer.readDocument { win ->
                val vin = win.read()
                queue += ByteArrayParamGen.TestCase(
                    bytes = vin.int32().let { len ->
                        ByteArray(len).also { bytes ->
                            require(vin.array(bytes) == bytes.size) { "Deserialization error" }
                        }
                    },
                    branchHashes = vin.int32().takeIf { it > 0 }?.let { len ->
                        IntArray(len).also { ints ->
                            require(vin.array(ints) == ints.size) { "Deserialization error" }
                        }.toList()
                    },
                    nanoTime = vin.int64().takeIf { it >= 0 }
                )
            });
            object : ByteArrayParamGen.ByteArrayInputQueue.ListBacked(queue) {
                val inMemoryQueue get() = queue
                override fun onQueueCullComplete() = refreshQueue()
            }
        }

        val appender = queue.acquireAppender()

        override fun enqueue(testCase: ByteArrayParamGen.TestCase) = inMemory.enqueue(testCase)

        override fun cullAndDequeue() = inMemory.cullAndDequeue()

        fun refreshQueue() {
            queue.clear()
            inMemory.inMemoryQueue.forEach { appender.writeDocument(it, TestCaseWriter) }
        }

        override fun close() { if (closeQueueOnClose) queue.close() }

        object TestCaseWriter : BiConsumer<ValueOut, ByteArrayParamGen.TestCase> {
            override fun accept(out: ValueOut, case: ByteArrayParamGen.TestCase) {
                out.int32(case.bytes.size)
                out.array(case.bytes, case.bytes.size)
                out.int32(case.branchHashes?.size ?: 0)
                out.array(case.branchHashes?.toIntArray() ?: intArrayOf(), case.branchHashes?.size ?: 0)
                out.int64(case.nanoTime ?: -1)
            }
        }
    }
}