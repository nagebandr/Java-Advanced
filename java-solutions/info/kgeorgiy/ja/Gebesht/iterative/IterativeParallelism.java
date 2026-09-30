package info.kgeorgiy.ja.Gebesht.iterative;

import info.kgeorgiy.java.advanced.iterative.NewScalarIP;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

/**
 * Parallel implementation of the {@link NewScalarIP} interface.
 * Provides methods for parallel searching, indexing, and aggregation on lists
 * using a configurable number of threads and an optional step.
 *
 * <p>This class can work either with a custom {@link ParallelMapper} instance
 * (for more efficient task distribution) or with a simple thread-per-block approach
 * when no mapper is supplied.
 *
 * @author Andrey Gebesht
 * @see NewScalarIP
 * @see ParallelMapper
 */
public final class IterativeParallelism implements NewScalarIP {
    private final ParallelMapper mapper;

    /**
     * Constructs a new {@code IterativeParallelism} instance that uses a simple
     * thread-per-block execution model (no external {@code ParallelMapper}).
     */
    public IterativeParallelism() {
        this.mapper = null;
    }

    /**
     * Constructs a new {@code IterativeParallelism} instance that delegates
     * parallel task execution to the given {@link ParallelMapper}.
     *
     * @param mapper the parallel mapper to use for executing blocks in parallel
     */
    public IterativeParallelism(final ParallelMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Executes a block of work on each part of the list and collects the results.
     *
     * @param parts     list of {@link StepListView} blocks
     * @param calcBlock function that computes a value for a single block
     * @param mapper    optional {@code ParallelMapper} (may be {@code null})
     * @param <T>       element type of the original list
     * @param <A>       result type of block computation
     * @return list of {@link BlockResult} containing block results and their offsets
     * @throws InterruptedException if the execution is interrupted
     */
    private static <T, A> List<BlockResult<A>> calculateBlocks(
            final List<StepListView<T>> parts,
            final Function<StepListView<T>, A> calcBlock,
            final ParallelMapper mapper
    ) throws InterruptedException {
        final int blocks = parts.size();

        final List<A> values;
        if (mapper != null) {
            values = mapper.map(calcBlock, parts);
        } else {
            values = new ArrayList<>(Collections.nCopies(blocks, null));
            final List<Thread> threads = IntStream.range(0, blocks)
                    .mapToObj(i -> new Thread(() -> values.set(i, calcBlock.apply(parts.get(i)))))
                    .peek(Thread::start)
                    .toList();

            try {
                for (final Thread thread : threads) {
                    thread.join();
                }
            } catch (final InterruptedException e) {
                threads.forEach(Thread::interrupt);
                throw e;
            }
        }

        return IntStream.range(0, blocks)
                .mapToObj(i -> new BlockResult<>(values.get(i), parts.get(i).offset()))
                .toList();
    }

    /**
     * Core method that orchestrates parallel computation: splits the list into blocks,
     * processes each block in parallel, and combines the results.
     *
     * @param threads   number of threads to use (maximum block count)
     * @param list      the input list
     * @param step      step between elements to consider
     * @param calcBlock function that processes a single block
     * @param calcResult function that combines block results into a final answer
     * @param ifEmpty   supplier that provides the result when the list is empty
     * @param <T>       element type of the list
     * @param <A>       intermediate block result type
     * @param <V>       final result type
     * @return the combined result
     * @throws InterruptedException if the execution is interrupted
     * @throws IllegalArgumentException if {@code threads <= 0} or {@code step <= 0}
     */
    private <T, A, V> V basicCalculate(
            final int threads,
            final List<T> list,
            final int step,
            final Function<StepListView<T>, A> calcBlock,
            final Function<List<BlockResult<A>>, V> calcResult,
            final Supplier<V> ifEmpty
    ) throws InterruptedException {
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be > 0");
        }
        if (step <= 0) {
            throw new IllegalArgumentException("step must be > 0");
        }
        if (list.isEmpty()) {
            return ifEmpty.get();
        }

        final List<StepListView<T>> parts = split(threads, list, step);
        final List<BlockResult<A>> results = calculateBlocks(parts, calcBlock, mapper);
        return calcResult.apply(results);
    }

    /**
     * Holds an accumulated value and its offset within the original list.
     *
     * @param acc    the accumulated result for a block
     * @param offset the start index (offset) of the block in the original list
     * @param <A>    the type of the accumulated value
     */
    private record BlockResult<A>(A acc, int offset) {}

    /**
     * Splits a list into several {@link StepListView} blocks, each covering a contiguous range
     * but taking only every {@code step}-th element.
     *
     * @param threads the maximum number of threads to use (actual number of blocks may be smaller)
     * @param list    the original list
     * @param step    the step between elements to consider
     * @param <T>     the type of elements in the list
     * @return a list of {@code StepListView} objects representing the blocks
     */
    private static <T> List<StepListView<T>> split(
            final int threads,
            final List<T> list,
            final int step
    ) {
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        final int n = list.size();
        final int cnt = Math.min(threads, n);
        final int block = n / cnt;
        final int rem = n % cnt;

        return IntStream.range(0, cnt)
                .mapToObj(i -> {
                    final int start = i * block + Math.min(i, rem);
                    final int size = block + (i < rem ? 1 : 0);
                    final int end = start + size;
                    return new StepListView<>(list, start, end, step);
                })
                .collect(Collectors.toList());
    }

    /**
     * Helper that finds the index of the maximum element among several block results.
     *
     * @param results      list of block results, each containing a candidate index (relative to block) and an offset
     * @param list         the original list (used to compare elements)
     * @param comparator   comparator for the list elements
     * @param extractArg   function that extracts the absolute index candidate from a block result and its block index
     * @param <T>          element type of the list
     * @param <U>          type of the block result
     * @return the absolute index of the maximum element
     * @throws NoSuchElementException if no element is available
     */
    private static <T, U> int argMax(
            final List<U> results,
            final List<T> list,
            final Comparator<? super T> comparator,
            final BiFunction<U, Integer, Integer> extractArg
    ) {
        return IntStream.range(0, results.size())
                .map(i -> extractArg.apply(results.get(i), i))
                .boxed()
                .max(Comparator.comparing(list::get, comparator))
                .orElseThrow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> int argMax(
            final int threads,
            final List<T> list,
            final Comparator<? super T> comparator,
            final int step
    ) throws InterruptedException {
        return basicCalculate(
                threads,
                list,
                step,
                block -> {
                    if (block.isEmpty()) return null;
                    return argMax(block, block, comparator, (_, idx) -> idx);
                },
                results -> {
                    List<BlockResult<Integer>> nonNull = results.stream()
                            .filter(Objects::nonNull)
                            .filter(r -> r.acc() != null)
                            .toList();
                    if (nonNull.isEmpty()) throw new NoSuchElementException();
                    return argMax(nonNull, list, comparator,
                            (result, _) -> result.acc() * step + result.offset());
                },
                () -> { throw new NoSuchElementException(); }
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> int argMax(
            final int threads,
            final List<T> list,
            final Comparator<? super T> comparator
    ) throws InterruptedException {
        return argMax(threads, list, comparator, 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> int argMin(
            final int threads,
            final List<T> list,
            final Comparator<? super T> comparator,
            final int step
    ) throws InterruptedException {
        return argMax(threads, list, comparator.reversed(), step);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> int argMin(
            final int threads,
            final List<T> list,
            final Comparator<? super T> comparator
    ) throws InterruptedException {
        return argMin(threads, list, comparator, 1);
    }

    /**
     * Searches for an index satisfying a predicate in a list, optionally from the end.
     *
     * @param list      the list to search
     * @param predicate the predicate to test elements
     * @param calc      function to transform the element and its index into a return value (usually the index itself)
     * @param first     if {@code true} searches from the beginning, otherwise from the end
     * @param <T>       element type
     * @return the first (or last) index that satisfies the predicate, or -1 if none found
     */
    private static <T> int findIndex(
            final List<T> list,
            final Predicate<? super T> predicate,
            final BiFunction<T, Integer, Integer> calc,
            final boolean first
    ) {
        IntStream indices = IntStream.range(0, list.size());
        if (!first) {
            indices = indices.map(i -> list.size() - 1 - i);
        }
        return indices.filter(i -> predicate.test(list.get(i)))
                .map(i -> calc.apply(list.get(i), i))
                .findFirst()
                .orElse(-1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> int indexOf(
            final int threads,
            final List<T> list,
            final Predicate<? super T> predicate,
            final int step
    ) throws InterruptedException {
        return basicCalculate(
                threads,
                list,
                step,
                block -> findIndex(block, predicate, (_, idx) -> idx, true),
                results -> findIndex(
                        results,
                        result -> result.acc() != -1,
                        (result, _) -> result.acc() * step + result.offset(),
                        true),
                () -> -1
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> int indexOf(
            final int threads,
            final List<T> list,
            final Predicate<? super T> predicate
    ) throws InterruptedException {
        return indexOf(threads, list, predicate, 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> int lastIndexOf(
            final int threads,
            final List<T> list,
            final Predicate<? super T> predicate,
            final int step
    ) throws InterruptedException {
        return basicCalculate(
                threads,
                list,
                step,
                block -> findIndex(block, predicate, (_, idx) -> idx, false),
                results -> findIndex(
                        results,
                        result -> result.acc() != -1,
                        (result, _) -> result.acc() * step + result.offset(),
                        false),
                () -> -1
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> int lastIndexOf(
            final int threads,
            final List<T> list,
            final Predicate<? super T> predicate
    ) throws InterruptedException {
        return lastIndexOf(threads, list, predicate, 1);
    }

    /**
     * Helper record that holds the count and sum of indices in a block.
     *
     * @param count number of elements satisfying the predicate
     * @param sum   sum of their relative indices within the block
     */
    private record BlockSum(long count, long sum) {}

    /**
     * Computes the count and sum of indices (relative to the block) of elements that satisfy a predicate.
     *
     * @param block     the block view (list) to process
     * @param predicate predicate to test elements
     * @param <T>       element type
     * @return a {@code BlockSum} containing the count and the sum of indices
     */
    private static <T> BlockSum sumIndices(
            final List<? extends T> block,
            final Predicate<? super T> predicate
    ) {
        long[] result = IntStream.range(0, block.size())
                .filter(i -> predicate.test(block.get(i)))
                .collect(
                        () -> new long[2],
                        (a, i) -> { a[0]++; a[1] += i; },
                        (a, b) -> { a[0] += b[0]; a[1] += b[1]; }
                );
        return new BlockSum(result[0], result[1]);
    }

    /**
     * Combines the block results into the total sum of absolute indices.
     *
     * @param results list of block results, each containing a {@code BlockSum} and an offset
     * @param step    the step used when splitting the list
     * @return the total sum of absolute indices of elements satisfying the predicate
     */
    private static long sumIndices(
            final List<BlockResult<BlockSum>> results,
            final int step
    ) {
        return results.stream()
                .mapToLong(result -> result.acc().sum() * step + result.offset() * result.acc().count())
                .sum();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> long sumIndices(
            final int threads,
            final List<? extends T> values,
            final Predicate<? super T> predicate,
            final int step
    ) throws InterruptedException {
        return basicCalculate(
                threads,
                values,
                step,
                block -> sumIndices(block, predicate),
                results -> sumIndices(results, step),
                () -> 0L
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> long sumIndices(
            final int threads,
            final List<? extends T> values,
            final Predicate<? super T> predicate
    ) throws InterruptedException {
        return sumIndices(threads, values, predicate, 1);
    }
}