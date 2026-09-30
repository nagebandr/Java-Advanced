package info.kgeorgiy.ja.Gebesht.walk;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Walk {
    private static final String ERROR_HASH = "00000000";
    private static final int BUFFER_SIZE = 1 << 13;

    private record Args(Path in, Path out) {}

    private static Args parseArgs(final String[] args) throws IllegalArgumentException {
        if (args == null || args.length != 2 || args[0] == null || args[1] == null) {
            throw new IllegalArgumentException("Usage: java Walk <input file> <output file>");
        }
        try {
            return new Args(Paths.get(args[0]), Paths.get(args[1]));
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid path in arguments", e);
        }
    }

    private static void createDirectories(final Path path) throws IOException {
        final Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static String getHash(final String pathName) {
        Path path;
        try {
            path = Paths.get(pathName);
        } catch (final InvalidPathException e) {
            return ERROR_HASH;
        }
        final FNVHasher hasher = new FNVHasher();
        final byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream is = new BufferedInputStream(Files.newInputStream(path))) {
            int read;
            while ((read = is.read(buffer)) != -1) {
                hasher.update(buffer, read);
            }
        } catch (final IOException e) {
            return ERROR_HASH;
        }
        return String.format("%08x", hasher.hash());
    }

    public static void main(final String[] args) {
        Args files;
        try {
            files = parseArgs(args);
        } catch (final IllegalArgumentException e) {
            System.err.println("Incorrect arguments: " + e.getMessage());
            return;
        }

        final Path in = files.in;
        final Path out = files.out;

        try {
            createDirectories(out);
        } catch (final IOException e) {
            System.err.println("Can't create directories for output file: " + e.getMessage());
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(in);
             BufferedWriter writer = Files.newBufferedWriter(out)) {

            String pathName;
            while ((pathName = reader.readLine()) != null) {
                final String hash = getHash(pathName);
                writer.write(hash);
                writer.write(' ');
                writer.write(pathName);
                writer.newLine();
            }
        } catch (final IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
        }
    }
}