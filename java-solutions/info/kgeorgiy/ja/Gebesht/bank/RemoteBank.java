package info.kgeorgiy.ja.Gebesht.bank;

import info.kgeorgiy.ja.Gebesht.bank.account.Account;
import info.kgeorgiy.ja.Gebesht.bank.account.RemoteAccount;
import info.kgeorgiy.ja.Gebesht.bank.person.LocalPerson;
import info.kgeorgiy.ja.Gebesht.bank.person.Person;
import info.kgeorgiy.ja.Gebesht.bank.person.RemotePerson;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Remote implementation of the {@link Bank} interface.
 * Stores accounts and persons in memory and exports remote objects
 * on a specified RMI port.
 *
 * @author Gebesht
 * @version 1.0
 */
public final class RemoteBank implements Bank {
    private final int port;
    private final ConcurrentMap<String, RemoteAccount> accounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RemotePerson> persons = new ConcurrentHashMap<>();

    /**
     * Constructs a remote bank that exports remote objects on the given port.
     *
     * @param port RMI port to export remote accounts and persons on
     */
    public RemoteBank(final int port) {
        this.port = port;
    }

    /**
     * Creates a new account with the specified identifier if it does not already exist.
     * The created account is exported as a remote object on the bank's port.
     *
     * @param id account identifier (must be unique)
     * @return the created or existing remote account
     * @throws RemoteException if exporting the account fails
     */
    @Override
    public RemoteAccount createAccount(final String id) throws RemoteException {
        final RemoteAccount account = new RemoteAccount(id);
        if (accounts.putIfAbsent(id, account) == null) {
            try {
                UnicastRemoteObject.exportObject(account, port);
                return account;
            } catch (final RemoteException e) {
                accounts.remove(id);
                throw e;
            }
        } else {
            return getAccount(id);
        }
    }

    /**
     * Returns an account by its identifier.
     *
     * @param id account identifier
     * @return the remote account with the specified identifier,
     *         or {@code null} if no such account exists
     */
    @Override
    public RemoteAccount getAccount(final String id) {
        return accounts.get(id);
    }

    /**
     * Creates a new person record if absent.
     * The created person is exported as a remote object on the bank's port.
     * If a person with the same passport already exists but has different
     * name or surname, a {@link RemoteException} is thrown.
     *
     * @param id      unique person passport
     * @param name    first name
     * @param surname last name
     * @return the created or existing remote person
     * @throws RemoteException if a person with the same id exists with different data,
     *                         or if exporting the remote person fails
     */
    @Override
    public RemotePerson createPerson(final String id, final String name, final String surname) throws RemoteException {
        synchronized (persons) {
            final RemotePerson existing = persons.get(id);
            if (existing != null) {
                if (!existing.getName().equals(name) || !existing.getSurname().equals(surname)) {
                    throw new RemoteException("Person with id " + id + " already exists with different data");
                }
                return existing;
            }

            final RemotePerson remotePerson = new RemotePerson(id, name, surname, this);
            UnicastRemoteObject.exportObject(remotePerson, port);
            persons.put(id, remotePerson);
            return remotePerson;
        }
    }

    /**
     * Returns a person by passport.
     * If {@code remote} is {@code true} returns the remote person (still living on the server),
     * otherwise returns a local snapshot ({@link LocalPerson}) that is detached from the server state.
     *
     * @param id     person passport id
     * @param remote whether to return the remote person (true) or a local snapshot (false)
     * @return the person (remote or local), or {@code null} if no person with the given passport exists
     * @throws RemoteException if a remote error occurs (rare, as the method is mainly local)
     */
    @Override
    public Person getPerson(final String id, final boolean remote) throws RemoteException {
        final RemotePerson remotePerson = persons.get(id);
        if (remotePerson == null) {
            return null;
        }
        if (remote) {
            return remotePerson;
        }
        final LocalPerson localPerson = new LocalPerson(id, remotePerson.getName(), remotePerson.getSurname());
        for (final Map.Entry<String, ? extends Account> entry : getAccounts(id).entrySet()) {
            final String subId = entry.getKey();
            localPerson.addAccount(subId, entry.getValue().getAmount());
        }
        return localPerson;
    }

    /**
     * Returns all accounts belonging to the person with the specified passport.
     * Accounts are identified by the prefix "{@code passport:}" followed by a sub-identifier.
     *
     * @param id person passport id
     * @return a map from sub-identifier to the corresponding remote account
     */
    @Override
    public Map<String, ? extends Account> getAccounts(final String id) {
        final ConcurrentMap<String, Account> personAccounts = new ConcurrentHashMap<>();
        final String prefix = id + ":";
        for (final Map.Entry<String, RemoteAccount> entry : accounts.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                final String fullId = entry.getKey();
                final String subId = fullId.substring(prefix.length());
                personAccounts.put(subId, entry.getValue());
            }
        }
        return personAccounts;
    }
}