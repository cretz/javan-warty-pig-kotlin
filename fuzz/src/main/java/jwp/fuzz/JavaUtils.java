package jwp.fuzz;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;

class JavaUtils {

    static native void startJvmtiTrace(Thread thread);
    // Only returns possible branches (i.e. different methods and
    // location off by more than one), not actual branches (checking bytecodes).
    // Note this returns 5-length longs.
    static native long[] stopJvmtiTrace(Thread thread);

    // Each possible branch is a 5-long section of the array. This method puts all 5
    // values as -1 if the branch is not an actual branch.
    static native void markNonBranches(long[] possible);

    static native String methodName(long methodId);
    static native Class<?> declaringClass(long methodId);

    // Has to be in Java, Kotlin adds dumb result checks and extra insns unnecessarily
    static TraceComplete invokeTraced(@NotNull Tracer tracer, @NotNull MethodHandle mh, Object... args) {
        Object result;
        TraceResult traceResult;
        Thread thread = Thread.currentThread();
        tracer.startTrace(thread);
        try {
            result = mh.invokeWithArguments(args);
            traceResult = tracer.stopTrace(thread);
            return new TraceComplete(result, traceResult);
        } catch (Throwable e) {
            traceResult = tracer.stopTrace(thread);
            return new TraceComplete(e, traceResult);
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
