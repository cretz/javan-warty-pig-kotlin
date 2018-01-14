package jwp.fuzz;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;

class JavaUtils {

    static native void startJvmtiTrace(@NotNull Thread thread);
    static native long[] stopJvmtiTrace(@NotNull Thread thread);

    static native String methodName(long methodId);
    static native Class<?> declaringClass(long methodId);

    // Has to be in Java, Kotlin adds dumb result checks
    static TraceComplete invokeTraced(@NotNull Tracer tracer, @NotNull MethodHandle mh, Object... args) throws Throwable {
        tracer.startTrace(Thread.currentThread());
        try {
            return new TraceComplete(mh.invoke(args), tracer.stopTrace(Thread.currentThread()));
        } catch (Throwable e) {
            return new TraceComplete(e, tracer.stopTrace(Thread.currentThread()));
        }
    }

    static class TraceComplete {
        final Object result;
        final Throwable exception;
        final TraceResult tracerResult;

        TraceComplete(@NotNull Object result, @NotNull TraceResult tracerResult) {
            this.result = result;
            this.exception = null;
            this.tracerResult = tracerResult;
        }

        TraceComplete(@NotNull Throwable exception, @NotNull TraceResult tracerResult) {
            this.result = null;
            this.exception = exception;
            this.tracerResult = tracerResult;
        }
    }
}
