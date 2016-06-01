package foundation.stack.jdbc;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public class ConnectionLookupResult {
    private final String connectionString;
    private final ConnectionLookup lookup;

    public ConnectionLookupResult(ConnectionLookup lookup, String connectionString) {
        this.lookup = lookup;
        this.connectionString = connectionString;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public ConnectionLookup getLookup() {
        return lookup;
    }
}
