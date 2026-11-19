package processx.sync;

import processx.CpuScheduler.AccountManager;
import processx.Synchronization.BankSyncManager;
import processx.Synchronization.DeadlockReport;

public class SyncRefactorTest {

    public static void run() {
        System.out.println("\n--------------------------------------------------------------");
        System.out.println(" >>> [SYNCHRONIZATION: REFACTOR PHASE] <<<");
        System.out.println("--------------------------------------------------------------");

        AccountManager accMgr = new AccountManager();
        BankSyncManager syncMgr = new BankSyncManager(4);

        DeadlockReport rep = syncMgr.runDeadlockDemo(accMgr, 601, 602, true);
        boolean pass = !rep.circularWaitOccurred && (rep.completedCount == 2);

        System.out.printf(" Circular Wait Occurred : %s%n", (rep.circularWaitOccurred ? "YES (DEADLOCK)" : "NO (PREVENTED)"));
        System.out.printf(" Completed Transactions : %d / 2%n", rep.completedCount);
        System.out.println(" Details: " + rep.details);
        System.out.println(" Status: " + (pass ? "REFACTORED (Deadlock-Free Concurrent Execution)" : "FAIL"));
    }

    public static void main(String[] args) {
        run();
    }
}
