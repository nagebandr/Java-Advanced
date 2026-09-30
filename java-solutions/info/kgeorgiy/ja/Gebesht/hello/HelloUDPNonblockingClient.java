package info.kgeorgiy.ja.Gebesht.hello;

import info.kgeorgiy.ja.Gebesht.common.UDPUtilities;
import info.kgeorgiy.java.advanced.hello.HelloClient;
import info.kgeorgiy.ja.Gebesht.common.NetworkUtilities;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Non‑blocking UDP client that uses a single thread and a {@link Selector}.
 * <p>
 * The client sends a fixed number of requests per thread (conceptually,
 * “threads” are emulated by request identifiers). All requests are generated upfront,
 * stored in a pending queue, and sent asynchronously when the channel becomes writable.
 * Responses are processed as they arrive. If a response does not arrive within
 * the timeout window, the corresponding request is moved back to the pending queue
 * to be retried.
 * </p>
 * <p>
 * No additional threads are created; all I/O operations are multiplexed by the
 * selector in the main loop. This implementation is analogous to
 * {@link HelloUDPClient} but uses non‑blocking I/O instead of blocking sockets
 * and thread pools.
 * </p>
 *
 * @see HelloClient
 * @see Selector
 * @see DatagramChannel
 */
public final class HelloUDPNonblockingClient implements HelloClient {

    /**
     * Starts the non‑blocking client.
     * <p>
     * Resolves the host address, opens a {@link DatagramChannel} in non‑blocking mode,
     * creates a {@link Selector}, and registers the channel for write operations.
     * A {@link RequestManager} holds all request states. The main event loop
     * ({@link #loop}) drives sending and receiving until all requests are
     * completed.
     * </p>
     *
     * @param host     server hostname or IP address
     * @param port     server port
     * @param prefix   prefix used to build request strings
     * @param requests number of requests per “thread” (identifier)
     * @param threads  number of distinct request sequences (thread ids)
     */
    @Override
    public void run(final String host, final int port, final String prefix, final int requests, final int threads) {
        final InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (final IOException e) {
            System.err.println("Cannot resolve host " + host + ": " + e.getMessage());
            return;
        }

        final InetSocketAddress target = new InetSocketAddress(address, port);
        try (DatagramChannel channel = DatagramChannel.open();
             Selector selector = Selector.open()) {
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_WRITE);
            final RequestManager manager = new RequestManager(threads, requests, prefix);
            final int bufferSize = channel.socket().getReceiveBufferSize();
            final IOState state = new IOState(bufferSize);
            loop(channel, selector, target, manager, state);
        } catch (final IOException e) {
            System.err.println("IO error: " + e.getMessage());
        }
    }

    /**
     * Main event loop that drives all I/O operations.
     * <p>
     * Repeatedly calls {@link Selector#select(long)} with a timeout.
     * If no channels are ready within the timeout, {@link RequestManager#processTimeouts()}
     * is invoked to resend requests that have been waiting too long.
     * Otherwise, the loop iterates over selected keys and dispatches
     * {@link #write} or {@link #read} accordingly.
     * </p>
     * <p>
     * The loop terminates when {@link RequestManager#finished()} returns {@code true}.
     * </p>
     *
     * @param channel  the datagram channel
     * @param selector the selector to wait on
     * @param target   server address
     * @param manager  request state manager
     * @throws IOException if an I/O error occurs
     */
    private void loop(
            final DatagramChannel channel,
            final Selector selector,
            final InetSocketAddress target,
            final RequestManager manager,
            final IOState state
    ) throws IOException {
        while (!manager.finished()) {
            final int count = selector.select(UDPUtilities.SELECT_TIMEOUT_MILLISECONDS);
            final SelectionKey selectorKey = channel.keyFor(selector);
            if (count == 0 && manager.processTimeouts() && selectorKey != null && selectorKey.isValid()) {
                selectorKey.interestOps(manager.hasPending()
                        ? SelectionKey.OP_READ | SelectionKey.OP_WRITE
                        : SelectionKey.OP_READ);
            }
            for (final SelectionKey selectedKey : selector.selectedKeys()) {
                if (!selectedKey.isValid()) {
                    continue;
                }
                if (selectedKey.isReadable()) {
                    read(channel, selectedKey, manager, state);
                }
                if (selectedKey.isWritable()) {
                    write(channel, target, selectedKey, manager, state);
                }
            }
            selector.selectedKeys().clear();
        }
    }

    /**
     * Handles a writable channel event.
     * <p>
     * Retrieves the next pending request from the manager, serialises it into a
     * {@link ByteBuffer}, and sends it via {@link DatagramChannel#send(ByteBuffer, java.net.SocketAddress)}.
     * The {@code sentTime} is recorded, and the request is moved to the “in flight” set.
     * Afterwards, the channel’s interest set is updated: if more requests remain to be sent,
     * both {@code OP_WRITE} and {@code OP_READ} are kept; otherwise only {@code OP_READ}
     * is kept to receive remaining responses.
     * </p>
     *
     * @param channel the datagram channel
     * @param target  server address
     * @param key     selection key for the channel
     * @param manager request manager
     * @throws IOException if an error occurs during send
     */
    private void write(
            final DatagramChannel channel,
            final InetSocketAddress target,
            final SelectionKey key,
            final RequestManager manager,
            final IOState state
    ) throws IOException {
        final Request next = manager.getNextPending();
        if (next != null) {
            final byte[] bytes = next.getText().getBytes(StandardCharsets.UTF_8);
            state.ensureWriteCapacity(bytes.length);
            state.writeBuffer.clear();
            state.writeBuffer.put(bytes);
            state.writeBuffer.flip();
            channel.send(state.writeBuffer, target);
            next.setTime(System.currentTimeMillis());
        }

        if (manager.hasPending()) {
            key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        } else {
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    /**
     * Handles a readable channel event.
     * <p>
     * Allocates a {@link ByteBuffer} sized to the channel’s receive buffer,
     * receives a datagram, and converts the received bytes to a string.
     * The manager then attempts to match this response against any pending request.
     * If a match is found, the request is considered successful and removed from the
     * “in flight” set; the original request string is printed to standard output.
     * </p>
     *
     * @param channel the datagram channel
     * @param key     selection key for the channel (used to update interest set)
     * @param manager request manager
     * @throws IOException if an error occurs during receive
     */
    private void read(
            final DatagramChannel channel,
            final SelectionKey key,
            final RequestManager manager,
            final IOState state
    ) throws IOException {
        state.readBuffer.clear();
        final InetSocketAddress sender = (InetSocketAddress) channel.receive(state.readBuffer);
        if (sender != null) {
            state.readBuffer.flip();
            final String response = NetworkUtilities.bufferToString(state.readBuffer);
            if (manager.processResponse(response)) {
                key.interestOps(manager.hasPending()
                        ? SelectionKey.OP_READ | SelectionKey.OP_WRITE
                        : SelectionKey.OP_READ);
            }
        }
    }

    /**
     * Reusable I/O buffers for the single selector thread.
     */
    private static final class IOState {
        private final ByteBuffer readBuffer;
        private ByteBuffer writeBuffer;

        private IOState(final int initialCapacity) {
            readBuffer = ByteBuffer.allocate(initialCapacity);
            writeBuffer = ByteBuffer.allocate(initialCapacity);
        }

        private void ensureWriteCapacity(final int size) {
            if (writeBuffer.capacity() < size) {
                writeBuffer = ByteBuffer.allocate(size);
            }
        }
    }

    /**
     * Manages the lifecycle of all requests.
     * <p>
     * Stores two collections:
     * <ul>
     *   <li>{@code pending} – requests that have not been sent yet (or need retransmission);</li>
     *   <li>{@code waiting} – requests that have been sent but for which no valid response
     *       has been received yet.</li>
     * </ul>
     * Timeout handling moves requests from {@code waiting} back to {@code pending}.
     * A response is matched only if it equals the expected string
     * (as defined by {@link UDPUtilities#formatExpectedResponse(String)}).
     * </p>
     * <p>
     * The manager is not thread‑safe; it is used exclusively from the single selector thread.
     * </p>
     */
    private static class RequestManager {
        private final List<Task> tasks = new ArrayList<>();

        /**
         * Generates all request strings for the given parameters.
         *
         * @param threads  number of logical threads (1‑based identifier)
         * @param requests number of requests per logical thread
         * @param prefix   request string prefix
         */
        RequestManager(final int threads, final int requests, final String prefix) {
            IntStream.rangeClosed(1, threads)
                    .mapToObj(thread -> new Task(thread, requests, prefix))
                    .forEach(this.tasks::add);
        }

        /**
         * Retrieves and removes the next pending request.
         * <p>
         * Scans logical threads in order and returns the first request that is ready to be sent.
         * A request is ready if the thread still has requests to send and is not currently
         * waiting for a response (i.e., no in‑flight request).
         * </p>
         *
         * @return the next request state, or {@code null} if none are pending
         */
        Request getNextPending() {
            for (final Task task : tasks) {
                if (task.canSend()) {
                    return task.send();
                }
            }
            return null;
        }

        /**
         * Checks all in‑flight requests for timeout and moves expired ones back to pending.
         * <p>
         * A request is considered expired if the time elapsed since
         * {@link Request#getTime()} exceeds {@link UDPUtilities#SOCKET_TIMEOUT_MILLISECONDS}.
         * </p>
         *
         * @return {@code true} if at least one request timed out and was reset,
         *         {@code false} otherwise
         */
        boolean processTimeouts() {
            final long now = System.currentTimeMillis();
            boolean changed = false;
            for (final Task task :tasks) {
                changed |= task.processTimeout(now);
            }
            return changed;
        }

        /**
         * Tries to match a received response to an in‑flight request.
         * <p>
         * For each logical thread, if it has a current in‑flight request and the response
         * equals the expected response (as defined by
         * {@link UDPUtilities#formatExpectedResponse(String)}), the request is marked as
         * successfully completed and the thread moves to its next request.
         * </p>
         *
         * @param response the received response string
         * @return {@code true} if the response was successfully matched to some request,
         *         {@code false} otherwise
         */
        boolean processResponse(final String response) {
            for (final Task task : tasks) {
                if (task.processResponse(response)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Indicates whether there are still requests to send (including those that are
         * waiting for retransmission).
         *
         * @return {@code true} if at least one logical thread has a pending request
         *         (either not sent yet or expired and waiting to be resent),
         *         {@code false} otherwise
         */
        boolean hasPending() {
            for (final Task thread : tasks) {
                if (thread.canSend()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Indicates whether all requests from all logical threads have been successfully completed.
         *
         * @return {@code true} if every thread has finished all its requests and no thread
         *         is currently waiting for a response, {@code false} otherwise
         */
        boolean finished() {
            for (final Task task : tasks) {
                if (!task.finished()) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * State of one logical client thread.
     * <p>
     * Each logical thread maintains its own sequence of request numbers (from 1 to {@code requests}).
     * It holds at most one outstanding request at any time (the {@code current} field).
     * When a response matches, the thread advances to the next request number.
     * If a timeout occurs, the current request is resent without advancing the sequence.
     * </p>
     */
    private static final class Task {
        private final int threadId;
        private final int requests;
        private final String prefix;
        private int nextRequest = 1;
        private Request current;

        /**
         * Creates a new thread state.
         *
         * @param threadId logical thread identifier (1‑based)
         * @param requests total number of requests this thread must send
         * @param prefix   request prefix
         */
        Task(final int threadId, final int requests, final String prefix) {
            this.threadId = threadId;
            this.requests = requests;
            this.prefix = prefix;
        }

        /**
         * Checks whether this thread can send a new request right now.
         *
         * @return {@code true} if there are still requests to send and no outstanding
         *         request is currently waiting for a response (or the outstanding request
         *         has expired and is ready to be resent)
         */
        boolean canSend() {
            return nextRequest <= requests && (current == null || current.getTime() < 0);
        }

        /**
         * Sends the next request for this thread.
         * <p>
         * If there is no current request, it creates a new one using the current
         * {@code nextRequest} number. Then marks it as sent (updates its sent time).
         * The returned {@link Request} is the same as stored in {@code current}.
         * </p>
         *
         * @return the request state that was just sent (never {@code null})
         */
        Request send() {
            if (current == null) {
                current = new Request(UDPUtilities.formatRequest(prefix, nextRequest, threadId));
            }
            current.setTime(System.currentTimeMillis());
            return current;
        }

        /**
         * Checks if the outstanding request has timed out.
         *
         * @param now current time in milliseconds since epoch
         * @return {@code true} if there is an outstanding request and it has been waiting
         *         longer than {@link UDPUtilities#SOCKET_TIMEOUT_MILLISECONDS},
         *         and its sent time is reset to {@code -1} (so it can be resent);
         *         {@code false} otherwise
         */
        boolean processTimeout(final long now) {
            if (current != null && current.getTime() >= 0
                    && now - current.getTime() > UDPUtilities.SOCKET_TIMEOUT_MILLISECONDS) {
                current.setTime(-1);
                return true;
            }
            return false;
        }

        /**
         * Tries to match a response to the current outstanding request.
         *
         * @param response the received response string
         * @return {@code true} if there is an outstanding request and the response matches
         *         the expected response (see {@link UDPUtilities#formatExpectedResponse(String)});
         *         in that case the request is marked as completed, the thread advances
         *         to the next request number, and the current request is cleared.
         *         Returns {@code false} otherwise.
         */
        boolean processResponse(final String response) {
            if (current != null && response.equals(UDPUtilities.formatExpectedResponse(current.getText()))) {
                current = null;
                nextRequest++;
                return true;
            }
            return false;
        }

        /**
         * Checks whether this thread has finished all its requests.
         *
         * @return {@code true} if all request numbers have been processed
         *         (i.e., {@code nextRequest > requests}) and there is no outstanding request
         *         waiting for a response; {@code false} otherwise
         */
        boolean finished() {
            return nextRequest > requests && current == null;
        }
    }

    /**
     * Holds the state of a single request.
     * <p>
     * Stores the request string (the actual data to send) and the timestamp
     * when it was last transmitted. A sent time of {@code -1} indicates that
     * the request has not been sent yet or has been reset due to a timeout.
     * </p>
     */
    private static final class Request {
        private final String text;
        private long time = -1;

        /**
         * Constructs a new request state.
         *
         * @param text request string
         */
        Request(final String text) {
            this.text = text;
        }

        /**
         * Returns the original request string.
         *
         * @return the request text
         */
        public String getText() {
            return text;
        }

        /**
         * Returns the timestamp of when the request was last sent, or {@code -1} if it has never been sent.
         *
         * @return the sent time in milliseconds since epoch, or {@code -1} if not sent yet
         */
        public long getTime() {
            return time;
        }

        /**
         * Sets the timestamp of when the request was sent.
         *
         * @param sentTime the time in milliseconds since epoch when the request was sent
         */
        public void setTime(final long sentTime) {
            this.time = sentTime;
        }
    }

    /**
     * Command‑line entry point.
     * <p>
     * Expects arguments: {@code host port prefix requests threads}.
     * Parses them using {@link UDPUtilities#parseClientArgs(String[])} and
     * invokes {@link #run(String, int, String, int, int)}.
     * </p>
     *
     * @param args command line arguments
     */
    static void main(final String[] args) {
        UDPUtilities.runClientMain(args, new HelloUDPNonblockingClient());
    }
}