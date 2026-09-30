package info.kgeorgiy.ja.Gebesht.iterative;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Parallel mapper implementation that distributes tasks among a fixed number of worker threads.
 *
 * <p>This class provides a thread pool that executes submitted tasks (mapping functions over lists)
 * and allows waiting for completion. It does not rely on {@code java.util.concurrent} and uses only
 * low-level synchronization constructs.
 *
 * <p>The mapper can be closed, after which no new tasks are accepted and worker threads terminate
 * once the queue becomes empty.
 *
 * @author Andrey Gebesht
 * @see ParallelMapper
 */
public final class ParallelMapperImpl implements ParallelMapper {
    private final List<Thread> workers;
    private final Deque<Task<?, ?>> tasks = new ArrayDeque<>();
    private final Object monitor = new Object();
    private boolean closed = false;

    /**
     * Creates a new parallel mapper with the specified number of worker threads.
     *
     * @param workers the number of worker threads to create (must be positive)
     * @throws IllegalArgumentException if {@code workers <= 0}
     */
    public ParallelMapperImpl(final int workers) {
        if (workers <= 0) {
            throw new IllegalArgumentException("workers must be > 0");
        }

        this.workers = new ArrayList<>(workers);
        IntStream.range(0, workers).forEach(_ -> {
            Thread worker = new Thread(this::lifeCycle);
            this.workers.add(worker);
            worker.start();
        });
    }

    /**
     * Main loop executed by each worker thread.
     *
     * <p>The thread repeatedly takes a task from the queue and executes it.
     * It waits if the queue is empty and the mapper is not closed.
     * The thread exits when the queue is empty and the mapper is closed,
     * or when it is interrupted while waiting and the mapper is closed.
     */
    private void lifeCycle() {
        while (true) {
            final Task<?, ?> task;
            synchronized (monitor) {
                while (tasks.isEmpty() && !closed) {
                    try {
                        monitor.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        if (closed) {
                            return;
                        }
                    }
                }

                if (tasks.isEmpty()) {
                    return;
                }

                task = tasks.removeFirst();
            }

            task.execute();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>If a {@code ParallelMapper} was supplied to the constructor, it is used to execute
     * the block computations; otherwise, a new thread is created for each block.
     *
     * @param f    the function to apply to each element
     * @param args the list of arguments
     * @param <T>  the type of input elements
     * @param <R>  the type of output elements
     * @return the list of results in the same order as the input
     * @throws InterruptedException     if the calling thread is interrupted while waiting
     * @throws IllegalStateException    if the mapper has already been closed
     */

    @Override
    public <T, R> List<R> map(final Function<? super T, ? extends R> f, final List<? extends T> args)
            throws InterruptedException {
        if (args.isEmpty()) {
            return Collections.emptyList();
        }

        final List<Task<T, R>> tasks = new ArrayList<>(args.size());

        synchronized (monitor) {
            if (closed) {
                throw new IllegalStateException("ParallelMapperImpl is closed");
            }

            for (final T arg : args) {
                final Task<T, R> task = new Task<>(f, arg);
                tasks.add(task);
                this.tasks.addLast(task);
            }

            monitor.notifyAll();
        }

        RuntimeException firstException = null;
        for (final Task<T, R> task : tasks) {
            task.await();
            if (task.getException() != null && firstException == null) {
                firstException = task.getException();
            }
        }

        if (firstException != null) {
            throw firstException;
        }

        return tasks.stream().map(Task::getResult).toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Closes the mapper, preventing new tasks from being submitted.
     * The method waits for all worker threads to finish processing the remaining tasks.
     * If the calling thread is interrupted while waiting, it restores the interrupt flag
     * and returns immediately (without waiting for workers to finish).
     */
    @Override
    public void close() {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            closed = true;
            monitor.notifyAll();
        }

        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}