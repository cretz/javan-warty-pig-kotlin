package jwp.fuzz

class Natives {

    interface StepHandler {
        fun step(thread: Thread, methodId: Long, locationId: Long)
    }

    companion object {
        @JvmStatic
        external fun startTrace(thread: Thread, handler: StepHandler)

        @JvmStatic
        external fun stopTrace(thread: Thread)
    }
}