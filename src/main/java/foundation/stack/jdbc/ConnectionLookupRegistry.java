package foundation.stack.jdbc;

import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public class ConnectionLookupRegistry {
    private static final ConnectionLookupRegistry registry = new ConnectionLookupRegistry();

    public static ConnectionLookupRegistry getRegistry() {
        return registry;
    }

    private final CopyOnWriteArrayList<ConnectionLookup> lookups = new CopyOnWriteArrayList<>();

    private ConnectionLookupRegistry() {
    }

    public void registerLookup(ConnectionLookup lookup) {
        lookups.addIfAbsent(lookup);
    }

    public void deregisterLookup(ConnectionLookup lookup) {
        lookups.remove(lookup);
    }

    public ConnectionLookupResult lookupConnection(String query) {
        for (ConnectionLookup lookup : lookups) {
            String connection = lookup.find(query);
            if (connection != null) {
                return new ConnectionLookupResult(lookup, connection);
            }
        }

        return lookupUsingConnectionLookupServices(query);
    }

    private ConnectionLookupResult lookupUsingConnectionLookupServices(String query) {
        ServiceLoader<ConnectionLookup> lookups = ServiceLoader.load(ConnectionLookup.class);
        for (ConnectionLookup lookup : lookups) {
            String connection = lookup.find(query);
            if (connection != null) {
                return new ConnectionLookupResult(lookup, connection);
            }
        }

        return null;
    }
}
