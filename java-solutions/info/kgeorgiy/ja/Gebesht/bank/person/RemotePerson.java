package info.kgeorgiy.ja.Gebesht.bank.person;

import info.kgeorgiy.ja.Gebesht.bank.Bank;
import info.kgeorgiy.ja.Gebesht.bank.account.Account;

import java.rmi.RemoteException;
import java.util.Map;

/**
 * Remote implementation of {@link Person}.
 * Delegates account operations to the remote bank.
 */
public final class RemotePerson extends AbstractPerson {
    private final Bank bank;

    /**
     * Constructs a remote person with the specified data.
     *
     * @param id unique identifier
     * @param firstName first name
     * @param lastName surname
     */
    public RemotePerson(final String id, final String firstName, final String lastName, final Bank bank) {
        super(id, firstName, lastName);
        this.bank = bank;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Account getAccount(final String id) throws RemoteException {
        return bank.getAccount(getAccountId(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Account createAccount(final String id) throws RemoteException {
        return bank.createAccount(getAccountId(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, ? extends Account> getAccounts() throws RemoteException {
        return bank.getAccounts(getId());
    }
}