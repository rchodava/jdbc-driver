package foundation.stack.test;

import foundation.stack.jdbc.DockerDatabaseServerPerApplicationConnectionLookup;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
@Ignore
public class DockerDatabaseServerPerApplicationConnectionLookupTest {
    @Test
    public void testFind() {
        DockerDatabaseServerPerApplicationConnectionLookup lookup = new DockerDatabaseServerPerApplicationConnectionLookup();
        System.out.println(lookup.find("branch"));
    }
}
