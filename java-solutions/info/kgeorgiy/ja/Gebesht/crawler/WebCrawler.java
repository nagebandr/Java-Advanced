package info.kgeorgiy.ja.Gebesht.crawler;

import info.kgeorgiy.java.advanced.crawler.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import info.kgeorgiy.ja.Gebesht.common.*;

/**
 * Thread-safe web crawler performing breadth-first traversal up to a given depth.
 * Uses two fixed thread pools (downloaders and extractors). The {@code perHost}
 * limit is validated but not enforced (simplified version).
 */
public final class WebCrawler implements NewCrawler {
    private static final List<Integer> DEFAULT_ARG_VALUES = List.of(1, 1, 1, 1);

    private final Downloader downloader;
    private final ExecutorService downloaderPool;
    private final ExecutorService extractorPool;

    /**
     * Holds the mutable state of one crawl: visited URLs, successes, errors,
     * and level-by-level synchronization.
     */
    private static final class CrawlState {
        private final Set<String> visited = ConcurrentHashMap.newKeySet();
        private final List<String> downloaded = Collections.synchronizedList(new ArrayList<>());
        private final ConcurrentMap<String, IOException> errors = new ConcurrentHashMap<>();
        private final Phaser phaser = new Phaser(1);

        boolean addVisited(String url) { return visited.add(url); }
        void addDownloaded(String url) { downloaded.add(url); }
        void addError(String url, IOException e) { errors.put(url, e); }
        void register() { phaser.register(); }
        void arriveAndDeregister() { phaser.arriveAndDeregister(); }
        void awaitAdvance() { phaser.arriveAndAwaitAdvance(); }
        Result result() { return new Result(downloaded, errors); }
    }

    /**
     * Constructs a crawler with the given concurrency limits.
     *
     * @param downloader  page downloader
     * @param downloaders max concurrent downloads
     * @param extractors  max concurrent link extractions
     * @param perHost     per-host limit (not enforced but must be &gt; 0)
     * @throws NullPointerException     if the downloader is null
     * @throws IllegalArgumentException if any numeric argument is &le; 0
     */
    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        Objects.requireNonNull(downloader);
        if (downloaders <= 0 || extractors <= 0 || perHost <= 0)
            throw new IllegalArgumentException("Arguments must be positive");
        this.downloader = downloader;
        this.downloaderPool = Executors.newFixedThreadPool(downloaders);
        this.extractorPool = Executors.newFixedThreadPool(extractors);
    }

    /**
     * Crawls with an empty include filter (all links are followed).
     */
    @Override
    public Result download(String url, int depth) {
        return download(url, depth, List.of(""));
    }

    /**
     * Starts a breadth-first crawl from the given URL.
     * Only URLs containing at least one string from {@code includes} are followed.
     *
     * @param url      starting URL
     * @param depth    maximum depth (must be &gt; 0)
     * @param includes the list of substrings a URL must contain to be followed
     * @return downloaded pages and errors
     * @throws NullPointerException     if url or includes is null, or includes contains null
     * @throws IllegalArgumentException if depth &le; 0
     */
    @Override
    public Result download(String url, int depth, List<String> includes) {
        Objects.requireNonNull(url);
        Objects.requireNonNull(includes);
        if (depth <= 0) throw new IllegalArgumentException("Depth must be positive");
        for (String inc : includes) Objects.requireNonNull(inc);

        Predicate<String> filter = c -> includes.stream().anyMatch(c::contains);
        if (!filter.test(url)) return new Result(List.of(), Map.of());

        CrawlState state = new CrawlState();
        Set<String> level = Set.of(url);
        state.addVisited(url);

        for (int d = 1; d <= depth && !level.isEmpty(); d++) {
            Set<String> nextLevel = ConcurrentHashMap.newKeySet();
            for (String current : level) {
                state.register();
                try {
                    submitDownload(current, d, depth, state, nextLevel, filter);
                } catch (RejectedExecutionException e) {
                    state.addError(current, new IOException("Crawler is closed", e));
                    state.arriveAndDeregister();
                }
            }
            state.awaitAdvance();
            level = nextLevel;
        }
        return state.result();
    }

    /**
     * Submits a download task for a single URL.
     */
    private void submitDownload(String url, int depth, int maxDepth,
                                CrawlState state, Set<String> nextLevel,
                                Predicate<String> filter) {
        downloaderPool.submit(() -> {
            try {
                Document doc = downloader.download(url);
                state.addDownloaded(url);
                if (depth < maxDepth) {
                    state.register();
                    try {
                        submitExtract(url, doc, state, nextLevel, filter);
                    } catch (RejectedExecutionException e) {
                        state.arriveAndDeregister();
                    }
                }
            } catch (IOException e) {
                state.addError(url, e);
            } finally {
                state.arriveAndDeregister();
            }
        });
    }

    /**
     * Submits a link extraction task for a downloaded page.
     */
    private void submitExtract(String pageUrl, Document doc,
                               CrawlState state, Set<String> nextLevel,
                               Predicate<String> filter) {
        extractorPool.submit(() -> {
            try {
                for (String link : doc.extractLinks()) {
                    if (filter.test(link) && state.addVisited(link))
                        nextLevel.add(link);
                }
            } catch (IOException e) {
                state.addError(pageUrl, e);
            } finally {
                state.arriveAndDeregister();
            }
        });
    }

    /**
     * Immutable record with parsed command-line arguments.
     */
    private record ParseResult(String url, int depth, int downloaders, int extractors, int perHost) {}

    /**
     * Parses all command-line arguments.
     *
     * @throws NullPointerException if args or the first element is null
     * @throws IllegalArgumentException if a format is invalid
     */
    private static ParseResult parse(String[] args) {
        Objects.requireNonNull(args);
        if (args.length > 5) throw new IllegalArgumentException(
                "Usage: WebCrawler <url> <depth> <downloaders> <extractors> <perHost>");
        Objects.requireNonNull(args[0]);
        return new ParseResult(args[0],
                ParsingUtilities.parseIntegerArg(args, 1, DEFAULT_ARG_VALUES),
                ParsingUtilities.parseIntegerArg(args, 1, DEFAULT_ARG_VALUES),
                ParsingUtilities.parseIntegerArg(args, 1, DEFAULT_ARG_VALUES),
                ParsingUtilities.parseIntegerArg(args, 1, DEFAULT_ARG_VALUES));
    }

    /**
     * Entry point. Creates a crawler, runs the crawl, and prints counts.
     */
    public static void main(String[] args) {
        final ParseResult result;
        try {
            result = parse(args);
        } catch (final RuntimeException e) {
            System.err.println(e.getMessage());
            return;
        }
        try (WebCrawler crawler = new WebCrawler(
                new CachingDownloader(1.0), result.downloaders(), result.extractors(), result.perHost())) {
            Result r = crawler.download(result.url(), result.depth());
            System.out.println("Downloaded: " + r.downloaded().size());
            System.out.println("Errors: " + r.errors().size());
        } catch (IOException e) {
            System.err.println("Failed to initialize downloader: " + e.getMessage());
        }
    }

    /**
     * Closes the crawler by shutting down both thread pools.
     */
    @Override
    public void close() {
        downloaderPool.close();
        extractorPool.close();
    }
}