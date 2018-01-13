package jwp.fuzz

import java.util.stream.Stream
import kotlin.streams.asStream

internal object TracerHelper {
    @JvmStatic
    fun branchLongsToBranchStream(branches: LongArray): Stream<Tracer.Branch> =
        branches.asSequence().chunked(5) { (fromMeth, fromLoc, toMeth, toLoc, hits) ->
            Tracer.Branch(fromMeth, fromLoc, toMeth, toLoc, hits.toInt())
        }.asStream()
}