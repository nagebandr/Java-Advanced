package info.kgeorgiy.ja.Gebesht.bank;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

/**
 * Utility class for obtaining Bank instances from the RMI registry.
 * Provides convenient methods to look up Bank remote objects using standard RMI URLs.
 */
public final class BankUtilities {
    /**
     * Default URL for the bank in the RMI registry.
     */
    public static final String DEFAULT_BANK_URL = "//localhost/bank";

    /**
     * Utility class.
     */
    private BankUtilities() {
    }

    /**
     * Gets a bank instance from the RMI registry by URL.
     * @param bankURL the URL of the bank in the RMI registry (e.g., "//localhost/bank")
     * @return the Bank instance
     * @throws RemoteException if the bank cannot be accessed, not found, or URL is invalid
     */
    public static Bank getBank(final String bankURL) throws RemoteException {
        try {
            return (Bank) Naming.lookup(bankURL);
        } catch (final NotBoundException e) {
            throw new RemoteException("Bank not found in registry", e);
        } catch (final MalformedURLException e) {
            throw new RemoteException("Invalid bank URL", e);
        }
    }

    /**
     * Gets a bank instance from the RMI registry using the default URL.
     * @return the Bank instance
     * @throws RemoteException if the bank cannot be accessed
     */
    public static Bank getBank() throws RemoteException {
        return getBank(DEFAULT_BANK_URL);
    }
}
