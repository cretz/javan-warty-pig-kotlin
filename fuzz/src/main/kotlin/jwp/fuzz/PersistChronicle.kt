package jwp.fuzz

import net.openhft.chronicle.queue.ChronicleQueue
import net.openhft.chronicle.queue.ChronicleQueueBuilder
import net.openhft.chronicle.queue.RollCycles
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder
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
        init { println("Hash cache at start: ${set.size}") }
        override fun close() { if (closeSetOnClose) set.close() }
    }

    @Deprecated("This does not work very well")
    open class ByteArrayInputQueue(
        val chronQueue: ChronicleQueue,
        val closeQueueOnClose: Boolean = true
    ) : ByteArrayParamGen.ByteArrayInputQueue {

        constructor(builder: ChronicleQueueBuilder<*>, closeQueueOnClose: Boolean = true) : this(
            // For now, we just roll forever sadly. We keep a number on the case so we can know
            // if we're using the latest version. And we also delete files when they are no longer
            // in use. Ref: https://github.com/OpenHFT/Chronicle-Queue/issues/424
            chronQueue = builder.storeFileListener { _, file ->
                try { require(file.delete()) { "File.delete returned false"} }
                catch (e: Exception) { println("Failed deleting file $file: $e") }
            }.rollCycle(RollCycles.HOURLY).build(),
            closeQueueOnClose = closeQueueOnClose
        )

        private var rollCounter = 0L
        private val inMemory: DelegatingQueue

        init {
            inMemory = chronQueue.createTailer().let { tailer ->
                val list = ArrayList<ByteArrayParamGen.TestCase>()
                while (tailer.readDocument { win ->
                    val vin = win.read()
                    val currCounter = vin.int64()
                    if (currCounter != rollCounter) list.clear().also { rollCounter = currCounter }
                    list += ByteArrayParamGen.TestCase(
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
                // Moved because of https://youtrack.jetbrains.com/issue/KT-22511
                println("Queue at start: ${list.size}")
                DelegatingQueue(list)
            }
        }

        private val appender = chronQueue.acquireAppender()

        override fun enqueue(testCase: ByteArrayParamGen.TestCase) = inMemory.enqueue(testCase)

        override fun cullAndDequeue() = inMemory.cullAndDequeue()

        fun persistQueue(list: List<ByteArrayParamGen.TestCase>) {
            rollCounter++
            val writer = object : BiConsumer<ValueOut, ByteArrayParamGen.TestCase> {
                override fun accept(out: ValueOut, case: ByteArrayParamGen.TestCase) {
                    out.int64(rollCounter)
                    out.int32(case.bytes.size)
                    out.array(case.bytes, case.bytes.size)
                    out.int32(case.branchHashes?.size ?: 0)
                    out.array(case.branchHashes?.toIntArray() ?: intArrayOf(), case.branchHashes?.size ?: 0)
                    out.int64(case.nanoTime ?: -1)
                }
            }
            list.forEach { appender.writeDocument(it, writer) }
        }

        override fun close() { if (closeQueueOnClose) chronQueue.close() }

        inner class DelegatingQueue(list: MutableList<ByteArrayParamGen.TestCase>) :
                ByteArrayParamGen.ByteArrayInputQueue.ListBacked(list) {
            override fun onQueueCullComplete() = persistQueue(queue)
        }
    }
}