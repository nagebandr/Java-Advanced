package info.kgeorgiy.ja.Gebesht.streams;

import info.kgeorgiy.java.advanced.streams.Trees;

import java.util.ArrayDeque;
import java.util.function.Consumer;

public final class BinaryTreeSpliterator<T> extends AbstractTreeSpliterator<T, Trees.Binary<T>> {
    private long size = 0;

    private static int calcSize(final Trees.Binary<?> node) {
        if (node == null) {
            return 0;
        }
        if (node instanceof Trees.Leaf<?>) {
            return 1;
        }
        final Trees.Binary.Branch<?> branch = (Trees.Binary.Branch<?>) node;
        return calcSize(branch.left()) + calcSize(branch.right());
    }

    public BinaryTreeSpliterator(final Trees.Binary<T> root) {
        super(root, SIZED | SUBSIZED);
        size = calcSize(root);
    }

    public BinaryTreeSpliterator(final ArrayDeque<Trees.Binary<T>> nodes) {
        super(nodes, SIZED | SUBSIZED);
        for (Trees.Binary<T> node : nodes) {
            size += calcSize(node);
        }
    }

    @Override
    protected boolean isLeaf(final Trees.Binary<T> node) {
        return node instanceof Trees.Leaf<T>;
    }

    @Override
    protected void pushChildren(final Trees.Binary<T> node) {
        if (node instanceof Trees.Binary.Branch<T> branch) {
            add(branch.right());
            add(branch.left());
        }
    }

    @Override
    protected void consume(final Consumer<? super T> action, final Trees.Binary<T> node) {
        size -= calcSize(node);
        action.accept(((Trees.Leaf<T>) node).value());
    }

    @Override
    protected ArrayDeque<Trees.Binary<T>> split() {
        final ArrayDeque<Trees.Binary<T>> prefix = new ArrayDeque<>();
        final long half = size / 2;
        long prefixSize = 0;
        while (prefixSize < half && !traversal.isEmpty()) {
            final Trees.Binary<T> node = traversal.pop();
            prefix.addFirst(node);
            prefixSize += calcSize(node);
        }
        size -= prefixSize;
        return prefix;
    }

    @Override
    protected BinaryTreeSpliterator<T> newSpliterator(final ArrayDeque<Trees.Binary<T>> nodes) {
        return new BinaryTreeSpliterator<>(nodes);
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
