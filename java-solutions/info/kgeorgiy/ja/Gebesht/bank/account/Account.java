package info.kgeorgiy.ja.Gebesht.bank.account;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote interface representing a bank account.
 * Provides access to account identifier and balance.
 */
public interface Account extends Remote {

    /**
     * Returns the unique identifier of this account.
     *
     * @return account identifier
     * @throws RemoteException if a remote error occurs
     */
    String getId() throws RemoteException;

    /**
     * Returns the current account balance.
     *
     * @return account balance
     * @throws RemoteException if a remote error occurs
     */
    int getAmount() throws RemoteException;

    /**
     * Sets the account balance.
     *
     * @param amount new balance value
     * @throws RemoteException if a remote error occurs
     */
    void setAmount(int amount) throws RemoteException;

    /**
     * Atomically changes the account balance by the specified delta.
     *
     * @param delta balance change
     * @throws RemoteException if a remote error occurs
     */
    void addAmount(int delta) throws RemoteException;
}