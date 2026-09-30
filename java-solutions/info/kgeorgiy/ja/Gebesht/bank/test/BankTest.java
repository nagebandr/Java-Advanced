package info.kgeorgiy.ja.Gebesht.bank.test;

import info.kgeorgiy.ja.Gebesht.bank.account.Account;
import info.kgeorgiy.ja.Gebesht.bank.person.LocalPerson;
import info.kgeorgiy.ja.Gebesht.bank.person.Person;
import info.kgeorgiy.ja.Gebesht.bank.Bank;
import org.junit.jupiter.api.Test;
import java.rmi.RemoteException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test suite for the bank implementation.
 * Verifies creation and interaction of remote and local persons,
 * accounts, and their consistency across RMI boundaries.
 */
class BankTest extends TestBase {

    /**
     * Tests that a person can be created and retrieved in both remote and local forms.
     * Verifies that the data (passport, name, surname) is correctly stored and that
     * the local snapshot is an instance of {@link LocalPerson}.
     *
     * @throws RemoteException if an RMI error occurs during bank operations
     */
    @Test
    void createPerson() throws RemoteException {
        final String passport = "116501";

        final Person created = bank.createPerson(passport, "Andrew", "Stankevich");
        final Person remote = bank.getPerson(passport, true);
        final Person local = bank.getPerson(passport, false);

        assertNotNull(created);
        assertNotNull(remote);
        assertNotNull(local);
        assertEquals(passport, remote.getId());
        assertEquals("Andrew", remote.getName());
        assertEquals("Stankevich", remote.getSurname());
        assertEquals(passport, local.getId());
        assertEquals("Andrew", local.getName());
        assertEquals("Stankevich", local.getSurname());
        assertInstanceOf(LocalPerson.class, local);
    }

    /**
     * Tests that an account can be created through a person and that changes to the account
     * are reflected globally and visible through remote person references.
     *
     * @throws RemoteException if an RMI error occurs during bank operations
     */
    @Test
    void createAccountThroughPerson() throws RemoteException {
        final String passport = "105590";
        final String subId = "wallet";
        final String accountId = passport + ":" + subId;

        final Person person = bank.createPerson(passport, "Georgiy", "Korneev");
        final Account account = person.createAccount(subId);

        assertEquals(accountId, account.getId());
        assertEquals(0, account.getAmount());
        assertNotNull(bank.getAccount(accountId));

        account.setAmount(228);
        assertEquals(228, bank.getAccount(accountId).getAmount());

        final Person remote = bank.getPerson(passport, true);
        assertEquals(228, remote.getAccount(subId).getAmount());
    }

    /**
     * Tests that a local person is a snapshot and does not reflect subsequent remote changes,
     * while changes made locally do not propagate back to the bank.
     *
     * @throws RemoteException if an RMI error occurs during bank operations
     */
    @Test
    void localPersonIsSnapshot() throws RemoteException {
        final String passport = "138947";
        final String subId = "wallet";
        final String accountId = passport + ":" + subId;

        final Person person = bank.createPerson(passport, "Ekaterina", "Kucheruk");
        final Account remoteAccount = person.createAccount(subId);
        remoteAccount.setAmount(101);

        final LocalPerson local = (LocalPerson) bank.getPerson(passport, false);
        assertEquals(101, local.getAccount(subId).getAmount());
        assertNull(local.getAccount("missing"));

        remoteAccount.setAmount(1);
        assertEquals(1, bank.getAccount(accountId).getAmount());
        assertEquals(101, local.getAccount(subId).getAmount());

        final Account localOnly = local.createAccount("local-only");
        localOnly.setAmount(2);
        assertEquals(2, local.getAccount("local-only").getAmount());
        assertNull(bank.getAccount(passport + ":local-only"));
    }

    /**
     * Tests that a single person can own multiple accounts, each with independent amounts,
     * and that {@link Person#getAccounts()} returns all of them.
     *
     * @throws RemoteException if an RMI error occurs during bank operations
     */
    @Test
    void multipleAccountsForPerson() throws RemoteException {
        final String passport = "127721";
        final Person person = bank.createPerson(passport, "Dmitriy", "Shtuckenberg");

        final Account acc1 = person.createAccount("wallet1");
        final Account acc2 = person.createAccount("wallet2");
        final Account acc3 = person.createAccount("wallet3");

        acc1.setAmount(1);
        acc2.setAmount(2);
        acc3.setAmount(3);

        final var accounts = person.getAccounts();
        assertEquals(3, accounts.size());
        assertEquals(1, accounts.get("wallet1").getAmount());
        assertEquals(2, accounts.get("wallet2").getAmount());
        assertEquals(3, accounts.get("wallet3").getAmount());
    }

    /**
     * Tests that {@link Bank#getAccounts(String)} correctly maps full account IDs
     * (passport:subId) to sub-identifiers for a given person.
     *
     * @throws RemoteException if an RMI error occurs during bank operations
     */
    @Test
    void getAccountsMapping() throws RemoteException {
        final String passport = "311643";
        final Person person = bank.createPerson(passport, "Artyom", "Koton");

        person.createAccount("wallet1");
        person.createAccount("wallet2");

        final var accounts = bank.getAccounts(passport);
        assertTrue(accounts.containsKey("wallet1"));
        assertTrue(accounts.containsKey("wallet2"));
        assertEquals(2, accounts.size());
    }

    /**
     * Tests that requesting a person (either remote or local) by a non‑existent passport
     * returns {@code null} instead of throwing an exception.
     *
     * @throws RemoteException if an RMI error occurs during bank operations
     */
    @Test
    void nonexistentPersonReturnsNull() throws RemoteException {
        final Person remote = bank.getPerson("nonexistent", true);
        final Person local = bank.getPerson("nonexistent", false);

        assertNull(remote);
        assertNull(local);
    }

    /**
     * Tests that a remote person obtained through the bank is a live reference:
     * any changes made through one reference are immediately visible through another.
     *
     * @throws RemoteException if an RMI error occurs during bank operations
     */
    @Test
    void remotePersonChangesGlobal() throws RemoteException {
        final String passport = "224934";
        bank.createPerson(passport, "Lev", "Dovzhik");

        final Person person1 = bank.getPerson(passport, true);
        final Account account1 = person1.createAccount("wallet");
        account1.setAmount(1);

        final Person person2 = bank.getPerson(passport, true);
        final Account account2 = person2.getAccount("wallet");
        assertEquals(1, account2.getAmount());

        account2.setAmount(2);
        assertEquals(2, account1.getAmount());
    }

    /**
     * Tests that a local person is a detached snapshot:
     * modifications to a local account do not affect the global remote state,
     * and remote changes after the snapshot was taken are not reflected in the snapshot.
     *
     * @throws RemoteException if an RMI error occurs during bank operations
     */
    @Test
    void localPersonChangesNotGlobal() throws RemoteException {
        final String passport = "121805";
        bank.createPerson(passport, "Pavel", "Skakov");

        final Person person = bank.getPerson(passport, true);
        final Account remoteAccount = person.createAccount("students tears");
        remoteAccount.setAmount(1000000);

        final LocalPerson localSnapshot = (LocalPerson) bank.getPerson(passport, false);
        final Account localAccount = localSnapshot.getAccount("students tears");
        assertEquals(1000000, localAccount.getAmount());

        localAccount.setAmount(2000000);
        assertEquals(1000000, remoteAccount.getAmount());
        assertEquals(1000000, bank.getAccount(passport + ":students tears").getAmount());
    }

    /**
     * Concurrent test: create many accounts for the same person in parallel
     * and set amounts concurrently. Verifies that all accounts exist with
     * expected amounts after the operations.
     */
    @Test
    void createAccountsConcurrently() throws Exception {
        final String passport = "USA:(";
        final String name = "Walter";
        final String surname = "White";
        final String account = "barrel";

        final Person heisenberg = bank.createPerson(passport, name, surname);

        final int N = 8;

        runConcurrentTasks(N,
                i -> {
                    final String subId = account + i;
                    try {
                        final Account acc = heisenberg.createAccount(subId);
                        acc.setAmount(10000000 + i);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                () -> {
                    for (int i = 0; i < N; i++) {
                        final String subId = account + i;
                        final String id = passport + ":" + subId;
                        final Account bankAcc = bank.getAccount(id);
                        assertNotNull(bankAcc);
                        assertEquals(10000000 + i, bankAcc.getAmount());
                    }
                }
        );
    }
}