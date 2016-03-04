package foundation.stack.jdbc;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public interface ConnectionLookup {
    String find(String query);
}
