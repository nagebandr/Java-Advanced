package info.kgeorgiy.ja.Gebesht.bank.person;

import info.kgeorgiy.ja.Gebesht.bank.account.LocalAccount;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Local snapshot implementation of {@link Person}.
 * Stores accounts independently from the remote bank state.
 */
public final class LocalPerson extends AbstractPerson implements Serializable {

    /**
     * Serialization version identifier.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Accounts owned by this local person.
     */
    private final Map<String, LocalAccount> accounts;

    /**
     * Constructs a local person with the specified data.
     *
     * @param passportId unique identifier
     * @param firstName first name
     * @param lastName surname
     */
    public LocalPerson(final String passportId, final String firstName, final String lastName) {
        super(passportId, firstName, lastName);
        this.accounts = new HashMap<>();
    }

    /**
     * Adds an account snapshot with the specified amount.
     *
     * @param subId account sub-identifier
     * @param amount initial account amount
     */
    public void addAccount(final String subId, final int amount) {
        final String accountId = getAccountId(subId);
        final LocalAccount account = new LocalAccount(accountId);
        account.setAmount(amount);
        accounts.put(subId, account);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LocalAccount getAccount(final String id) {
        return accounts.get(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LocalAccount createAccount(final String id) {
        return accounts.computeIfAbsent(id, key -> new LocalAccount(getAccountId(key)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, LocalAccount> getAccounts() {
        return new HashMap<>(accounts);
    }
}