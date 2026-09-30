package info.kgeorgiy.ja.Gebesht.streams;

import java.util.*;
import java.util.function.Consumer;

public abstract class AbstractTreeSpliterator<T, N> extends Spliterators.AbstractSpliterator<T> {
    protected final ArrayDeque<N> traversal = new ArrayDeque<>();

    protected AbstractTreeSpliterator(final ArrayDeque<N> nodes, final int additionalCharacteristics) {
        super(Long.MAX_VALUE, additionalCharacteristics | ORDERED | IMMUTABLE);
        for (N node : nodes) {
            add(node);
        }
    }

    protected AbstractTreeSpliterator(N node, final int additionalCharacteristics) {
        this(new ArrayDeque<>(List.of(node)), additionalCharacteristics);
    }

    protected void add(final N node) {
        if (node != null) {
            traversal.push(node);
        }
    }

    protected abstract boolean isLeaf(final N node);

    protected abstract void pushChildren(final N node);

    protected abstract void consume(final Consumer<? super T> action, final N node);

    @Override
    public boolean tryAdvance(final Consumer<? super T> action) {
        while (!traversal.isEmpty()) {
            final N node = traversal.pop();
            if (isLeaf(node)) {
                consume(action, node);
                return true;
            }
            pushChildren(node);
        }
        return false;
    }

    protected abstract AbstractTreeSpliterator<T, N> newSpliterator(final ArrayDeque<N> nodes);

    protected abstract ArrayDeque<N> split();

    @Override
    public AbstractTreeSpliterator<T, N> trySplit() {
        if (traversal.isEmpty()) {
            return null;
        }
        if (traversal.size() == 1) {
            while (traversal.size() == 1 && !isLeaf(traversal.peek())) {
                pushChildren(traversal.pop());
            }
            if (traversal.size() == 1) {
                return null;
            }
        }
        ArrayDeque<N> prefix = split();
        return newSpliterator(prefix);
    }
}
