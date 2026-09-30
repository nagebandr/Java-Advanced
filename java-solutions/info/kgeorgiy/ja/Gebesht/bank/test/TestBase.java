package info.kgeorgiy.ja.Gebesht.bank.test;

import info.kgeorgiy.ja.Gebesht.bank.Bank;
import info.kgeorgiy.ja.Gebesht.bank.BankUtilities;
import info.kgeorgiy.ja.Gebesht.bank.RemoteBank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

abstract class TestBase {
    protected static final int REGISTRY_PORT = 1099;
    protected static final String BANK_URL = BankUtilities.DEFAULT_BANK_URL;
    protected static final int TL_SECONDS = 10;

    protected RemoteBank remoteBank;
    protected Bank bank;

    @BeforeAll
    static void ensureRegistry() throws RemoteException {
        try {
            LocateRegistry.getRegistry(REGISTRY_PORT).list();
        } catch (final RemoteException e) {
            LocateRegistry.createRegistry(REGISTRY_PORT);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        remoteBank = new RemoteBank(0);
        UnicastRemoteObject.exportObject(remoteBank, 0);
        Naming.rebind(BANK_URL, remoteBank);
        bank = (Bank) Naming.lookup(BANK_URL);
    }

    @AfterEach
    void tearDown() {
        try {
            Naming.unbind(BANK_URL);
        } catch (final Exception ignored) {
        }
        if (remoteBank != null) {
            try {
                UnicastRemoteObject.unexportObject(remoteBank, true);
            } catch (final Exception ignored) {
            }
        }
    }

    @FunctionalInterface
    interface ThrowingIntConsumer {
        void accept(int i) throws Exception;
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Runs N tasks in parallel, synchronizing their start.
     *
     * @param tests     number of threads (and tasks)
     * @param task      action that receives the thread index (0..nThreads-1)
     * @param postCheck check to run after completion
     * @throws Exception if timeout occurs or any task throws an exception
     */
    protected void runConcurrentTasks(
            final int tests,
            final ThrowingIntConsumer task,
            final ThrowingRunnable postCheck
    ) throws Exception {
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch end = new CountDownLatch(tests);

        try (final ExecutorService service = Executors.newFixedThreadPool(tests)) {
            for (int i = 0; i < tests; i++) {
                final int index = i;
                service.submit(() -> {
                    try {
                        start.await();
                        task.accept(index);
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        end.countDown();
                    }
                });
            }

            start.countDown();

            if (!end.await(TL_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("Time limit exceeded");
            }
            postCheck.run();
        }
    }
}

