package info.kgeorgiy.ja.Gebesht.streams;

import info.kgeorgiy.java.advanced.streams.Trees;

import java.util.ArrayDeque;
import java.util.function.Consumer;

public final class SizedBinaryTreeSpliterator<T> extends AbstractTreeSpliterator<T, Trees.SizedBinary<T>> {
    private long size = 0;

    public SizedBinaryTreeSpliterator(final Trees.SizedBinary<T> root) {
        super(root, SIZED | SUBSIZED);
        if (root != null) {
            size = root.size();
        }
    }

    private SizedBinaryTreeSpliterator(final ArrayDeque<Trees.SizedBinary<T>> nodes) {
        super(nodes, SIZED | SUBSIZED);
        for (Trees.SizedBinary<T> node : nodes) {
            if (node != null) {
                size += node.size();
            }
        }
    }

    @Override
    protected boolean isLeaf(final Trees.SizedBinary<T> node) {
        return node instanceof Trees.Leaf<T>;
    }

    @Override
    protected void pushChildren(final Trees.SizedBinary<T> node) {
        if (node instanceof Trees.SizedBinary.Branch<T> branch) {
            add(branch.right());
            add(branch.left());
        }
    }

    @Override
    protected void consume(final Consumer<? super T> action, final Trees.SizedBinary<T> node) {
        size -= node.size();
        action.accept(((Trees.Leaf<T>) node).value());
    }

    @Override
    protected ArrayDeque<Trees.SizedBinary<T>> split() {
        ArrayDeque<Trees.SizedBinary<T>> prefix = new ArrayDeque<>();
        long half = size / 2;
        long prefix_size = 0;
        while (prefix_size < half) {
            Trees.SizedBinary<T> node = traversal.pop();
            prefix.addFirst(node);
            prefix_size += node.size();
        }
        size -= prefix_size;
        return prefix;
    }

    @Override
    protected SizedBinaryTreeSpliterator<T> newSpliterator(final ArrayDeque<Trees.SizedBinary<T>> nodes) {
        return new SizedBinaryTreeSpliterator<>(nodes);
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
