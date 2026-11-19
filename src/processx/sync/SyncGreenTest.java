package processx.sync;

import processx.CpuScheduler.AccountManager;
import processx.Synchronization.BankSyncManager;
import processx.Synchronization.RaceReport;

public class SyncGreenTest {

    public static void run() {
        System.out.println("\n--------------------------------------------------------------");
        System.out.println(" >>> [SYNCHRONIZATION: GREEN PHASE] <<<");
        System.out.println("--------------------------------------------------------------");

        AccountManager accMgr = new AccountManager();
        BankSyncManager syncMgr = new BankSyncManager(4);

        RaceReport rep = syncMgr.runRaceConditionDemo(accMgr, 501, 100, 10.0);
        boolean pass = Math.abs(rep.actualSafeBalance - rep.expectedFinalBalance) < 1e-4;

        System.out.printf(" Expected Balance     : $%.2f%n", rep.expectedFinalBalance);
        System.out.printf(" Synchronized Balance : $%.2f%n", rep.actualSafeBalance);
        System.out.println(" Status: " + (pass ? "GREEN (Thread-Safe Consistency Guaranteed)" : "FAIL"));
    }

    public static void main(String[] args) {
        run();
    }
}
