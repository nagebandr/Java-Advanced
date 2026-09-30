package info.kgeorgiy.ja.Gebesht.bank.account;

import java.io.Serial;
import java.io.Serializable;

/**
 * Local snapshot implementation of {@link Account}.
 * Stores balance independently from the remote bank state.
 */
public final class LocalAccount extends AbstractAccount implements Serializable {

    /**
     * Serialization version identifier.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a local account with the specified identifier.
     *
     * @param id account identifier
     */
    public LocalAccount(final String id) {
        super(id);
    }
}