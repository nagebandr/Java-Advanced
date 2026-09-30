package info.kgeorgiy.ja.Gebesht.bank.person;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Base implementation of {@link Person}.
 * Stores common personal information shared by local and remote persons.
 */
public abstract class AbstractPerson implements Person, Serializable {

    /**
     * Serialization version identifier.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier of the person.
     */
    private final String id;

    /**
     * First name of the person.
     */
    private final String name;

    /**
     * Surname of the person.
     */
    private final String surname;

    /**
     * Constructs a person with the specified data.
     *
     * @param id unique identifier
     * @param name first name
     * @param surname surname
     */
    protected AbstractPerson(final String id, final String name, final String surname) {
        this.id = id;
        this.name = name;
        this.surname = surname;
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
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSurname() {
        return surname;
    }

    /**
     * Returns a full account identifier in the format {@code passport:subId}.
     *
     * @param id account sub-identifier
     * @return full account identifier
     */
    public String getAccountId(final String id) {
        return getId() + ":" + id;
    }

    /**
     * Compares persons by their identifiers.
     *
     * @param o object to compare
     * @return {@code true} if identifiers are equal
     */
    @Override
    public boolean equals(final Object o) {
        if (o == null) {
            return false;
        }

        if (o == this) {
            return true;
        }

        if (o instanceof final AbstractPerson other) {
            return Objects.equals(getId(), other.getId());
        }

        return false;
    }

    /**
     * Returns the hash code of this person.
     *
     * @return hash code based on identifier
     */
    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}