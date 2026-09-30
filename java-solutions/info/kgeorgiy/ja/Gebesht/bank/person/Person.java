package info.kgeorgiy.ja.Gebesht.bank.person;

import info.kgeorgiy.ja.Gebesht.bank.account.Account;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

/**
 * Remote interface representing a bank person.
 * Provides access to personal data and associated accounts.
 */
public interface Person extends Remote {

    /**
     * Returns the unique identifier of this person.
     *
     * @return person identifier
     * @throws RemoteException if a remote error occurs
     */
    String getId() throws RemoteException;

    /**
     * Returns the first name of this person.
     *
     * @return first name
     * @throws RemoteException if a remote error occurs
     */
    String getName() throws RemoteException;

    /**
     * Returns the surname of this person.
     *
     * @return surname
     * @throws RemoteException if a remote error occurs
     */
    String getSurname() throws RemoteException;

    /**
     * Returns an account by its sub-identifier.
     *
     * @param id account sub-identifier
     * @return account associated with the identifier,
     *         or {@code null} if no such account exists
     * @throws RemoteException if a remote error occurs
     */
    Account getAccount(String id) throws RemoteException;

    /**
     * Creates a new account with the specified sub-identifier.
     * If the account already exists, returns the existing account.
     *
     * @param id account sub-identifier
     * @return created or existing account
     * @throws RemoteException if a remote error occurs
     */
    Account createAccount(String id) throws RemoteException;

    /**
     * Returns all accounts owned by this person.
     *
     * @return map of account sub-identifiers to accounts
     * @throws RemoteException if a remote error occurs
     */
    Map<String, ? extends Account> getAccounts() throws RemoteException;
}