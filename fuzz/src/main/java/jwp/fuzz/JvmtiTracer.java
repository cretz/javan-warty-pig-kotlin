package jwp.fuzz;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Stream;

// Has to be in Java because Kotlin adds extra insns
public class JvmtiTracer implements Tracer {

    private static final Object globalTracerStartStopLocker = new Object();

    private Thread threadStarted;
    private long[] branches;

    @Override
    public void startTrace(@NotNull Thread thread) {
        branches = null;
        threadStarted = thread;
        synchronized (globalTracerStartStopLocker) {
            internalStartTrace(thread);
        }
    }

    private native void internalStartTrace(Thread thread);

    @Override
    public void stopTrace() {
        synchronized (globalTracerStartStopLocker) {
            branches = internalStopTrace(threadStarted);
        }
        threadStarted = null;
    }

    private native long[] internalStopTrace(Thread thread);

    @Nullable
    @Override
    public String methodName(long methodId) {
        if (methodId < 0) return null;
        return internalMethodName(methodId);
    }

    private static native String internalMethodName(long methodId);

    @Nullable
    @Override
    public Class<?> declaringClass(long methodId) {
        if (methodId < 0) return null;
        return internalDeclaringClass(methodId);
    }

    private static native Class<?> internalDeclaringClass(long methodId);

    @NotNull
    @Override
    public Stream<Branch> branches() {
        Objects.requireNonNull(branches);
        return TracerHelper.branchLongsToBranchStream(branches);
    }

    @Override
    public int stableBranchesHash() {
        return DefaultImpls.stableBranchesHash(this);
    }

    @NotNull
    @Override
    public Stream<BranchWithResolvedMethods> branchesWithResolvedMethods() {
        return DefaultImpls.branchesWithResolvedMethods(this);
    }
}
