package info.kgeorgiy.ja.Gebesht.bank.test;

import info.kgeorgiy.ja.Gebesht.bank.Client;
import info.kgeorgiy.ja.Gebesht.bank.ClientService;
import info.kgeorgiy.ja.Gebesht.bank.person.Person;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test suite for the {@link Client} application.
 * Verifies person creation, account updates,
 * argument validation, and consistency checks.
 */
class ClientTest extends TestBase {

    /**
     * Original standard output stream.
     */
    private final PrintStream originalOut = System.out;

    /**
     * Original standard error stream.
     */
    private final PrintStream originalErr = System.err;

    /**
     * Restores original output streams after each test.
     */
    @AfterEach
    void restoreOutput() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    /**
     * Tests that the client creates a new person and account,
     * and correctly updates the account balance.
     *
     * @throws Exception if an unexpected error occurs
     */
    @Test
    void clientCreatesPerson() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));

        ClientService.execute(new String[]{"Andrew", "Stankevich", "116501", "wallet", "5"});

        final String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Creating person") || output.contains("Person exists"));
        assertTrue(output.contains("Creating account via person") || output.contains("Money:"));
        assertTrue(output.contains("Money: 5"));

        final Person person = bank.getPerson("116501", true);
        assertEquals(5, person.getAccount("wallet").getAmount());
    }

    /**
     * Tests that the client correctly works with an existing person
     * and updates the account balance.
     *
     * @throws Exception if an unexpected error occurs
     */
    @Test
    void clientChecksPersonData() throws Exception {
        bank.createPerson("passport-5", "Jane", "Roe");

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));

        ClientService.execute(new String[]{"Jane", "Roe", "passport-5", "acc", "10"});

        final String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Person exists"));
        assertTrue(output.contains("Money: 10"));
    }

    /**
     * Tests that the client throws an exception
     * when an insufficient number of arguments is provided.
     */
    @Test
    void invalidArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> ClientService.execute(new String[]{"Name", "Surname"}));
    }

    /**
     * Tests that the client throws an exception
     * when the balance delta is not a valid integer.
     */
    @Test
    void invalidDelta() {
        assertThrows(IllegalArgumentException.class,
                () -> ClientService.execute(new String[]{"Name", "Surname", "passport", "account", "NaN"}));
    }

    /**
     * Tests that the client detects mismatched personal data
     * for an existing passport identifier.
     *
     * @throws Exception if an unexpected error occurs
     */
    @Test
    void dataMismatch() throws Exception {
        bank.createPerson("passport", "Name", "Surname1");

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));

        final RemoteException exception = assertThrows(RemoteException.class,
                () -> ClientService.execute(new String[]{"Name", "Surname2", "passport", "account", "100"}));

        assertTrue(exception.getMessage().contains("mismatch")
                || exception.getMessage().contains("Error"));
    }

    /**
     * Concurrent test: run multiple ClientService.execute calls in parallel
     * updating the same account and verify the final amount is correct.
     */
    @Test
    void clientConcurrentRequests() throws Exception {
        final String passport = "USA:(";
        final String name = "Jesse";
        final String surname = "Pinkman";
        final String account = "Saul Goodman";

        bank.createPerson(passport, name, surname).createAccount(account);

        final int N = 40;

        runConcurrentTasks(N,
                _ -> ClientService.execute(new String[]{name, surname, passport, account, "10000"}),
                () -> {
                    final Person person = bank.getPerson(passport, true);
                    assertEquals(10000 * N, person.getAccount(account).getAmount());
                }
        );
    }
}