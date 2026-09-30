package info.kgeorgiy.ja.Gebesht.bank.account;

/**
 * Remote implementation of {@link Account}.
 * Provides synchronized access to the account balance.
 */
public final class RemoteAccount extends AbstractAccount {

    /**
     * Constructs a remote account with the specified identifier.
     *
     * @param id account identifier
     */
    public RemoteAccount(final String id) {
        super(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized int getAmount() {
        return super.getAmount();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void setAmount(final int amount) {
        super.setAmount(amount);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void addAmount(final int delta) {
        super.addAmount(delta);
    }
}