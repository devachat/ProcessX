package processx;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import processx.CpuScheduler;
import processx.CpuScheduler.Account;
import processx.CpuScheduler.AccountManager;
import processx.CpuScheduler.CPUMetrics;
import processx.CpuScheduler.Transaction;
import processx.CpuScheduler.TransactionManager;
import processx.CpuScheduler.TransactionType;
import processx.DiskScheduling.DiskDirection;
import processx.DiskScheduling.DiskOutput;
import processx.DiskScheduling.DiskScheduler;
import processx.DiskScheduling.PerformanceSummary;
import processx.DiskScheduling.TransactionLogger;
import processx.MemoryManagement.MemoryManager;
import processx.MemoryManagement.MemoryStats;
import processx.MemoryManagement.MemoryStrategy;
import processx.Synchronization.BankSyncManager;
import processx.Synchronization.BatchSyncReport;
import processx.Synchronization.DeadlockReport;

public class Main {

    public static void runIntegratedPipeline(AccountManager accMgr, TransactionManager txMgr,
                                           BankSyncManager syncMgr, MemoryManager memMgr,
                                           TransactionLogger logger, int cpuChoice, int memChoice, int diskChoice) {
        List<Transaction> workload = txMgr.getTransactions();
        if (workload.isEmpty()) {
            txMgr.generateWorkload(8);
        }

        PerformanceSummary summary = new PerformanceSummary();

        System.out.println("\n===============================================================================");
        System.out.println("          INTEGRATED BANKING OS RESOURCE MANAGEMENT SIMULATION PIPELINE         ");
        System.out.println("===============================================================================");

        System.out.println("\n>>> [STAGE 1: TRANSACTION MANAGER & READY QUEUE] <<<");
        txMgr.displayWorkload();
        logger.log("WORKLOAD", -1, "Loaded " + workload.size() + " transaction processes.");

        System.out.println("\n>>> [STAGE 2: CPU SCHEDULING] <<<");
        CPUMetrics cpuRes;
        if (cpuChoice == 1) cpuRes = CpuScheduler.scheduleFCFS(workload);
        else if (cpuChoice == 2) cpuRes = CpuScheduler.scheduleSJF(workload, false);
        else if (cpuChoice == 4) cpuRes = CpuScheduler.schedulePriority(workload, false);
        else cpuRes = CpuScheduler.scheduleRoundRobin(workload, 2);

        cpuRes.printGantt();
        cpuRes.printMetrics();

        summary.cpuAlgo = cpuRes.algorithmName;
        summary.cpuWait = cpuRes.avgWaiting;
        summary.cpuTAT = cpuRes.avgTurnaround;
        summary.cpuResp = cpuRes.avgResponse;
        summary.cpuUtil = cpuRes.cpuUtil;
        summary.cpuThroughput = cpuRes.throughput;
        logger.log("CPU_SCHED", -1, "Scheduled transactions via " + cpuRes.algorithmName);

        System.out.println("\n>>> [STAGE 3: MEMORY ALLOCATION FOR TRANSACTIONS] <<<");
        MemoryStrategy memStrat = MemoryStrategy.BEST_FIT;
        if (memChoice == 1) memStrat = MemoryStrategy.FIRST_FIT;
        else if (memChoice == 3) memStrat = MemoryStrategy.WORST_FIT;
        else if (memChoice == 4) memStrat = MemoryStrategy.NEXT_FIT;

        summary.memAlgo = memMgr.getStrategyName(memStrat);
        for (Transaction tx : workload) {
            boolean ok = memMgr.allocate(tx.txId, tx.memoryNeeded, memStrat, "TX" + tx.txId);
            logger.log("MEMORY", tx.txId, (ok ? "Allocated " : "Failed to allocate ") + tx.memoryNeeded + " KB (" + summary.memAlgo + ")");
        }
        memMgr.displayMemoryMap();
        memMgr.displayStats("Memory State (Pre-Execution)");

        MemoryStats mStats = memMgr.getStats();
        summary.memTotal = mStats.totalMemory;
        summary.memAllocated = mStats.allocatedMemory;
        summary.memUtil = mStats.utilization;
        summary.memFrag = mStats.externalFragmentationBytes;
        summary.memHoles = mStats.holeCount;

        System.out.println("\n>>> [STAGE 4: SYNCHRONIZED CONCURRENT EXECUTION] <<<");
        BatchSyncReport syncRes = syncMgr.processBatch(workload, accMgr, 4, true, true);
        syncMgr.displayBatchReport(syncRes);

        for (Transaction tx : workload) {
            String desc = "Type: " + tx.getTypeString() +
                    " | Src: " + tx.sourceAccountId +
                    " | Dest: " + tx.targetAccountId +
                    " | Amt: " + tx.amount +
                    " | Status: " + tx.getStatusString();
            logger.log("BANK_TX", tx.txId, desc);
        }
        summary.totalTxs = syncRes.totalTransactions;
        summary.successfulTxs = syncRes.successfulTransactions;
        summary.syncDuration = syncRes.executionTimeMs;

        System.out.println("\n>>> [STAGE 5: TRANSACTION LOG DISK I/O SCHEDULING] <<<");
        List<Integer> diskTracks = new ArrayList<>();
        for (Transaction tx : workload) {
            diskTracks.add(tx.diskTrackNeeded);
        }

        DiskOutput diskRes;
        if (diskChoice == 1) diskRes = DiskScheduler.scheduleFCFS(50, diskTracks);
        else if (diskChoice == 2) diskRes = DiskScheduler.scheduleSSTF(50, diskTracks);
        else if (diskChoice == 3) diskRes = DiskScheduler.scheduleSCAN(50, diskTracks, 200, DiskDirection.RIGHT_DIRECTION);
        else if (diskChoice == 4) diskRes = DiskScheduler.scheduleCSCAN(50, diskTracks, 200, DiskDirection.RIGHT_DIRECTION);
        else if (diskChoice == 6) diskRes = DiskScheduler.scheduleCLOOK(50, diskTracks, DiskDirection.RIGHT_DIRECTION);
        else diskRes = DiskScheduler.scheduleLOOK(50, diskTracks, DiskDirection.RIGHT_DIRECTION);

        diskRes.print();
        summary.diskAlgo = diskRes.algorithmName;
        summary.diskMovement = diskRes.totalHeadMovements;
        summary.diskAvgSeek = diskRes.avgSeekDistance;
        logger.log("DISK_IO", -1, "Processed " + diskTracks.size() + " disk requests via " + diskRes.algorithmName);

        System.out.println("\n>>> [STAGE 6: MEMORY DEALLOCATION & LOGGING] <<<");
        for (Transaction tx : workload) {
            memMgr.deallocate(tx.txId);
        }
        logger.log("MEMORY", -1, "Deallocated completed transaction memory blocks.");
        memMgr.displayStats("Memory State (Post-Deallocation)");

        logger.saveToFile("transaction_log.txt");
        System.out.println("\n[Audit Logger] Detailed audit log saved to transaction_log.txt");

        System.out.println("\n>>> [STAGE 7: UPDATED ACCOUNT BALANCES] <<<");
        accMgr.displayAccounts();

        System.out.println("\n>>> [STAGE 8: PERFORMANCE ANALYZER REPORT] <<<");
        summary.display();
    }

    public static void runTestSuite() {
        System.out.println("\n====================================================================");
        System.out.println("      RUNNING FULL TEST SUITE (TC01 - TC08) FROM SYNOPSIS           ");
        System.out.println("====================================================================");
        int passed = 0;

        // TC01
        System.out.println("\n[TEST CASE TC01: Concurrent Withdrawal Consistency]");
        {
            AccountManager accMgr = new AccountManager();
            accMgr.createAccount(201, "Test User TC01", 1000.0);
            BankSyncManager syncMgr = new BankSyncManager(2);

            Transaction t1 = new Transaction(1, TransactionType.WITHDRAW, 201, 0, 700.0);
            Transaction t2 = new Transaction(2, TransactionType.WITHDRAW, 201, 0, 700.0);

            final boolean[] r = new boolean[2];
            Thread th1 = new Thread(() -> r[0] = syncMgr.executeSafeTransaction(t1, accMgr, true));
            Thread th2 = new Thread(() -> r[1] = syncMgr.executeSafeTransaction(t2, accMgr, true));
            th1.start();
            th2.start();
            try {
                th1.join();
                th2.join();
            } catch (InterruptedException ignored) {}

            double bal = accMgr.getAccount(201).balance;
            boolean pass = (r[0] != r[1]) && (Math.abs(bal - 300.0) < 1e-4);
            System.out.println("Tx1 Withdraw: " + (r[0] ? "SUCCESS" : "REJECTED"));
            System.out.println("Tx2 Withdraw: " + (r[1] ? "SUCCESS" : "REJECTED"));
            System.out.printf("Final Balance: $%.2f | Status: %s%n", bal, (pass ? "PASS" : "FAIL"));
            if (pass) passed++;
        }

        // TC02
        System.out.println("\n[TEST CASE TC02: CPU Scheduling Verification]");
        {
            List<Transaction> workload = Arrays.asList(
                    new Transaction(1, TransactionType.DEPOSIT, 101, 0, 100, 0, 4, 2, 64, 50),
                    new Transaction(2, TransactionType.WITHDRAW, 101, 0, 50, 1, 3, 1, 64, 50),
                    new Transaction(3, TransactionType.BALANCE_INQUIRY, 101, 0, 0, 2, 1, 3, 64, 50)
            );
            CPUMetrics rFCFS = CpuScheduler.scheduleFCFS(workload);
            CPUMetrics rSJF = CpuScheduler.scheduleSJF(workload, false);
            CPUMetrics rRR = CpuScheduler.scheduleRoundRobin(workload, 2);
            CPUMetrics rPrio = CpuScheduler.schedulePriority(workload, false);

            boolean pass = (rFCFS.scheduledList.size() == 3) && (rSJF.scheduledList.size() == 3) &&
                    (rRR.scheduledList.size() == 3) && (rPrio.scheduledList.size() == 3);
            System.out.printf("Metrics Calculated: FCFS TAT=%.2fms, SJF TAT=%.2fms%n", rFCFS.avgTurnaround, rSJF.avgTurnaround);
            System.out.println("Status: " + (pass ? "PASS" : "FAIL"));
            if (pass) passed++;
        }

        // TC03
        System.out.println("\n[TEST CASE TC03: Memory Allocation Comparison]");
        {
            MemoryManager mm = new MemoryManager(500);
            boolean a1 = mm.allocate(1, 100, MemoryStrategy.FIRST_FIT, "TX1");
            boolean a2 = mm.allocate(2, 200, MemoryStrategy.BEST_FIT, "TX2");
            boolean a3 = mm.allocate(3, 150, MemoryStrategy.WORST_FIT, "TX3");
            MemoryStats s1 = mm.getStats();
            boolean d1 = mm.deallocate(2);
            MemoryStats s2 = mm.getStats();

            boolean pass = a1 && a2 && a3 && d1 && (s1.allocatedMemory == 450) && (s2.allocatedMemory == 250);
            System.out.printf("Allocated Before: %d KB | After Deallocating TX2: %d KB%n", s1.allocatedMemory, s2.allocatedMemory);
            System.out.println("Status: " + (pass ? "PASS" : "FAIL"));
            if (pass) passed++;
        }

        // TC04
        System.out.println("\n[TEST CASE TC04: Disk Scheduling Verification]");
        {
            List<Integer> reqs = Arrays.asList(98, 183, 37, 122, 14, 124, 65, 67);
            DiskOutput rFCFS = DiskScheduler.scheduleFCFS(53, reqs);
            DiskOutput rSSTF = DiskScheduler.scheduleSSTF(53, reqs);
            DiskOutput rLOOK = DiskScheduler.scheduleLOOK(53, reqs, DiskDirection.RIGHT_DIRECTION);

            boolean pass = (rFCFS.totalHeadMovements == 640) &&
                    (rSSTF.totalHeadMovements == 236) &&
                    (rLOOK.totalHeadMovements == 299);
            System.out.println("FCFS Head Movement: " + rFCFS.totalHeadMovements + " (Expected: 640)");
            System.out.println("SSTF Head Movement: " + rSSTF.totalHeadMovements + " (Expected: 236)");
            System.out.println("LOOK Head Movement: " + rLOOK.totalHeadMovements + " (Expected: 299)");
            System.out.println("Status: " + (pass ? "PASS" : "FAIL"));
            if (pass) passed++;
        }

        // TC05
        System.out.println("\n[TEST CASE TC05: Insufficient Balance]");
        {
            AccountManager accMgr = new AccountManager();
            accMgr.createAccount(301, "Source User", 500.0);
            BankSyncManager syncMgr = new BankSyncManager();

            Transaction txW = new Transaction(1, TransactionType.WITHDRAW, 301, 0, 800.0);
            boolean ok = syncMgr.executeSafeTransaction(txW, accMgr);
            double bal = accMgr.getAccount(301).balance;

            boolean pass = (!ok) && (Math.abs(bal - 500.0) < 1e-4);
            System.out.println("Withdraw $800 from $500 balance: " + (ok ? "ALLOWED" : "REJECTED (Correct)"));
            System.out.println("Balance Preserved: $" + bal + " | Status: " + (pass ? "PASS" : "FAIL"));
            if (pass) passed++;
        }

        // TC06
        System.out.println("\n[TEST CASE TC06: Deadlock-Prevention Scenario]");
        {
            AccountManager accMgr = new AccountManager();
            BankSyncManager syncMgr = new BankSyncManager();
            DeadlockReport rep = syncMgr.runDeadlockDemo(accMgr, 401, 402, true);

            boolean pass = (!rep.circularWaitOccurred) && (rep.completedCount == 2);
            System.out.println("Circular Wait Occurred: " + (rep.circularWaitOccurred ? "YES" : "NO"));
            System.out.println("Operations Completed: " + rep.completedCount + " / 2");
            System.out.println("Status: " + (pass ? "PASS" : "FAIL"));
            if (pass) passed++;
        }

        // TC07
        System.out.println("\n[TEST CASE TC07: Transaction Logging]");
        {
            TransactionLogger logger = new TransactionLogger();
            logger.log("DEPOSIT", 1, "Deposited $500");
            logger.log("WITHDRAW", 2, "Withdrew $200");
            logger.saveToFile("tc07_log.txt");

            File f = new File("tc07_log.txt");
            boolean pass = f.exists() && f.length() > 0 && (logger.count() == 2);
            System.out.println("Log File Created & Verified | Count: " + logger.count() + " | Status: " + (pass ? "PASS" : "FAIL"));
            if (pass) passed++;
        }

        // TC08
        System.out.println("\n[TEST CASE TC08: Stress Workload]");
        {
            AccountManager accMgr = new AccountManager();
            accMgr.createAccount(1, "Acc 1", 50000.0);
            accMgr.createAccount(2, "Acc 2", 50000.0);

            TransactionManager txMgr = new TransactionManager();
            for (int i = 0; i < 100; ++i) {
                txMgr.addTransaction(TransactionType.DEPOSIT, 1, 0, 10.0);
            }

            BankSyncManager syncMgr = new BankSyncManager(8);
            BatchSyncReport bRes = syncMgr.processBatch(txMgr.getTransactions(), accMgr, 8, true, true);
            double finalBal = accMgr.getAccount(1).balance;

            boolean pass = (bRes.successfulTransactions == 100) && (Math.abs(finalBal - 51000.0) < 1e-4);
            System.out.printf("100 Concurrent Deposits Processed | Final Balance: $%.2f%n", finalBal);
            System.out.println("Status: " + (pass ? "PASS" : "FAIL"));
            if (pass) passed++;
        }

        System.out.println("\n====================================================================");
        System.out.println("TEST SUITE SUMMARY: " + passed + " / 8 PASSED");
        System.out.println("====================================================================");
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--test")) {
            runTestSuite();
            return;
        }

        AccountManager accMgr = new AccountManager();
        TransactionManager txMgr = new TransactionManager();
        BankSyncManager syncMgr = new BankSyncManager();
        MemoryManager memMgr = new MemoryManager(1024);
        TransactionLogger logger = new TransactionLogger();

        accMgr.initializeDefaultAccounts();

        System.out.println("\n===============================================================================");
        System.out.println("                          OPERATING SYSTEMS & AGILE                            ");
        System.out.println("            Process X - OS Resource Management Simulator                       ");
        System.out.println("                       Academic Year 2026-2027                                 ");
        System.out.println("===============================================================================");
        System.out.println(" Project: Process X (Banking OS Resource Management Simulator)");
        System.out.println(" Methodology: Agile Development (TDD Red-Green-Refactor)");
        System.out.println(" Language: Java 17");
        System.out.println("===============================================================================");

        Scanner scanner = new Scanner(System.in);
        int choice = 0;

        while (true) {
            System.out.println("\n------------------------------ MAIN MENU ------------------------------");
            System.out.println(" 1. Run Complete Integrated Simulation Pipeline (End-to-End OS Flow)");
            System.out.println(" 2. CPU Scheduling Module (FCFS, SJF, Round Robin, Priority & Gantt)");
            System.out.println(" 3. Synchronization Module (Race Condition & Deadlock Demos)");
            System.out.println(" 4. Memory Management Module (First/Best/Worst/Next Fit & Compaction)");
            System.out.println(" 5. Disk Scheduling Module (FCFS, SSTF, SCAN, C-SCAN, LOOK, C-LOOK)");
            System.out.println(" 6. Run Complete Test Suite (TC01 - TC08 Verification)");
            System.out.println(" 7. Display Accounts & Audit Logs");
            System.out.println(" 8. Exit Simulator");
            System.out.println("------------------------------------------------------------------------");
            System.out.print("Enter your choice (1-8): ");

            if (!scanner.hasNextInt()) {
                if (!scanner.hasNext()) break;
                scanner.next();
                continue;
            }
            choice = scanner.nextInt();

            if (choice == 8) {
                System.out.println("\nExiting Process X Simulator. Goodbye!");
                break;
            }

            switch (choice) {
                case 1: {
                    System.out.print("\nSelect CPU Algorithm: 1. FCFS  2. SJF  3. Round Robin  4. Priority: ");
                    int cpuAlgo = scanner.hasNextInt() ? scanner.nextInt() : 3;

                    System.out.print("Select Memory Algorithm: 1. First Fit  2. Best Fit  3. Worst Fit  4. Next Fit: ");
                    int memAlgo = scanner.hasNextInt() ? scanner.nextInt() : 2;

                    System.out.print("Select Disk Algorithm: 1. FCFS 2. SSTF 3. SCAN 4. C-SCAN 5. LOOK 6. C-LOOK: ");
                    int diskAlgo = scanner.hasNextInt() ? scanner.nextInt() : 5;

                    accMgr.clear();
                    accMgr.initializeDefaultAccounts();
                    txMgr.clear();
                    txMgr.generateWorkload(8);
                    memMgr.reset();
                    logger.clear();

                    runIntegratedPipeline(accMgr, txMgr, syncMgr, memMgr, logger, cpuAlgo, memAlgo, diskAlgo);
                    break;
                }
                case 2: {
                    TransactionManager tm = new TransactionManager();
                    tm.generateWorkload(6);
                    tm.displayWorkload();
                    CpuScheduler.compareAll(tm.getTransactions(), 2);
                    CPUMetrics rr = CpuScheduler.scheduleRoundRobin(tm.getTransactions(), 2);
                    rr.printGantt();
                    rr.printMetrics();
                    break;
                }
                case 3: {
                    AccountManager am = new AccountManager();
                    BankSyncManager sm = new BankSyncManager();
                    System.out.println("\n[1] Race Condition Demonstration:");
                    var rRep = sm.runRaceConditionDemo(am, 501, 100, 10.0);
                    sm.displayRaceReport(rRep);

                    System.out.println("\n[2] Deadlock Prevention Demonstration (Lock Ordering):");
                    var dSafe = sm.runDeadlockDemo(am, 601, 602, true);
                    sm.displayDeadlockReport(dSafe);

                    System.out.println("\n[3] Deadlock Demonstration (Arbitrary Lock Order):");
                    var dHit = sm.runDeadlockDemo(am, 603, 604, false);
                    sm.displayDeadlockReport(dHit);
                    break;
                }
                case 4: {
                    MemoryManager mm = new MemoryManager(1024);
                    mm.allocate(101, 128, MemoryStrategy.BEST_FIT, "TX101");
                    mm.allocate(102, 256, MemoryStrategy.BEST_FIT, "TX102");
                    mm.allocate(103, 64, MemoryStrategy.BEST_FIT, "TX103");
                    mm.allocate(104, 300, MemoryStrategy.BEST_FIT, "TX104");
                    mm.displayMemoryMap();
                    mm.displayStats("Initial State");

                    System.out.println("\nDeallocating TX102...");
                    mm.deallocate(102);
                    mm.displayMemoryMap();
                    mm.displayStats("After TX102 Deallocation");

                    System.out.println("\nPerforming Compaction...");
                    mm.compact();
                    mm.displayMemoryMap();
                    mm.displayStats("After Compaction");

                    List<int[]> reqs = Arrays.asList(
                            new int[]{1, 100},
                            new int[]{2, 200},
                            new int[]{3, 50},
                            new int[]{4, 150},
                            new int[]{5, 80}
                    );
                    MemoryManager.compareStrategies(1024, reqs);
                    break;
                }
                case 5: {
                    List<Integer> reqs = Arrays.asList(98, 183, 37, 122, 14, 124, 65, 67);
                    DiskScheduler.compareAll(53, reqs, 200, DiskDirection.RIGHT_DIRECTION);
                    DiskOutput lookRes = DiskScheduler.scheduleLOOK(53, reqs, DiskDirection.RIGHT_DIRECTION);
                    lookRes.print();
                    break;
                }
                case 6: {
                    runTestSuite();
                    break;
                }
                case 7: {
                    accMgr.displayAccounts();
                    logger.displayLogs(15);
                    break;
                }
                default:
                    System.out.println("Invalid choice! Enter 1-8.");
                    break;
            }
        }
    }
}
