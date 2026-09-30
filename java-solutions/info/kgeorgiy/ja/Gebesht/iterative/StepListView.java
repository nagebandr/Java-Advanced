package info.kgeorgiy.ja.Gebesht.iterative;

import java.util.*;

/**
 * A read-only view of a list that presents every {@code step}-th element
 * from a specified contiguous range {@code [from, to)}.
 * <p>
 * The view adapts the original list so that:
 * <ul>
 *   <li>Element {@code 0} of the view corresponds to the smallest index in
 *       {@code [from, to)} that is congruent to {@code from} modulo {@code step}.</li>
 *   <li>Element {@code i} corresponds to {@code start + i * step}.</li>
 *   <li>The view is empty if no such element exists.</li>
 * </ul>
 *
 * @param <T> the type of elements in the underlying list
 */
public final class StepListView<T> extends AbstractList<T> {

    private final List<T> list;
    private final int step;
    private final int start;
    private final int size;

    /**
     * Constructs a step view over a portion of the original list.
     *
     * @param list the underlying list (must not be null)
     * @param from the start index (inclusive) of the range in the original list
     * @param to   the end index (exclusive) of the range in the original list
     * @param step the step between selected elements (must be > 0)
     * @throws IllegalArgumentException if {@code step <= 0}
     * @throws IndexOutOfBoundsException if {@code from} or {@code to} are out of bounds
     *                                   (checked by {@link Objects#checkFromToIndex})
     */
    public StepListView(List<T> list, final int from, final int to, final int step) {
        if (step <= 0) {
            throw new IllegalArgumentException("step must be > 0");
        }
        Objects.checkFromToIndex(from, to, list.size());
        this.list = Objects.requireNonNull(list);
        this.step = step;
        start = ((from + step - 1) / step) * step;
        size = start >= to ? 0 : (to - start + step - 1) / step;
    }

    /**
     * Returns the element at the specified position in this view.
     *
     * @param idx index of the element to return (0‑based in this view)
     * @return the element at position {@code idx}
     * @throws IndexOutOfBoundsException if {@code idx} is out of range
     *         ({@code idx < 0 || idx >= size()})
     */
    @Override
    public T get(final int idx) {
        Objects.checkIndex(idx, size);
        return list.get(start + idx * step);
    }

    /**
     * Returns the number of elements in this view.
     *
     * @return the size of the view
     */
    @Override
    public int size() {
        return size;
    }

    /**
     * Returns the absolute index in the original list of the first element of this view.
     *
     * @return the start offset in the original list
     */
    public int offset() {
        return start;
    }
}