package info.kgeorgiy.ja.Gebesht.common;

import info.kgeorgiy.java.advanced.hello.HelloClient;
import info.kgeorgiy.java.advanced.hello.NewHelloServer;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Helper utilities shared by UDP client and server implementations in this
 * package.
 *
 * <p>Provides:
 * <ul>
 *   <li>Default timeout constants</li>
 *   <li>Command-line parsing helpers for client and server</li>
 *   <li>Message formatting helpers used by both blocking and non-blocking
 *       implementations</li>
 * </ul>
 * </p>
 */
public final class UDPUtilities {
    
    private UDPUtilities() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Default socket timeout for blocking operations in milliseconds.
     */
    public static final int SOCKET_TIMEOUT_MILLISECONDS = 100;

    /**
     * Default selector timeout in milliseconds.
     */
    public static final long SELECT_TIMEOUT_MILLISECONDS = 100;

    /**
     * Parses command-line arguments for the client.
     * Expected format: host port prefix threads requests
     *
     * @param args the command-line arguments
     * @return parsed arguments as a record
     * @throws IllegalArgumentException if arguments are invalid
     */
    public static ClientParseResult parseClientArgs(final String[] args) {
        if (args == null || args.length != 5) {
            throw new IllegalArgumentException("Usage: HelloUDPClient host port prefix threads requests");
        }
        for (final String arg : args) {
            Objects.requireNonNull(arg, "Arguments must not be null");
        }
        return new ClientParseResult(
                args[0],
                ParsingUtilities.parseIntegerArg(args, 1, null),
                args[2],
                ParsingUtilities.parseIntegerArg(args, 3, null),
                ParsingUtilities.parseIntegerArg(args, 4, null)
        );
    }

    /**
     * Parses command-line arguments for the server.
     * Expected format: port threads
     *
     * @param args the command-line arguments
     * @return parsed arguments as a record
     * @throws IllegalArgumentException if arguments are invalid
     */
    public static ServerParseResult parseServerArgs(final String[] args) {
        if (args == null || args.length != 2) {
            throw new IllegalArgumentException("Usage: HelloUDPServer port threads");
        }
        for (final String arg : args) {
            Objects.requireNonNull(arg, "Arguments must not be null");
        }
        return new ServerParseResult(
                ParsingUtilities.parseIntegerArg(args, 0, null),
                ParsingUtilities.parseIntegerArg(args, 1, null)
        );
    }

    /**
     * Shared CLI runner for UDP clients.
     *
     * @param args command-line arguments: host, port, prefix, threads, requests
     * @param client client implementation to run
     */
    public static void runClientMain(final String[] args, final HelloClient client) {
        Objects.requireNonNull(client, "Client must not be null");
        final ClientParseResult result;
        try {
            result = parseClientArgs(args);
        } catch (final RuntimeException e) {
            System.err.println(e.getMessage());
            return;
        }
        client.run(
                result.host(),
                result.port(),
                result.prefix(),
                result.requests(),
                result.threads()
        );
    }

    /**
     * Shared CLI runner for UDP servers.
     *
     * @param args command-line arguments: port, threads
     * @param serverSupplier supplier creating a new server instance
     */
    public static void runServerMain(final String[] args, final Supplier<? extends NewHelloServer> serverSupplier) {
        Objects.requireNonNull(serverSupplier, "Server supplier must not be null");
        final ServerParseResult result;
        try {
            result = parseServerArgs(args);
        } catch (final RuntimeException e) {
            System.err.println(e.getMessage());
            return;
        }

        try (NewHelloServer server = serverSupplier.get()) {
            Objects.requireNonNull(server, "Server must not be null");
            server.start(result.port(), result.threads());
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
            Thread.currentThread().join();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final Exception e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    /**
     * Formats the request string according to the client protocol.
     * Format: prefix + request_number + "_" + thread_id
     *
     * @param prefix the prefix string
     * @param requestNumber the request number
     * @param threadId the thread identifier
     * @return formatted request string
     */
    public static String formatRequest(final String prefix, final int requestNumber, final int threadId) {
        return prefix + requestNumber + "_" + threadId;
    }

    /**
     * Formats the expected response according to the server protocol.
     * Format: "Hello, " + request
     *
     * @param request the request string
     * @return expected response string
     */
    public static String formatExpectedResponse(final String request) {
        return "Hello, " + request;
    }

    /**
     * Parses the server format string and substitutes %% with the request.
     * Example: "Hello, %%" becomes "Hello, " + request
     *
     * @param format the format string with %% as placeholder
     * @param request the request string to substitute
     * @return formatted response string
     */
    public static String formatServerResponse(final String format, final String request) {
        return format.replaceAll("%%", request);
    }

    /**
     * Parsed client arguments.
     */
    public record ClientParseResult(String host, int port, String prefix, int threads, int requests) {
    }

    /**
     * Parsed server arguments.
     */
    public record ServerParseResult(int port, int threads) {
    }
}

