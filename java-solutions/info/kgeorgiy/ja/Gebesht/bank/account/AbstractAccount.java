package info.kgeorgiy.ja.Gebesht.bank.account;

import java.io.Serial;
import java.io.Serializable;

/**
 * Base implementation of {@link Account}.
 * Stores common account data shared by local and remote accounts.
 */
public abstract class AbstractAccount implements Account, Serializable {

    /**
     * Serialization version identifier.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Unique account identifier.
     */
    private final String id;

    /**
     * Current account balance.
     */
    //:NOTE: long
    private int amount;

    /**
     * Constructs an account with zero balance.
     *
     * @param id account identifier
     */
    protected AbstractAccount(final String id) {
        this(id, 0);
    }

    /**
     * Constructs an account with the specified balance.
     *
     * @param id account identifier
     * @param amount initial balance
     */
    protected AbstractAccount(final String id, final int amount) {
        this.id = id;
        this.amount = amount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getAmount() {
        return amount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAmount(final int amount) {
        this.amount = amount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAmount(final int delta) {
        amount += delta;
    }
}