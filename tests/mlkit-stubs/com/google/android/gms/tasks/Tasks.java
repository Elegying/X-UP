package com.google.android.gms.tasks;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public final class Tasks {
    public static Task<Void> whenAll(Collection<? extends Task<?>> tasks) {
        Task<Void> combined = new Task<>();
        if (tasks.isEmpty()) { combined.succeed(null); return combined; }
        int[] remaining = { tasks.size() };
        Exception[] failure = { null };
        for (Task<?> task : tasks) task.addOnCompleteListener(done -> {
            if (!done.isSuccessful()) failure[0] = done.getException();
            if (--remaining[0] == 0) {
                if (failure[0] == null) combined.succeed(null); else combined.fail(failure[0]);
            }
        });
        return combined;
    }

    public static <T> T await(Task<T> task, long timeout, TimeUnit unit) throws Exception {
        if (!task.isComplete()) throw new IllegalStateException("Test task is pending");
        if (!task.isSuccessful()) throw task.getException();
        return task.getResult();
    }
}
