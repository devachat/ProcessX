package processx.sync;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import processx.CpuScheduler.AccountManager;
import processx.CpuScheduler.Transaction;
import processx.CpuScheduler.TransactionStatus;
import processx.CpuScheduler.TransactionType;
import processx.Synchronization.BankSyncManager;
import processx.Synchronization.DeadlockReport;
import processx.Synchronization.RaceReport;

/**
 * RED PHASE (Sprint 3: Synchronization & Concurrency)
 * JUnit 5 test suite verifying race condition elimination and deadlock prevention.
 */
public class SyncRedTest {

    private AccountManager accMgr;
    private BankSyncManager syncMgr;

    @BeforeEach
    void setUp() {
        // Initialize account manager and synchronization manager
        accMgr = new AccountManager();
        syncMgr = new BankSyncManager(4);
    }

    // Test 1: Verify concurrent withdrawal consistency under insufficient balance
    @Test
    @DisplayName("Verify only one withdrawal succeeds when balance cannot cover both")
    void testConcurrentWithdrawalConsistency() throws InterruptedException {
        accMgr.createAccount(201, "Test User", 1000.0);
        Transaction t1 = new Transaction(1, TransactionType.WITHDRAW, 201, 0, 700.0);
        Transaction t2 = new Transaction(2, TransactionType.WITHDRAW, 201, 0, 700.0);

        boolean[] results = new boolean[2];
        Thread th1 = new Thread(() -> results[0] = syncMgr.executeSafeTransaction(t1, accMgr, true));
        Thread th2 = new Thread(() -> results[1] = syncMgr.executeSafeTransaction(t2, accMgr, true));

        th1.start();
        th2.start();
        th1.join();
        th2.join();

        assertNotEquals(results[0], results[1], "Only one transaction can succeed on insufficient mutual balance");
        assertEquals(300.0, accMgr.getAccount(201).balance, 1e-4, "Remaining balance must be exactly $300.00");
    }

    // Test 2: Verify thread-safe balance preservation in multi-threaded environment
    @Test
    @DisplayName("Verify synchronized operations preserve exact expected balance")
    void testThreadSafeBalancePreservation() {
        RaceReport rep = syncMgr.runRaceConditionDemo(accMgr, 501, 50, 10.0);
        assertEquals(rep.expectedFinalBalance, rep.actualSafeBalance, 1e-4, "Safe balance must equal expected balance");
    }

    // Test 3: Verify lock ordering completely eliminates circular wait deadlocks
    @Test
    @DisplayName("Verify ascending lock ordering prevents circular-wait deadlock")
    void testDeadlockPrevention() {
        DeadlockReport rep = syncMgr.runDeadlockDemo(accMgr, 601, 602, true);
        assertFalse(rep.circularWaitOccurred, "Circular wait must not occur when lock ordering is enabled");
        assertEquals(2, rep.completedCount, "Both concurrent transfer transactions must complete");
    }

    // Test 4: Verify single withdrawal transaction fails when requested amount exceeds balance
    @Test
    @DisplayName("Verify withdrawal rejection when requested amount exceeds balance")
    void testInsufficientBalanceRejection() {
        accMgr.createAccount(301, "Source User", 500.0);
        Transaction tx = new Transaction(1, TransactionType.WITHDRAW, 301, 0, 800.0);
        boolean success = syncMgr.executeSafeTransaction(tx, accMgr);

        assertFalse(success, "Transaction must return false on insufficient funds");
        assertEquals(TransactionStatus.FAILED_INSUFFICIENT_FUNDS, tx.status, "Status must be FAILED_INSUFFICIENT_FUNDS");
        assertEquals(500.0, accMgr.getAccount(301).balance, 1e-4, "Original balance of $500.00 must be preserved");
    }
}
