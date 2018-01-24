package jwp.fuzz

import org.mapdb.DB
import org.mapdb.DataInput2
import org.mapdb.DataOutput2
import org.mapdb.Serializer

object PersistMapDb {
    abstract class TrackUniqueBranches(
        val db: DB,
        val hashSetName: String = TrackUniqueBranches::class.java.name,
        includeHitCounts: Boolean = true
    ) : Fuzzer.PostSubmissionHandler.TrackUniqueBranches(
        includeHitCounts = includeHitCounts,
        backingSet = db.hashSet(hashSetName, Serializer.INTEGER).createOrOpen()
    )

    open class BranchesHashCache(
        val db: DB,
        val hashSetName: String = BranchesHashCache::class.java.name,
        val closeDbOnClose: Boolean = true
    ) : ByteArrayParamGen.BranchesHashCache.SetBacked(db.hashSet(hashSetName, Serializer.INTEGER).createOrOpen()) {
        override fun close() { if (closeDbOnClose) db.close() }
    }

    @Suppress("UNCHECKED_CAST")
    open class ByteArrayInputQueue(
        val db: DB,
        val listName: String = ByteArrayInputQueue::class.java.name,
        val closeDbOnClose: Boolean = true
    ) : ByteArrayParamGen.ByteArrayInputQueue.ListBacked(
        db.indexTreeList(listName, TestCaseSerializer).createOrOpen() as MutableList<ByteArrayParamGen.TestCase>
    ) {
        override fun close() { if (closeDbOnClose) db.close() }
    }

    object TestCaseSerializer : Serializer<ByteArrayParamGen.TestCase> {
        override fun serialize(out: DataOutput2, value: ByteArrayParamGen.TestCase) {
            Serializer.BYTE_ARRAY.serialize(out, value.bytes)
            Serializer.INT_ARRAY.serialize(out, value.branchHashes?.toIntArray() ?: intArrayOf())
            Serializer.LONG.serialize(out, value.nanoTime ?: -1)
        }

        override fun deserialize(input: DataInput2, available: Int) = ByteArrayParamGen.TestCase(
            bytes = Serializer.BYTE_ARRAY.deserialize(input, available),
            branchHashes = Serializer.INT_ARRAY.deserialize(input, available).takeIf { it.isNotEmpty() }?.toList(),
            nanoTime = Serializer.LONG.deserialize(input, available).takeIf { it >= 0 }
        )
    }
}