package com.google.android.gms.tasks;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Controllable SDK boundary: default listeners are queued on the main thread. */
public final class Task<T> {
    private final List<Runnable> listeners = new ArrayList<>();
    private boolean complete;
    private T result;
    private Exception error;

    private void listen(Runnable listener) {
        if (complete) post(listener); else listeners.add(listener);
    }

    private void post(Runnable listener) {
        new Handler(Looper.getMainLooper()).post(listener);
    }

    public Task<T> addOnSuccessListener(Consumer<T> listener) {
        listen(() -> { if (isSuccessful()) listener.accept(result); });
        return this;
    }

    public Task<T> addOnFailureListener(Consumer<Exception> listener) {
        listen(() -> { if (error != null) listener.accept(error); });
        return this;
    }

    public Task<T> addOnCompleteListener(Consumer<Task<T>> listener) {
        listen(() -> listener.accept(this));
        return this;
    }

    public boolean isSuccessful() { return complete && error == null; }
    public boolean isComplete() { return complete; }
    public T getResult() { return result; }
    public Exception getException() { return error; }
    public void succeed(T value) { finish(value, null); }
    public void fail(Exception failure) { finish(null, failure); }

    private void finish(T value, Exception failure) {
        if (complete) throw new IllegalStateException("Task already completed");
        result = value;
        error = failure;
        complete = true;
        for (Runnable listener : listeners) post(listener);
        listeners.clear();
    }
}
