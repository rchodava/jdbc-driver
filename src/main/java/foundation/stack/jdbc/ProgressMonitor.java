package foundation.stack.jdbc;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public interface ProgressMonitor {
    void statusChanged(String status);
    void workChanged(int workCompleted, int totalWork);

    ProgressMonitor NULL = new ProgressMonitor() {
        @Override
        public void statusChanged(String status) {
        }

        @Override
        public void workChanged(int workCompleted, int totalWork) {
        }
    };
}
