package info.kgeorgiy.ja.Gebesht.bank;

import info.kgeorgiy.ja.Gebesht.bank.account.Account;
import info.kgeorgiy.ja.Gebesht.bank.person.Person;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

public interface Bank extends Remote {
    /**
     * Creates a new account with specified identifier if it does not already exist.
     * @param id account id
     * @return created or existing account.
     */
    Account createAccount(String id) throws RemoteException;

    /**
     * Returns account by identifier.
     * @param id account id
     * @return account with specified identifier or {@code null} if such account does not exist.
     */
    Account getAccount(String id) throws RemoteException;

    /**
     * Creates a new person record if absent.
     * @param passport unique person id
     * @param name first name
     * @param surname last name
     * @return created or existing person (remote)
     */
    Person createPerson(String passport, String name, String surname) throws RemoteException;

    /**
     * Returns person by passport. If {@code remote} is true returns a remote person (RemotePerson),
     * otherwise returns a local serializable snapshot (LocalPerson).
     * @param id person passport id
     * @param remote whether to return remote person
     * @return person or null if absent
     */
    Person getPerson(String id, boolean remote) throws RemoteException;

    /**
     * Returns all accounts belonging to the person with the specified passport.
     * @param id person passport id
     * @return map of subId -> account
     */
    Map<String, ? extends Account> getAccounts(String id) throws RemoteException;
}
