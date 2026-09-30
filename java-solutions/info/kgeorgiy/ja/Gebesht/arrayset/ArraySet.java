package info.kgeorgiy.ja.Gebesht.arrayset;

import java.util.*;

public class ArraySet<E> extends AbstractSet<E> implements SortedSet<E> {
    private final List<E> elements;
    private final Comparator<? super E> comparator;

    private ArraySet(final List<E> elements, final Comparator<? super E> comparator) {
        this.elements = elements;
        this.comparator = comparator;
    }

    private int compare(E e1, E e2) {
        if (comparator != null) {
            return comparator.compare(e1, e2);
        }
        return Collections.reverseOrder().reversed().compare(e1, e2);
    }

    private static<E> List<E> uniqueSortedList(final Collection<? extends E> collection, final Comparator<? super E> comparator) {
        final TreeSet<E> set = new TreeSet<>(comparator);
        set.addAll(collection);
        return List.copyOf(set);
    }

    public ArraySet(final Collection<? extends E> collection,
                    final Comparator<? super E> comparator) {
        this.comparator = comparator;
        if (collection instanceof SortedSet<? extends E> sorted &&
                Objects.equals(sorted.comparator(), comparator)) {
            this.elements = List.copyOf(sorted);
        } else {
            this.elements = uniqueSortedList(collection, this::compare);
        }
    }

    public ArraySet() {
        this(Collections.emptyList(), null);
    }

    public ArraySet(final Comparator<? super E> comparator) {
        this(Collections.emptyList(), comparator);
    }

    public ArraySet(final Collection<? extends E> collection) {
        this(collection, null);
    }

    @Override
    public Iterator<E> iterator() {
        return elements.iterator();
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(final Object o) {
        try {
            return Collections.binarySearch(elements, (E) o, this::compare) >= 0;
        } catch (ClassCastException | NullPointerException e) {
            return false;
        }
    }

    @Override
    public E first() {
        if (isEmpty()) {
            throw new NoSuchElementException("Attempt to get first element from empty set");
        }
        return elements.getFirst();
    }

    @Override
    public E last() {
        if (isEmpty()) {
            throw new NoSuchElementException("Attempt to get last element from empty set");
        }
        return elements.getLast();
    }

    private int lowerBound(final E e) {
        final int index = Collections.binarySearch(elements, e, this::compare);
        return index >= 0 ? index : -index - 1;
    }

    private SortedSet<E> view(int fromIndex, int toIndex) {
        return new ArraySet<>(elements.subList(fromIndex, toIndex), comparator);
    }

    @Override
    public SortedSet<E> subSet(final E fromElement, final E toElement) {
        if (compare(fromElement, toElement) > 0) {
            throw new IllegalArgumentException("fromElement > toElement: " + fromElement + " > " + toElement);
        }
        return view(lowerBound(fromElement), lowerBound(toElement));
    }

    @Override
    public SortedSet<E> headSet(final E toElement) {
        return view(0, lowerBound(toElement));
    }

    @Override
    public SortedSet<E> tailSet(final E fromElement) {
        return view(lowerBound(fromElement), size());
    }

    @Override
    public Comparator<? super E> comparator() {
        return comparator;
    }
}
