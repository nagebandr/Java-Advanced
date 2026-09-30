package info.kgeorgiy.ja.Gebesht.bank;

import java.rmi.*;
import java.rmi.server.*;
import java.net.*;

public final class Server {
    private final static int DEFAULT_PORT = 8888;
    private final static String DEFAULT_URL = BankUtilities.DEFAULT_BANK_URL;

    public static void main(final String... args) {
        final int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        final String url = args.length > 1 ? args[1] : DEFAULT_URL;

        final Bank bank = new RemoteBank(port);
        try {
            UnicastRemoteObject.exportObject(bank, port);
            Naming.rebind(url, bank);
            System.out.println("Server started on port " + port + " at URL " + url);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Naming.unbind(url);
                    UnicastRemoteObject.unexportObject(bank, true);
                    System.out.println("Server stopped gracefully");
                } catch (final Exception e) {
                    System.err.println("Error during shutdown: " + e.getMessage());
                    e.printStackTrace();
                }
            }));

        } catch (final RemoteException e) {
            System.err.println("Cannot export object: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (final MalformedURLException e) {
            System.err.println("Malformed URL: " + url);
            e.printStackTrace();
            System.exit(1);
        }
    }
}
