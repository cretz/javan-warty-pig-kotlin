package jwp.fuzz

import kotlin.test.Test

class JvmtiTracerTest {
    @Test
    fun testSomething() {
        with(JvmtiTracer()) {
            startTrace(Thread.currentThread())
            try {
                println("Trace this!")
            } finally {
                stopTrace(Thread.currentThread())
            }
        }
    }
}