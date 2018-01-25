package jwp.fuzz

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.*

object PersistFile {
    open class ByteArrayInputQueue(
        val file: File,
        asyncWrite: Boolean = true,
        private val writeBuffer: ByteBuffer = ByteBuffer.allocateDirect(300000)
    ) : ByteArrayParamGen.ByteArrayInputQueue {
        private val inMemory = DelegatingInMemory(run {
            if (!file.exists()) ArrayList<ByteArrayParamGen.TestCase>() else {
                writeBuffer.clear()
                // We can use the write buffer here since it's at the beginning
                require(FileInputStream(file).channel.use { it.read(writeBuffer) } != -1) { "Buffer not large enough" }
                writeBuffer.flip()
                val len = writeBuffer.int
                (0 until len).mapTo(ArrayList(len)) {
                    ByteArrayParamGen.TestCase(
                        bytes = ByteArray(writeBuffer.int).also { writeBuffer.get(it) },
                        branchHashes = writeBuffer.int.takeIf { it > 0 }?.let { size ->
                            (0 until size).map { writeBuffer.int }
                        },
                        nanoTime = writeBuffer.long.takeIf { it >= 0 }
                    )
                }
            }
        })

        override fun enqueue(testCase: ByteArrayParamGen.TestCase) {
            inMemory.enqueue(testCase)
        }
        override fun cullAndDequeue() = inMemory.cullAndDequeue()

        private val writeExecutor = if (!asyncWrite) Executor { command -> command?.run() } else {
            ThreadPoolExecutor(1, 1, 30, TimeUnit.MINUTES,
                ArrayBlockingQueue(2), ThreadPoolExecutor.DiscardOldestPolicy())
        }

        private fun writeQueue(list: List<ByteArrayParamGen.TestCase>) = writeExecutor.execute {
            writeBuffer.clear()
            writeBuffer.putInt(list.size)
            list.forEach { case ->
                writeBuffer.putInt(case.bytes.size)
                writeBuffer.put(case.bytes)
                writeBuffer.putInt(case.branchHashes?.size ?: 0)
                case.branchHashes?.forEach { writeBuffer.putInt(it) }
                writeBuffer.putLong(case.nanoTime ?: -1)
            }
            writeBuffer.flip()
            FileOutputStream(file).channel.use { it.write(writeBuffer) }
        }

        override fun close() {
            (writeExecutor as? ExecutorService)?.also {
                it.shutdown()
                it.awaitTermination(10, TimeUnit.SECONDS)
            }
        }

        private inner class DelegatingInMemory(list: MutableList<ByteArrayParamGen.TestCase>) :
                ByteArrayParamGen.ByteArrayInputQueue.ListBacked(list) {
            override fun onQueueMutated() = writeQueue(queue.toTypedArray().toList())
        }
    }
}