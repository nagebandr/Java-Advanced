package info.kgeorgiy.ja.Gebesht.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.*;
import info.kgeorgiy.ja.Gebesht.common.*;

/**
 * Blocking UDP client that sends requests concurrently using multiple threads.
 * <p>
 * Each thread independently sends a series of request messages to the specified server
 * and waits for a response. If a response does not match the expected format
 * or a timeout occurs, the request is retried automatically.
 * </p>
 * <p>
 * The client uses {@link DatagramSocket} with a socket timeout to avoid infinite blocking.
 * Threads are managed via a fixed thread pool; the main thread waits for all tasks
 * to finish because the executor service is closed (using try‑with‑resources style
 * {@link ExecutorService#close()} which waits for termination).
 * </p>
 *
 * @see HelloClient
 * @see UDPUtilities
 * @see NetworkUtilities
 */
public final class HelloUDPClient implements HelloClient {

    /**
     * Starts the client: sends {@code requests} messages per thread using {@code threads}
     * concurrent workers.
     * <p>
     * The host name is resolved, and then a fixed thread pool is created.
     * Each thread runs {@link #runThread(InetSocketAddress, String, int, int)}.
     * </p>
     *
     * @param host     server hostname or IP address
     * @param port     server port
     * @param prefix   prefix string used to build request messages
     * @param requests number of requests per thread
     * @param threads  number of parallel threads
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

        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final InetSocketAddress target = new InetSocketAddress(address, port);

        for (int thread = 1; thread <= threads; thread++) {
            final int finalThread = thread;
            pool.submit(() -> runThread(target, prefix, requests, finalThread));
        }

        // ExecutorService.close() waits for all submitted tasks to complete.
        pool.close();
    }

    /**
     * Performs the work of a single client thread.
     * <p>
     * Creates a {@link DatagramSocket} with a socket timeout, then iterates
     * from 1 to {@code requests}. For each request it calls
     * {@link #sendWithRetries(DatagramSocket, InetSocketAddress, int, String, DatagramPacket)}.
     * </p>
     *
     * @param address  target server address (host + port)
     * @param prefix   message prefix
     * @param requests number of requests to send in this thread
     * @param thread   thread number (1‑based) used to build request identifiers
     */
    private static void runThread(
            final InetSocketAddress address,
            final String prefix,
            final int requests,
            final int thread
    ) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(UDPUtilities.SOCKET_TIMEOUT_MILLISECONDS);
            final int size = socket.getReceiveBufferSize();
            final byte[] responseBuffer = new byte[size];
            final DatagramPacket responsePacket = NetworkUtilities.bytesToPacket(responseBuffer);
            for (int request = 1; request <= requests; request++) {
                final String requestText = UDPUtilities.formatRequest(prefix, request, thread);
                sendWithRetries(socket, address, size, requestText, responsePacket);
            }
        } catch (final SocketException e) {
            System.err.println("Socket error in thread " + thread + ": " + e.getMessage());
        }
    }

    /**
     * Sends a single UDP request and retries until a valid response is received
     * or the thread is interrupted.
     * <p>
     * The method repeatedly sends the request packet and attempts to receive a response.
     * On {@link SocketTimeoutException} it simply retries. On any other I/O error
     * it prints an error message and continues retrying.
     * The loop terminates when a response matches the expected format
     * (as defined by {@link UDPUtilities#formatExpectedResponse(String)}).
     * </p>
     *
     * @param socket         the UDP socket used for communication
     * @param address        target server address
     * @param size           the buffer size to be set on the response packet before each receive
     * @param request        the request string to send
     * @param responsePacket reusable packet for receiving responses
     */
    private static void sendWithRetries(
            final DatagramSocket socket,
            final InetSocketAddress address,
            final int size,
            final String request,
            final DatagramPacket responsePacket
    ) {
        final byte[] requestBytes = NetworkUtilities.stringToBytes(request);
        final DatagramPacket requestPacket = NetworkUtilities.bytesToPacket(requestBytes, address);

        while (!socket.isClosed() && !Thread.currentThread().isInterrupted()) {
            try {
                socket.send(requestPacket);
                responsePacket.setLength(size);
                socket.receive(responsePacket);

                final String response = NetworkUtilities.packetToString(responsePacket);

                if (response.equals(UDPUtilities.formatExpectedResponse(request))) {
                    return;
                }
            } catch (final SocketTimeoutException ignored) {
                System.err.println("Timeout while processing request " + request + ", retrying...");
            } catch (final IOException e) {
                System.err.println("IO error while processing request " + request + ": " + e.getMessage());
            }
        }
    }

    /**
     * Command-line entry point.
     * <p>
     * Expects arguments: {@code host port prefix requests threads}.
     * Parses them using {@link UDPUtilities#parseClientArgs(String[])} and
     * invokes {@link #run(String, int, String, int, int)}.
     * </p>
     *
     * @param args command line arguments
     */
    static void main(final String[] args) {
        UDPUtilities.runClientMain(args, new HelloUDPClient());
    }
}