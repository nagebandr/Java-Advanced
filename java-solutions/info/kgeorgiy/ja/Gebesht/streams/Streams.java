package info.kgeorgiy.ja.Gebesht.streams;

import info.kgeorgiy.java.advanced.streams.*;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Gatherer;

public class Streams implements EasyStreams {

    @Override
    public <T> Spliterator<T> binaryTreeSpliterator(Trees.Binary<T> binary) {
        return new BinaryTreeSpliterator<>(binary);
    }

    @Override
    public <T> Spliterator<T> sizedBinaryTreeSpliterator(Trees.SizedBinary<T> sizedBinary) {
        return new SizedBinaryTreeSpliterator<>(sizedBinary);
    }

    @Override
    public <T> Spliterator<T> naryTreeSpliterator(Trees.Nary<T> nary) {
        return new NaryTreeSpliterator<>(nary);
    }

    @Override
    public <T> Collector<T, ?, Optional<T>> first() {
        return Collectors.reducing((a, b) -> a);
    }

    @Override
    public <T> Collector<T, ?, Optional<T>> last() {
        return Collectors.reducing((a, b) -> b);
    }

    @Override
    public <T> Collector<T, ?, Optional<T>> middle() {
        return Collector.of(
                ArrayList<T>::new,
                List::add,
                (a, b) -> {
                    a.addAll(b);
                    return a;
                },
                list -> {
                    if (list.isEmpty()) {
                        return Optional.empty();
                    }
                    return Optional.of(list.get(list.size() / 2));
                }
        );
    }

    private static Collector<CharSequence, ?, String> common(
            java.util.function.BinaryOperator<String> op
    ) {
        return Collectors.collectingAndThen(
                Collectors.mapping(CharSequence::toString,
                        Collectors.reducing(op)),
                opt -> opt.orElse("")
        );
    }

    @Override
    public Collector<CharSequence, ?, String> commonPrefix() {
        return common(Streams::commonPrefix);
    }

    private static String commonPrefix(String a, String b) {
        int len = Math.min(a.length(), b.length());
        int i = 0;
        while (i < len && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return a.substring(0, i);
    }

    @Override
    public Collector<CharSequence, ?, String> commonSuffix() {
        return common(Streams::commonSuffix);
    }

    private static String commonSuffix(String a, String b) {
        int len = Math.min(a.length(), b.length());
        int i = 0;
        while (i < len &&
                a.charAt(a.length() - 1 - i) == b.charAt(b.length() - 1 - i)) {
            i++;
        }
        return a.substring(a.length() - i);
    }

    @Override
    public Gatherer<CharSequence, ?, CharSequence> stringPrefixes() {
        return Gatherer.of(
                (_, element, downstream) -> {
                    String s = element.toString();
                    for (int i = 1; i <= s.length(); i++) {
                        if (!downstream.push(s.substring(0, i))) {
                            return false;
                        }
                    }
                    return true;
                }
        );
    }

    @Override
    public <T> Gatherer<T, ?, T> nth(int n) {
        class State {
            int index = 0;
        }

        return Gatherer.ofSequential(
                State::new,
                (state, element, downstream) -> {
                    state.index++;
                    if (state.index % n == 0) {
                        return downstream.push(element);
                    }
                    return true;
                }
        );
    }

    @Override
    public <T> Gatherer<T, ?, T> distinctPrefix() {
        class State {
            final Set<T> seen = new HashSet<>();
            boolean done = false;
        }

        return Gatherer.ofSequential(
                State::new,
                (state, element, downstream) -> {
                    if (state.done) {
                        return false;
                    }

                    if (state.seen.contains(element)) {
                        state.done = true;
                        return false;
                    }

                    state.seen.add(element);
                    return downstream.push(element);
                }
        );
    }
}