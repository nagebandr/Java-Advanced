package info.kgeorgiy.ja.Gebesht.streams;

import info.kgeorgiy.java.advanced.streams.Trees;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Consumer;

public final class NaryTreeSpliterator<T> extends AbstractTreeSpliterator<T, Trees.Nary<T>> {
    private long size = 0;

    private static int calcSize(Trees.Nary<?> node) {
        if (node == null) {
            return 0;
        }
        if (node instanceof Trees.Leaf<?>) {
            return 1;
        }
        int size = 0;
        for (Trees.Nary<?> child : ((Trees.Nary.Node<?>) node).children()) {
            size += calcSize(child);
        }
        return size;
    }

    public NaryTreeSpliterator(final Trees.Nary<T> root) {
        super(root, SIZED | SUBSIZED);
        size = calcSize(root);
    }

    private NaryTreeSpliterator(final ArrayDeque<Trees.Nary<T>> nodes) {
        super(nodes, SIZED | SUBSIZED);
        for (Trees.Nary<T> node : nodes) {
            size += calcSize(node);
        }
    }

    @Override
    protected boolean isLeaf(final Trees.Nary<T> node) {
        return node instanceof Trees.Leaf<T>;
    }

    @Override
    protected void pushChildren(final Trees.Nary<T> node) {
        if (node instanceof Trees.Nary.Node<T> branch) {
            List<Trees.Nary<T>> children = branch.children();
            for (int i = children.size() - 1; i >= 0; --i) {
                add(children.get(i));
            }
        }
    }

    @Override
    protected void consume(final Consumer<? super T> action, final Trees.Nary<T> node) {
        size -= calcSize(node);
        action.accept(((Trees.Leaf<T>) node).value());
    }

    @Override
    protected ArrayDeque<Trees.Nary<T>> split() {
        ArrayDeque<Trees.Nary<T>> prefix = new ArrayDeque<>();
        long half = size / 2;
        long prefix_size = 0;
        while (prefix_size < half) {
            Trees.Nary<T> node = traversal.pop();
            prefix.addFirst(node);
            prefix_size += calcSize(node);
        }
        size -= prefix_size;
        return prefix;
    }

    @Override
    protected NaryTreeSpliterator<T> newSpliterator(final ArrayDeque<Trees.Nary<T>> nodes) {
        return new NaryTreeSpliterator<>(nodes);
    }

    @Override
    public long estimateSize() {
        return size;
    }

    @Override
    public long getExactSizeIfKnown() {
        return size;
    }
}
