package info.kgeorgiy.ja.Gebesht.iterative;


/**
 * Represents a single mapped task (one element from the input list).
 * Stores the computed result and any exception that occurred during execution.
 *
 * @param <T> input element type
 * @param <R> result type
 */
public final class Task<T, R> {
    private final java.util.function.Function<? super T, ? extends R> function;
    private final T argument;
    private R result;
    private RuntimeException exception;
    private boolean ready;

    /**
     * Creates a task that will apply the given function to the argument.
     *
     * @param function the function to apply
     * @param argument the argument to pass to the function
     */
    Task(final java.util.function.Function<? super T, ? extends R> function, final T argument) {
        this.function = function;
        this.argument = argument;
        this.ready = false;
    }

    /**
     * Executes this task by applying the function to the argument.
     * Stores the result or exception, then marks the task as ready.
     */
    public synchronized void execute() {
        try {
            this.result = function.apply(argument);
        } catch (RuntimeException e) {
            this.exception = e;
        }
        this.ready = true;
        notifyAll();
    }

    /**
     * Waits until this task is complete.
     *
     * @throws InterruptedException if waiting thread is interrupted
     */
    public synchronized void await() throws InterruptedException {
        while (!ready) {
            wait();
        }
    }

    /**
     * Returns the exception if one occurred, otherwise null.
     */
    public RuntimeException getException() {
        return exception;
    }

    /**
     * Returns the computed result (may be null if an exception occurred).
     */
    public R getResult() {
        return result;
    }
}