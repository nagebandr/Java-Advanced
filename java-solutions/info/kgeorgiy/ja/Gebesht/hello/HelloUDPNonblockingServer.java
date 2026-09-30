package info.kgeorgiy.ja.Gebesht.hello;

import info.kgeorgiy.ja.Gebesht.common.UDPUtilities;
import info.kgeorgiy.java.advanced.hello.NewHelloServer;
import info.kgeorgiy.ja.Gebesht.common.NetworkUtilities;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Non‑blocking UDP server that handles multiple ports concurrently in a single thread.
 * <p>
 * The server uses Java NIO {@link DatagramChannel} and a {@link Selector} to multiplex
 * I/O operations. All channels are registered for read events initially.
 * When a datagram is received, the request is decoded, a response string is generated
 * using the per‑port format, and a response packet is stored in the channel’s
 * attachment ({@link Channel}). The channel’s interest set is then switched to
 * write operation. Once the channel becomes writable, the response is sent back to
 * the client, and the interest set is restored to read.
 * </p>
 * <p>
 * No separate thread pools are created; all processing (receiving, formatting,
 * sending) happens in the single selector thread. This design avoids blocking
 * waits and makes the server scalable for many concurrent connections.
 * </p>
 * <p>
 * The server can be stopped by calling {@link #close()}, which closes all channels,
 * the selector, and interrupts the server thread.
 * </p>
 *
 * @see NewHelloServer
 * @see Selector
 * @see DatagramChannel
 */
public final class HelloUDPNonblockingServer implements NewHelloServer {

    /** Flag indicating whether the server is currently running. */
    private volatile boolean running = false;

    /** The thread that runs the main selector loop. */
    private Thread thread;

    /** The selector used to multiplex channels. */
    private Selector selector;

    /** Mapping from port number to format string for that port. */
    private Map<Integer, String> format;

    /** Set of all opened channels (used for closing them later). */
    private final Set<DatagramChannel> channels = new HashSet<>();

    /**
     * State attached to each registered channel.
     * <p>
     * Stores the channel itself and reusable request/response buffers.
     * </p>
     */
    private static final class Channel {
        /** The underlying datagram channel. */
        private final DatagramChannel channel;

        /** Reusable buffer for incoming datagrams. */
        private final ByteBuffer readBuffer;

        /** Reusable buffer for outgoing datagrams. */
        private ByteBuffer writeBuffer;

        /** Client address for the pending response, or {@code null} if none. */
        private InetSocketAddress target;

        /**
         * Creates a new channel state.
         *
         * @param channel the associated datagram channel
         * @param bufferSize initial capacity for reusable buffers
         */
        private Channel(final DatagramChannel channel, final int bufferSize) {
            this.channel = channel;
            this.readBuffer = ByteBuffer.allocate(bufferSize);
            this.writeBuffer = ByteBuffer.allocate(bufferSize);
        }

        /**
         * Returns the associated datagram channel.
         *
         * @return the associated datagram channel
         */
        private DatagramChannel getChannel() {
            return channel;
        }

        private ByteBuffer getReadBuffer() {
            return readBuffer;
        }

        private ByteBuffer getWriteBuffer() {
            return writeBuffer;
        }

        private void ensureWriteCapacity(final int size) {
            if (writeBuffer.capacity() < size) {
                writeBuffer = ByteBuffer.allocate(size);
            }
        }

        private InetSocketAddress getTarget() {
            return target;
        }

        private void setTarget(final InetSocketAddress target) {
            this.target = target;
        }
    }

    /**
     * Starts the server on multiple ports with the given number of worker threads.
     * <p>
     * This implementation ignores the {@code threads} parameter because the server
     * uses only one thread (the selector loop). The parameter is kept for interface
     * compatibility.
     * </p>
     * <p>
     * For each port in the map, the method opens a non‑blocking {@link DatagramChannel},
     * binds it to the port, and registers it with the selector for read operations.
     * The format string for that port is stored. The server thread is then started
     * as a daemon thread.
     * </p>
     *
     * @param threads number of worker threads (ignored, but must be &gt; 0)
     * @param ports   map from port number to response format string
     * @throws NullPointerException if {@code ports} is null or any format string is null
     */
    @Override
    public synchronized void start(final int threads, final Map<Integer, String> ports) {
        try {
            Objects.requireNonNull(ports, "Ports must not be null");
            if (running) {
                return;
            }

            selector = Selector.open();
            format = new HashMap<>();
            channels.clear();

            for (final Map.Entry<Integer, String> entry : ports.entrySet()) {
                Objects.requireNonNull(entry.getValue(), "Format must not be null");
                final DatagramChannel channel = DatagramChannel.open();
                try {
                    channel.configureBlocking(false);
                    channel.bind(new InetSocketAddress(entry.getKey()));
                    final int bufferSize = channel.socket().getReceiveBufferSize();
                    channel.register(selector, SelectionKey.OP_READ, new Channel(channel, bufferSize));
                    channels.add(channel);
                    format.put(entry.getKey(), entry.getValue());
                } catch (final IOException e) {
                    channel.close();
                    throw e;
                }
            }

            running = true;
            thread = new Thread(this::loop);
            thread.setDaemon(true);
            thread.start();
        } catch (final IOException e) {
            System.err.println("Cannot start server: " + e.getMessage());
            close();
        }
    }

    /**
     * Starts the server on a single port with a default format.
     * <p>
     * This method delegates to {@link NewHelloServer#start(int, int)} which
     * constructs a singleton map and calls {@link #start(int, Map)}.
     * </p>
     *
     * @param port    the port to listen on
     * @param threads number of worker threads (ignored)
     */
    @Override
    public synchronized void start(final int port, final int threads) {
        NewHelloServer.super.start(port, threads);
    }

    /**
     * Main selector loop.
     * <p>
     * Repeatedly calls {@link Selector#select(long)} with a timeout.
     * When a channel is ready for reading, {@link #read(SelectionKey)} is called.
     * When a channel is ready for writing, {@link #write(SelectionKey)} is called.
     * The loop terminates when {@link #running} becomes {@code false} or the thread
     * is interrupted.
     * </p>
     */
    private void loop() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                final int count = selector.select(UDPUtilities.SELECT_TIMEOUT_MILLISECONDS);
                if (count == 0) {
                    continue;
                }

                for (final SelectionKey key : selector.selectedKeys()) {
                    try {
                        if (key.isReadable()) {
                            read(key);
                        } else if (key.isWritable()) {
                            write(key);
                        }
                    } catch (final IOException e) {
                        System.err.println("Error handling key: " + e.getMessage());
                    }
                }
                selector.selectedKeys().clear();
            }
        } catch (final IOException e) {
            System.err.println("Selector I/O error: " + e.getMessage());
        } catch (final ClosedSelectorException e) {
            System.err.println("Selector closed: " + e.getMessage());
        } finally {
            try {
                if (selector != null && selector.isOpen()) {
                    selector.close();
                }
            } catch (final IOException e) {
                System.err.println("Error while closing selector: " + e.getMessage());
            }
        }
    }

    /**
     * Handles a read event on a channel.
     * <p>
     * Retrieves the channel state from the key’s attachment. Receives a datagram,
     * decodes the request string, looks up the format string for the channel’s port,
     * builds the response, and stores it in the channel state as a pending response.
     * Finally, changes the key’s interest set to {@code OP_WRITE}.
     * </p>
     *
     * @param key the selection key for the ready channel
     * @throws IOException if an error occurs during receive
     */
    private void read(final SelectionKey key) throws IOException {
        final Object attachment = key.attachment();
        if (!(attachment instanceof Channel channel)) {
            key.cancel();
            return;
        }
        final DatagramChannel datagramChannel = channel.getChannel();
        final int port = ((InetSocketAddress) datagramChannel.getLocalAddress()).getPort();
        final String stringFormat = format.get(port);

        final ByteBuffer readBuffer = channel.getReadBuffer();
        readBuffer.clear();
        final InetSocketAddress sender = (InetSocketAddress) datagramChannel.receive(readBuffer);

        if (sender != null) {
            readBuffer.flip();
            final String request = NetworkUtilities.bufferToString(readBuffer);
            final String response = UDPUtilities.formatServerResponse(stringFormat, request);
            final byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            channel.ensureWriteCapacity(bytes.length);
            final ByteBuffer writeBuffer = channel.getWriteBuffer();
            writeBuffer.clear();
            writeBuffer.put(bytes);
            writeBuffer.flip();
            channel.setTarget(sender);
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }

    /**
     * Handles a write event on a channel.
     * <p>
     * If a pending response exists in the channel state, it sends the response
     * payload to the target address. After sending, the pending response is cleared
     * and the key’s interest set is changed back to {@code OP_READ}.
     * </p>
     *
     * @param key the selection key for the ready channel
     * @throws IOException if an error occurs during send
     */
    private void write(final SelectionKey key) throws IOException {
        final Object attachment = key.attachment();
        if (!(attachment instanceof Channel channel)) {
            key.cancel();
            return;
        }
        final DatagramChannel datagramChannel = channel.getChannel();

        final InetSocketAddress target = channel.getTarget();
        if (target != null) {
            datagramChannel.send(channel.getWriteBuffer(), target);
            if (!channel.getWriteBuffer().hasRemaining()) {
                channel.setTarget(null);
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    /**
     * Closes the server and releases all resources.
     * <p>
     * Sets the running flag to {@code false}, closes all datagram channels,
     * closes the selector, and interrupts the server thread. Waits for the server
     * thread to finish.
     * </p>
     */
    @Override
    public synchronized void close() {
        running = false;
        for (final DatagramChannel channel : channels) {
            try {
                if (channel.isOpen()) {
                    channel.close();
                }
            } catch (final IOException e) {
                System.err.println("Error while closing channel: " + e.getMessage());
            }
        }
        channels.clear();

        if (selector != null) {
            try {
                if (selector.isOpen()) {
                    selector.close();
                }
            } catch (final IOException e) {
                System.err.println("Error while closing selector: " + e.getMessage());
            }
        }

        if (thread != null) {
            thread.interrupt();
            try {
                thread.join();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Command‑line entry point for the server.
     * <p>
     * Expected arguments: {@code <port> <threads>}. The format string is assumed to be
     * {@code "Hello, %s"} (default behavior). The server starts and then waits
     * indefinitely. A shutdown hook is added to ensure proper closing on VM termination.
     * </p>
     *
     * @param args command line arguments
     */
    static void main(final String[] args) {
        UDPUtilities.runServerMain(args, HelloUDPNonblockingServer::new);
    }
}