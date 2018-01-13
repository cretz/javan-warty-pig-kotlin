package jwp.fuzz;

import java.lang.invoke.MethodHandle;

class JavaInvoker {
    // Has to be in Java, Kotlin adds dumb result checks
    static Object invoke(Tracer tracer, MethodHandle mh, Object... args) throws Throwable {
        tracer.startTrace(Thread.currentThread());
        try {
            return mh.invoke(args);
        } finally {
            tracer.stopTrace();
        }
    }
}