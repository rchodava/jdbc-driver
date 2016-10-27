package foundation.stack.jdbc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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

    private static final Map<String, ConnectionLookupResult> connectionLookupResults = Collections.synchronizedMap(new HashMap<>());

    public void registerLookup(ConnectionLookup lookup) {
        lookups.addIfAbsent(lookup);
    }

    public void deregisterLookup(ConnectionLookup lookup) {
        lookups.remove(lookup);
    }

    public ConnectionLookupResult lookupConnection(String query) {
        ConnectionLookupResult connectionLookupResult = connectionLookupResults.get(query);
        if (connectionLookupResult != null) {
            return connectionLookupResult;
        }
        for (ConnectionLookup lookup : lookups) {
            String connection = lookup.find(query);
            if (connection != null) {
                connectionLookupResult = new ConnectionLookupResult(lookup, connection);
                connectionLookupResults.putIfAbsent(query, connectionLookupResult);
                return connectionLookupResult;
            }
        }

        return lookupUsingConnectionLookupServices(query);
    }

    private ConnectionLookupResult lookupUsingConnectionLookupServices(String query) {
        ServiceLoader<ConnectionLookup> lookups = ServiceLoader.load(ConnectionLookup.class);
        for (ConnectionLookup lookup : lookups) {
            String connection = lookup.find(query);
            if (connection != null) {
                ConnectionLookupResult connectionLookupResult = new ConnectionLookupResult(lookup, connection);
                connectionLookupResults.putIfAbsent(query, connectionLookupResult);
                return connectionLookupResult;
            }
        }

        return null;
    }
}
