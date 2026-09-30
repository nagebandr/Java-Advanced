package info.kgeorgiy.ja.Gebesht.bank;

import info.kgeorgiy.ja.Gebesht.bank.account.Account;
import info.kgeorgiy.ja.Gebesht.bank.person.Person;

import java.rmi.RemoteException;

public final class ClientService {
    /**
     * Executes the bank operation with the given arguments.
     *
     * If the person exists, validates their data matches the provided name and surname.
     * Creates the person if they don't exist.
     * Creates the account if it doesn't exist for that person.
     * Updates the account balance by adding the delta value.
     *
     * @param args command-line arguments: name, surname, passport, subId, delta
     * @throws IllegalArgumentException if arguments are invalid or incomplete
     * @throws RemoteException          if a remote bank operation fails
     */
    public static void execute(final String[] args) throws RemoteException {
        if (args.length != 5) {
            throw new IllegalArgumentException("Usage: java Client <name> <surname> <passport> <subId> <delta>");
        }

        final Bank bank = BankUtilities.getBank();

        final String name = args[0];
        final String surname = args[1];
        final String personId = args[2];
        final String subId = args[3];
        final int delta;
        try {
            delta = Integer.parseInt(args[4]);
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("Error: delta must be an integer, got: " + args[4]);
        }

        Person person = bank.getPerson(personId, true);
        if (person == null) {
            System.out.println("Creating person");
            person = bank.createPerson(personId, name, surname);
        } else {
            System.out.println("Person exists: " + person.getName() + " " + person.getSurname());
            if (!person.getName().equals(name) || !person.getSurname().equals(surname)) {
                throw new RemoteException("Error: Person data mismatch!\n" +
                        "Expected: " + name + " " + surname + "\n" +
                        "Got: " + person.getName() + " " + person.getSurname());
            }
        }

        final Person remotePerson = person;
        Account account = remotePerson.getAccount(subId);
        if (account == null) {
            System.out.println("Creating account via person");
            account = remotePerson.createAccount(subId);
            if (account == null) {
                throw new RemoteException("Error: Failed to create account");
            }
        }

        System.out.println("Account id: " + account.getId());
        System.out.println("Money: " + account.getAmount());
        System.out.println("Adding money: " + delta);
        account.addAmount(delta);
        System.out.println("Money: " + account.getAmount());
    }
}
