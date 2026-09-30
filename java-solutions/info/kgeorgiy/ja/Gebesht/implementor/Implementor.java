package info.kgeorgiy.ja.Gebesht.implementor;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.tools.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Generates implementations for interface tokens and can package generated
 * implementation into a JAR file.
 * <p>
 * This class implements both:
 * <ul>
 *     <li>{@link Impler} - source generation</li>
 *     <li>{@link JarImpler} - source generation + compilation + JAR packaging</li>
 * </ul>
 * The current implementation corresponds to the easy version: only interfaces
 * are supported.
 */
public final class Implementor implements Impler, JarImpler {
    /**
     * Platform-specific line separator used in generated source code.
     */
    private static final String NL = System.lineSeparator();

    /**
     * One indentation level for generated methods.
     */
    private static final String INDENT = "    ";

    /**
     * Checks whether any enclosing class of {@code token} is private.
     *
     * @param token type token to inspect.
     * @return {@code true} if at least one enclosing class is private, otherwise {@code false}.
     */
    private static boolean hasPrivateEnclosingClass(final Class<?> token) {
        for (Class<?> current = token.getEnclosingClass(); current != null; current = current.getEnclosingClass()) {
            if (Modifier.isPrivate(current.getModifiers())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates input arguments for generation methods.
     * <p>
     * Easy version restriction: only implementable interfaces are accepted.
     *
     * @param token type token to implement.
     * @param root output root path.
     * @throws ImplerException if arguments are {@code null} or token is not supported.
     */
    private void validateArgs(final Class<?> token, final Path root) throws ImplerException {
        if (token == null || root == null) {
            throw new ImplerException("Arguments cannot be null");
        }
        if (!token.isInterface() || token.isSealed() || Modifier.isPrivate(token.getModifiers()) || hasPrivateEnclosingClass(token)) {
            throw new ImplerException("Only implementable interfaces are supported");
        }
    }

    /**
     * Escapes all non-ASCII characters in the given string with {@code \\uXXXX}.
     *
     * @param s source string.
     * @return escaped string safe for ASCII-only source output.
     */
    private static String escapeUnicode(final String s) {
        final StringBuilder result = new StringBuilder();
        for (final char c : s.toCharArray()) {
            if (c >= 128) {
                result.append(String.format("\\u%04X", (int) c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Writes text to writer after Unicode escaping.
     *
     * @param writer destination writer.
     * @param text text to write.
     * @throws IOException if I/O error occurs during writing.
     */
    private static void codeSafeWrite(final BufferedWriter writer, final String text) throws IOException {
        writer.write(escapeUnicode(text));
    }

    /**
     * Creates a set of exception types declared by the method.
     *
     * @param method method token.
     * @return set of declared exception classes.
     */
    private static Set<Class<?>> exceptionsSet(final Method method) {
        return new HashSet<>(List.of(method.getExceptionTypes()));
    }

    /**
     * Returns generated implementation simple class name for a token.
     *
     * @param token type token.
     * @return implementation simple name ({@code <SimpleName>Impl}).
     */
    private static String getImplSimpleName(final Class<?> token) {
        return token.getSimpleName() + "Impl";
    }

    /**
     * Resolves package directory path for generated files.
     *
     * @param token type token whose package should be used.
     * @param root output root.
     * @return full path to target package directory under {@code root}.
     */
    private static Path getPackagePath(final Class<?> token, final Path root) {
        return root.resolve(token.getPackageName().replace('.', File.separatorChar));
    }

    /**
     * Resolves generated Java source file path.
     *
     * @param token type token.
     * @param root output root.
     * @return path to generated {@code .java} file.
     */
    private static Path getJavaFilePath(final Class<?> token, final Path root) {
        return getPackagePath(token, root).resolve(getImplSimpleName(token) + ".java");
    }

    /**
     * Resolves generated class file path.
     *
     * @param token type token.
     * @param root output root.
     * @return path to compiled {@code .class} file.
     */
    private static Path getClassFilePath(final Class<?> token, final Path root) {
        return getPackagePath(token, root).resolve(getImplSimpleName(token) + ".class");
    }

    /**
     * Generates Java source code implementing the specified interface.
     *
     * @param token {@inheritDoc}
     * @param root {@inheritDoc}
     * @throws ImplerException {@inheritDoc}
     */
    @Override
    public void implement(final Class<?> token, final Path root) throws ImplerException {
        validateArgs(token, root);
        try {
            final String packageName = token.getPackageName();
            final String className = getImplSimpleName(token);

            final Path packagePath = getPackagePath(token, root);
            Files.createDirectories(packagePath);

            final Path file = packagePath.resolve(className + ".java");
            try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                if (!packageName.isEmpty()) {
                    codeSafeWrite(writer, "package " + packageName + ";" + NL + NL);
                }

                codeSafeWrite(writer, "public class " + className + " implements " + token.getCanonicalName() + " {" + NL + NL);

                final Map<String, Method> toGenerate = new LinkedHashMap<>();
                final Map<String, Set<Class<?>>> excepts = new HashMap<>();

                for (final Method method : token.getMethods()) {
                    if (!Modifier.isAbstract(method.getModifiers())) {
                        continue;
                    }
                    final String signature = generateSignature(method);
                    final Method existing = toGenerate.get(signature);

                    if (existing != null) {
                        final Class<?> seen = existing.getReturnType();
                        final Class<?> current = method.getReturnType();
                        if (seen.isAssignableFrom(current)) {
                            toGenerate.put(signature, method);
                            excepts.put(signature, exceptionsSet(method));
                        } else if (current.isAssignableFrom(seen)) {
                            if (!excepts.get(signature).equals(exceptionsSet(method))) {
                                throw new ImplerException("Methods with same signature and different exception sets encountered");
                            }
                        } else {
                            throw new ImplerException("Incompatible return types for method: " + signature);
                        }
                    } else {
                        toGenerate.put(signature, method);
                        excepts.put(signature, exceptionsSet(method));
                    }
                }

                for (final Method method : toGenerate.values()) {
                    final String ret = method.getReturnType().getCanonicalName();
                    final String signature = generateSignature(method);
                    final String body = generateBody(method.getReturnType());
                    final String exceptions = generateExceptions(method);
                    codeSafeWrite(writer, generateMethod(ret, signature, exceptions, body));
                }

                codeSafeWrite(writer, "}" + NL);
            }
        } catch (final IOException e) {
            throw new ImplerException("IO error", e);
        }
    }

    /**
     * Generates implementation for the specified interface and packs compiled class into JAR.
     *
     * @param token {@inheritDoc}
     * @param jarFile {@inheritDoc}
     * @throws ImplerException {@inheritDoc}
     */
    @Override
    public void implementJar(final Class<?> token, final Path jarFile) throws ImplerException {
        validateArgs(token, jarFile);

        final Path tempRoot;
        try {
            tempRoot = Files.createTempDirectory(Path.of("."), "implementor-temp-");
        } catch (final IOException e) {
            throw new ImplerException("Failed to create temporary directory", e);
        }

        try {
            implement(token, tempRoot);
            compile(token, tempRoot);
            createJar(token, jarFile, tempRoot);
        } finally {
            delete(tempRoot);
        }
    }

    /**
     * Compiles generated source file for implementation class.
     *
     * @param token original type token.
     * @param root temporary/output root where source is generated.
     * @throws ImplerException if compiler is unavailable or compilation fails.
     */
    private void compile(final Class<?> token, final Path root) throws ImplerException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new ImplerException("Java compiler is not available");
        }

        final Path javaFile = getJavaFilePath(token, root);

        final List<String> classpathEntries = new ArrayList<>();
        classpathEntries.add(root.toString());

        final CodeSource codeSource = token.getProtectionDomain().getCodeSource();
        if (codeSource != null && codeSource.getLocation() != null) {
            try {
                classpathEntries.add(Path.of(codeSource.getLocation().toURI()).toString());
            } catch (final URISyntaxException ignored) {
                // Maybe systemCp would be enough
            }
        }

        final String systemCp = System.getProperty("java.class.path");
        if (systemCp != null && !systemCp.isEmpty()) {
            classpathEntries.add(systemCp);
        }

        final String classpath = String.join(File.pathSeparator, classpathEntries);

        final int exitCode = compiler.run(
                null,
                null,
                null,
                "-encoding", "UTF-8",
                "-cp", classpath,
                "-d", root.toString(),
                javaFile.toString()
        );

        if (exitCode != 0) {
            throw new ImplerException("Compilation failed with exit code " + exitCode);
        }
    }

    /**
     * Creates target JAR file containing compiled implementation class.
     *
     * @param token original type token.
     * @param jarFile target JAR path.
     * @param root root directory containing compiled class.
     * @throws ImplerException if JAR creation fails.
     */
    private void createJar(final Class<?> token, final Path jarFile, final Path root) throws ImplerException {
        try {
            final Path parent = jarFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            final Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

            final Path classFile = getClassFilePath(token, root);
            final String entryName = token.getPackageName().replace('.', '/') + "/" + getImplSimpleName(token) + ".class";

            try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
                jos.putNextEntry(new JarEntry(entryName));
                Files.copy(classFile, jos);
                jos.closeEntry();
            }
        } catch (final IOException e) {
            throw new ImplerException("Failed to create jar", e);
        }
    }

    /**
     * Deletes directory tree recursively.
     *
     * @param root root path to delete.
     * @throws ImplerException if deletion fails.
     */
    private static void delete(final Path root) throws ImplerException {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (final IOException e) {
            throw new ImplerException("Failed to clean temporary directory", e);
        }
    }

    /**
     * Entry point.
     * <p>
     * Supported CLI formats:
     * <ul>
     *     <li>{@code Implementor <class-name> <output-directory>}</li>
     *     <li>{@code Implementor -jar <class-name> <output-jar-file>}</li>
     * </ul>
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        if (args == null) {
            System.err.println("Usage: Implementor <class name> <output directory>");
            System.err.println("   or: Implementor -jar <class name> <output jar file>");
            return;
        }

        final Implementor implementor = new Implementor();
        try {
            if (args.length == 2) {
                final Class<?> token = Class.forName(args[0]);
                final Path root = Path.of(args[1]);
                implementor.implement(token, root);
            } else if (args.length == 3 && "-jar".equals(args[0])) {
                final Class<?> token = Class.forName(args[1]);
                final Path jarFile = Path.of(args[2]);
                implementor.implementJar(token, jarFile);
            } else {
                System.err.println("Usage: Implementor <class name> <output directory>");
                System.err.println("   or: Implementor -jar <class name> <output jar file>");
            }
        } catch (final ClassNotFoundException e) {
            System.err.println("Class not found: " + e.getMessage());
        } catch (final ImplerException e) {
            System.err.println("Implementation error: " + e.getMessage());
        } catch (final RuntimeException e) {
            System.err.println("Runtime error: " + e.getMessage());
        }
    }

    /**
     * Builds a method signature with synthetic argument names ({@code arg0}, {@code arg1}, ...).
     *
     * @param method method token.
     * @return generated signature without modifiers and return type.
     */
    private static String generateSignature(final Method method) {
        final StringBuilder sb = new StringBuilder();
        sb.append(method.getName()).append("(");
        final Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            sb.append(params[i].getType().getCanonicalName()).append(" arg").append(i);
            if (i < params.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Builds the {@code throws} clause for a method.
     *
     * @param method method token.
     * @return throws clause string (empty when no declared exceptions).
     */
    private static String generateExceptions(final Method method) {
        final StringBuilder sb = new StringBuilder();
        final Class<?>[] exceptions = method.getExceptionTypes();
        if (exceptions.length > 0) {
            sb.append(" throws ");
            for (int i = 0; i < exceptions.length; i++) {
                sb.append(exceptions[i].getCanonicalName());
                if (i < exceptions.length - 1) {
                    sb.append(", ");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Generates a default method body based on return type.
     *
     * @param returnType method return type.
     * @return Java statement sequence representing method body.
     */
    private static String generateBody(final Class<?> returnType) {
        if (returnType.equals(void.class)) {
            return "return;";
        } else if (returnType.equals(boolean.class)) {
            return "return false;";
        } else if (returnType.isPrimitive()) {
            return "return 0;";
        } else {
            return "return null;";
        }
    }

    /**
     * Produces full Java source code for one generated method.
     *
     * @param ret return type canonical name.
     * @param signature method signature.
     * @param exceptions throws clause.
     * @param body method body.
     * @return formatted method source code string.
     */
    private static String generateMethod(final String ret, final String signature, final String exceptions, final String body) {
        return INDENT + "@Override" + NL +
                INDENT + "public " + ret + " " + signature + exceptions + "{" + NL +
                INDENT + INDENT + body + NL +
                INDENT + "}" + NL + NL;
    }
}
