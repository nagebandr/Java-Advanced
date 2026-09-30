package info.kgeorgiy.ja.Gebesht.bank;

import info.kgeorgiy.ja.Gebesht.bank.account.Account;
import info.kgeorgiy.ja.Gebesht.bank.person.Person;

import java.rmi.RemoteException;

/**
 * Command-line client for the bank application.
 * Connects to a remote bank server and performs account operations for a specified person.
 *
 * Usage: java Client <name> <surname> <passport> <subId> <delta>
 * - name: first name of the person
 * - surname: last name of the person
 * - passport: unique passport number of the person
 * - subId: account identifier for this person
 * - delta: amount to add to the account balance
 */
public final class Client {
    /**
     * Utility class.
     */
    private Client() {
    }

    /**
     * Main entry point for the client.
     * Processes command-line arguments, connects to the bank, and performs the requested operation.
     * Prints appropriate messages for each operation and outputs the final account balance.
     *
     * @param args command-line arguments: name, surname, passport, subId, delta
     */
    public static void main(final String[] args) {
        try {
            ClientService.execute(args);
        } catch (final IllegalArgumentException | RemoteException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
