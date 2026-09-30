package info.kgeorgiy.ja.Gebesht.hello;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

import info.kgeorgiy.ja.Gebesht.common.*;
import info.kgeorgiy.java.advanced.hello.NewHelloServer;

/**
 * Blocking UDP server that handles multiple ports concurrently.
 * <p>
 * For each port specified in the configuration, the server opens a separate
 * {@link DatagramSocket} and dedicates one receiver thread to that socket.
 * The receiver thread reads incoming datagrams and submits a response task
 * to a shared worker thread pool. This design isolates the blocking receive
 * operations while allowing response processing (which is typically fast)
 * to be parallelised.
 * </p>
 * <p>
 * The server expects each request to be a string. It replies with a message
 * constructed using the per‑port format string and the original request text:
 * {@code Hello, <request>} by default, but can be customised via the format.
 * </p>
 * <p>
 * The server can be stopped by calling {@link #close()} which closes all sockets
 * and shuts down both the receiver and worker thread pools.
 * </p>
 *
 * @see NewHelloServer
 * @see DatagramSocket
 * @see UDPUtilities
 */
public final class HelloUDPServer implements NewHelloServer {

    /** Sockets mapped to their corresponding format strings (one per port). */
    private Map<DatagramSocket, String> sockets;

    /** Thread pool for processing responses (sending reply packets). */
    private ExecutorService workers;

    /** Thread pool for receivers (one thread per open socket). */
    private ExecutorService receivers;

    /**
     * Starts the server on multiple ports.
     * <p>
     * For each entry in the {@code ports} map, this method opens a new
     * {@link DatagramSocket} on the specified port and associates it with
     * the given format string. A receiver thread is then started for each socket.
     * The worker pool size is fixed to {@code threads} and is shared by all
     * receiver threads.
     * </p>
     * <p>
     * If an error occurs while opening any socket (e.g. the port is already in use),
     * the method prints an error message and calls {@link #close()} to release
     * any already opened resources.
     * </p>
     *
     * @param threads number of worker threads for sending responses
     * @param ports   map from port number to format string (the format string
     *                is used to build the reply, typically {@code "Hello, %s"}).
     * @throws NullPointerException if {@code ports} is null or any format string is null
     */
    @Override
    public synchronized void start(final int threads, final Map<Integer, String> ports) {
        try {
            Objects.requireNonNull(ports, "Ports must not be null");
            final Map<DatagramSocket, String> openedSockets = new HashMap<>();
            for (final Map.Entry<Integer, String> entry : ports.entrySet()) {
                Objects.requireNonNull(entry.getValue(), "Format must not be null");
                openedSockets.put(new DatagramSocket(entry.getKey()), entry.getValue());
            }

            sockets = openedSockets;
            workers = Executors.newFixedThreadPool(threads);
            if (!openedSockets.isEmpty()) {
                receivers = Executors.newFixedThreadPool(openedSockets.size());
                for (final Map.Entry<DatagramSocket, String> entry : openedSockets.entrySet()) {
                    receivers.submit(() -> receive(entry.getKey(), entry.getValue()));
                }
            }
        } catch (final SocketException e) {
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
     * @param threads number of worker threads
     */
    @Override
    public synchronized void start(final int port, final int threads) {
        NewHelloServer.super.start(port, threads);
    }

    /**
     * Continuously receives datagrams on a specific socket.
     * <p>
     * This method runs in a dedicated receiver thread. It allocates a buffer
     * sized to the socket's receive buffer capacity and reuses a single
     * {@link DatagramPacket} for receiving. For each incoming request, it
     * extracts the payload and the client address, then submits a task to the
     * worker pool to send the response.
     * </p>
     * <p>
     * The method terminates when the socket is closed or the thread is interrupted.
     * </p>
     *
     * @param socket the UDP socket to read from
     * @param format the response format string (e.g. {@code "Hello, %s"})
     */
    private void receive(final DatagramSocket socket, final String format) {
        final int size;
        try {
            size = socket.getReceiveBufferSize();
        } catch (final SocketException e) {
            System.err.println("Cannot receive buffer size: " + e.getMessage());
            return;
        }

        final byte[] receiveBuffer = new byte[size];
        final DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);

        while (!socket.isClosed() && !Thread.currentThread().isInterrupted()) {
            try {
                packet.setLength(size);
                socket.receive(packet);

                final byte[] requestBytes = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), requestBytes, 0, packet.getLength());
                final SocketAddress address = packet.getSocketAddress();

                workers.submit(() -> sendResponse(socket, format, requestBytes, address));
            } catch (final IOException e) {
                if (!socket.isClosed()) {
                    System.err.println("Receive error: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Sends a response back to the client.
     * <p>
     * The request bytes are decoded as UTF‑8. A response string is constructed using
     * {@link UDPUtilities#formatServerResponse(String, String)} (which typically produces
     * {@code "Hello, <request>"}). The response is then sent over the same socket
     * to the client's address.
     * </p>
     * <p>
     * This method is executed by a worker thread and does not retry on failure;
     * any send error is printed to standard error.
     * </p>
     *
     * @param socket       the UDP socket used to send the reply
     * @param format       the format string for the response
     * @param requestBytes raw bytes of the received request
     * @param address      the client's socket address
     */
    private void sendResponse(
            final DatagramSocket socket,
            final String format,
            final byte[] requestBytes,
            final SocketAddress address
    ) {
        final String request = new String(requestBytes, StandardCharsets.UTF_8);
        final String response = UDPUtilities.formatServerResponse(format, request);
        final byte[] responseBytes = NetworkUtilities.stringToBytes(response);
        final DatagramPacket responsePacket = NetworkUtilities.bytesToPacket(responseBytes, address);
        try {
            socket.send(responsePacket);
        } catch (final IOException e) {
            System.err.println("Send error: " + e.getMessage());
        }
    }

    /**
     * Closes all server resources.
     * <p>
     * This method:
     * <ul>
     *   <li>Closes every open {@link DatagramSocket} (which interrupts any blocking
     *       receive operations on the receiver threads);</li>
     *   <li>Shuts down the receiver thread pool using {@link ExecutorService#close()}
     *       which waits for already submitted tasks to complete;</li>
     *   <li>Shuts down the worker thread pool similarly.</li>
     * </ul>
     * The method is idempotent – subsequent calls have no effect.
     * </p>
     */
    @Override
    public synchronized void close() {
        if (sockets != null) {
            for (final DatagramSocket socket : sockets.keySet()) {
                socket.close();
            }
            sockets = null;
        }
        if (receivers != null) {
            receivers.close();
            receivers = null;
        }
        if (workers != null) {
            workers.close();
            workers = null;
        }
    }

    /**
     * Command-line entry point for the server.
     * <p>
     * Expected arguments: {@code <port> <threads>}.
     * The format string is assumed to be {@code "Hello, %s"} (default behaviour).
     * The server starts and then waits indefinitely. A shutdown hook is added
     * to ensure proper closing on VM termination.
     * </p>
     *
     * @param args command line arguments
     */
    static void main(final String[] args) {
        UDPUtilities.runServerMain(args, HelloUDPServer::new);
    }
}