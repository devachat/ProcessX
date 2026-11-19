package processx;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import processx.CpuScheduler.Account;
import processx.CpuScheduler.AccountManager;
import processx.CpuScheduler.Transaction;
import processx.CpuScheduler.TransactionStatus;
import processx.CpuScheduler.TransactionType;

public class Synchronization {

    public static class Semaphore {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition cv = lock.newCondition();
        private int permits;

        public Semaphore(int initialPermits) {
            this.permits = initialPermits;
        }

        public void acquire() {
            lock.lock();
            try {
                while (permits <= 0) {
                    cv.await();
                }
                permits--;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }

        public boolean tryAcquireFor(int timeoutMs) {
            lock.lock();
            try {
                long nanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
                while (permits <= 0) {
                    if (nanos <= 0L) return false;
                    nanos = cv.awaitNanos(nanos);
                }
                permits--;
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } finally {
                lock.unlock();
            }
        }

        public void release() {
            lock.lock();
            try {
                permits++;
                cv.signal();
            } finally {
                lock.unlock();
            }
        }

        public int getAvailable() {
            lock.lock();
            try {
                return permits;
            } finally {
                lock.unlock();
            }
        }
    }

    public static class RaceReport {
        public double initialBalance;
        public double expectedFinalBalance;
        public double actualUnsafeBalance;
        public double actualSafeBalance;
        public int totalOps;
        public int lostUpdates;
        public boolean raceDetected;
    }

    public static class DeadlockReport {
        public boolean preventionEnabled;
        public boolean circularWaitOccurred;
        public int completedCount;
        public double executionTimeMs;
        public String details;
    }

    public static class BatchSyncReport {
        public int totalTransactions;
        public int successfulTransactions;
        public int failedTransactions;
        public double executionTimeMs;
        public boolean syncEnabled;
        public boolean deadlockPrevention;
    }

    public static class BankSyncManager {
        private final Semaphore throttle;
        private final AtomicInteger activeWorkers = new AtomicInteger(0);

        public BankSyncManager(int maxThreads) {
            this.throttle = new Semaphore(maxThreads);
        }

        public BankSyncManager() {
            this(4);
        }

        public boolean executeSafeTransaction(Transaction tx, AccountManager accMgr, boolean orderedLocking) {
            throttle.acquire();
            activeWorkers.incrementAndGet();
            boolean success = false;
            tx.status = TransactionStatus.RUNNING;

            try {
                if (tx.type == TransactionType.DEPOSIT) {
                    Account acc = accMgr.getAccount(tx.sourceAccountId);
                    if (acc != null) {
                        synchronized (acc) {
                            success = acc.deposit(tx.amount);
                            tx.status = success ? TransactionStatus.SUCCESS : TransactionStatus.FAILED_INVALID_ACCOUNT;
                        }
                    } else {
                        tx.status = TransactionStatus.FAILED_INVALID_ACCOUNT;
                    }
                } else if (tx.type == TransactionType.WITHDRAW) {
                    Account acc = accMgr.getAccount(tx.sourceAccountId);
                    if (acc != null) {
                        synchronized (acc) {
                            if (acc.balance >= tx.amount) {
                                success = acc.withdraw(tx.amount);
                                tx.status = success ? TransactionStatus.SUCCESS : TransactionStatus.FAILED_INSUFFICIENT_FUNDS;
                            } else {
                                tx.status = TransactionStatus.FAILED_INSUFFICIENT_FUNDS;
                            }
                        }
                    } else {
                        tx.status = TransactionStatus.FAILED_INVALID_ACCOUNT;
                    }
                } else if (tx.type == TransactionType.BALANCE_INQUIRY) {
                    Account acc = accMgr.getAccount(tx.sourceAccountId);
                    if (acc != null) {
                        synchronized (acc) {
                            acc.history.add("Inquired balance: " + String.format("%.2f", acc.balance));
                            success = true;
                            tx.status = TransactionStatus.SUCCESS;
                        }
                    } else {
                        tx.status = TransactionStatus.FAILED_INVALID_ACCOUNT;
                    }
                } else if (tx.type == TransactionType.TRANSFER) {
                    Account srcAcc = accMgr.getAccount(tx.sourceAccountId);
                    Account destAcc = accMgr.getAccount(tx.targetAccountId);

                    if (srcAcc == null || destAcc == null || tx.sourceAccountId == tx.targetAccountId) {
                        tx.status = TransactionStatus.FAILED_INVALID_ACCOUNT;
                    } else {
                        if (orderedLocking) {
                            Account first = (tx.sourceAccountId < tx.targetAccountId) ? srcAcc : destAcc;
                            Account second = (tx.sourceAccountId < tx.targetAccountId) ? destAcc : srcAcc;

                            synchronized (first) {
                                synchronized (second) {
                                    if (srcAcc.balance >= tx.amount) {
                                        srcAcc.withdraw(tx.amount);
                                        destAcc.deposit(tx.amount);
                                        srcAcc.history.add("Transferred " + String.format("%.2f", tx.amount) + " to Acc " + tx.targetAccountId);
                                        destAcc.history.add("Received " + String.format("%.2f", tx.amount) + " from Acc " + tx.sourceAccountId);
                                        success = true;
                                        tx.status = TransactionStatus.SUCCESS;
                                    } else {
                                        tx.status = TransactionStatus.FAILED_INSUFFICIENT_FUNDS;
                                    }
                                }
                            }
                        } else {
                            synchronized (srcAcc) {
                                try {
                                    Thread.sleep(5);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                synchronized (destAcc) {
                                    if (srcAcc.balance >= tx.amount) {
                                        srcAcc.withdraw(tx.amount);
                                        destAcc.deposit(tx.amount);
                                        success = true;
                                        tx.status = TransactionStatus.SUCCESS;
                                    } else {
                                        tx.status = TransactionStatus.FAILED_INSUFFICIENT_FUNDS;
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                activeWorkers.decrementAndGet();
                throttle.release();
            }
            return success;
        }

        public boolean executeSafeTransaction(Transaction tx, AccountManager accMgr) {
            return executeSafeTransaction(tx, accMgr, true);
        }

        public boolean executeUnsafeTransaction(Transaction tx, AccountManager accMgr) {
            boolean success = false;
            tx.status = TransactionStatus.RUNNING;

            if (tx.type == TransactionType.DEPOSIT) {
                Account acc = accMgr.getAccount(tx.sourceAccountId);
                if (acc != null) {
                    double bal = acc.balance;
                    try {
                        Thread.sleep(0, 50000); // 50 microseconds
                    } catch (InterruptedException ignored) {}
                    acc.balance = bal + tx.amount;
                    success = true;
                    tx.status = TransactionStatus.SUCCESS;
                }
            } else if (tx.type == TransactionType.WITHDRAW) {
                Account acc = accMgr.getAccount(tx.sourceAccountId);
                if (acc != null) {
                    double bal = acc.balance;
                    try {
                        Thread.sleep(0, 50000);
                    } catch (InterruptedException ignored) {}
                    if (bal >= tx.amount) {
                        acc.balance = bal - tx.amount;
                        success = true;
                        tx.status = TransactionStatus.SUCCESS;
                    } else {
                        tx.status = TransactionStatus.FAILED_INSUFFICIENT_FUNDS;
                    }
                }
            }
            return success;
        }

        public BatchSyncReport processBatch(List<Transaction> batch, AccountManager accMgr,
                                            int numWorkers, boolean useSync, boolean lockOrdering) {
            BatchSyncReport rep = new BatchSyncReport();
            rep.totalTransactions = batch.size();
            rep.successfulTransactions = 0;
            rep.failedTransactions = 0;
            rep.syncEnabled = useSync;
            rep.deadlockPrevention = lockOrdering;

            long startTime = System.nanoTime();

            Queue<Integer> jobQueue = new LinkedList<>();
            for (int i = 0; i < batch.size(); ++i) {
                jobQueue.add(i);
            }

            Object queueLock = new Object();
            List<Thread> workers = new ArrayList<>();

            for (int w = 0; w < numWorkers; ++w) {
                Thread t = new Thread(() -> {
                    while (true) {
                        int jobIdx = -1;
                        synchronized (queueLock) {
                            if (jobQueue.isEmpty()) break;
                            jobIdx = jobQueue.poll();
                        }

                        boolean ok;
                        if (useSync) {
                            ok = executeSafeTransaction(batch.get(jobIdx), accMgr, lockOrdering);
                        } else {
                            ok = executeUnsafeTransaction(batch.get(jobIdx), accMgr);
                        }

                        synchronized (rep) {
                            if (ok) rep.successfulTransactions++;
                            else rep.failedTransactions++;
                        }
                    }
                });
                workers.add(t);
                t.start();
            }

            for (Thread th : workers) {
                try {
                    th.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            long endTime = System.nanoTime();
            rep.executionTimeMs = (endTime - startTime) / 1_000_000.0;
            return rep;
        }

        public RaceReport runRaceConditionDemo(AccountManager accMgr, int testAccId, int operations, double amt) {
            RaceReport rep = new RaceReport();
            rep.totalOps = operations;
            rep.initialBalance = 10000.0;
            rep.expectedFinalBalance = rep.initialBalance - (operations * amt);

            accMgr.createAccount(testAccId, "Race Demo Account", rep.initialBalance);
            Account acc = accMgr.getAccount(testAccId);

            List<Thread> unsafeThreads = new ArrayList<>();
            for (int i = 0; i < operations; ++i) {
                Thread t = new Thread(() -> {
                    double current = acc.balance;
                    try {
                        Thread.sleep(0, 10000); // 10 us
                    } catch (InterruptedException ignored) {}
                    acc.balance = current - amt;
                });
                unsafeThreads.add(t);
                t.start();
            }
            for (Thread t : unsafeThreads) {
                try {
                    t.join();
                } catch (InterruptedException ignored) {}
            }
            rep.actualUnsafeBalance = acc.balance;

            acc.balance = rep.initialBalance;
            List<Thread> safeThreads = new ArrayList<>();
            for (int i = 0; i < operations; ++i) {
                Thread t = new Thread(() -> {
                    synchronized (acc) {
                        double current = acc.balance;
                        try {
                            Thread.sleep(0, 10000);
                        } catch (InterruptedException ignored) {}
                        acc.balance = current - amt;
                    }
                });
                safeThreads.add(t);
                t.start();
            }
            for (Thread t : safeThreads) {
                try {
                    t.join();
                } catch (InterruptedException ignored) {}
            }
            rep.actualSafeBalance = acc.balance;

            rep.raceDetected = (Math.abs(rep.actualUnsafeBalance - rep.expectedFinalBalance) > 1e-4);
            double diff = Math.abs(rep.actualUnsafeBalance - rep.expectedFinalBalance);
            rep.lostUpdates = (int) Math.round(diff / amt);
            return rep;
        }

        public DeadlockReport runDeadlockDemo(AccountManager accMgr, int idA, int idB, boolean enablePrevention) {
            DeadlockReport rep = new DeadlockReport();
            rep.preventionEnabled = enablePrevention;
            rep.circularWaitOccurred = false;
            rep.completedCount = 0;

            accMgr.createAccount(idA, "Deadlock Acc A", 1000.0);
            accMgr.createAccount(idB, "Deadlock Acc B", 1000.0);

            long startTime = System.nanoTime();

            if (enablePrevention) {
                Thread t1 = new Thread(() -> {
                    Transaction tx1 = new Transaction(91, TransactionType.TRANSFER, idA, idB, 100.0);
                    if (executeSafeTransaction(tx1, accMgr, true)) {
                        synchronized (rep) { rep.completedCount++; }
                    }
                });
                Thread t2 = new Thread(() -> {
                    Transaction tx2 = new Transaction(92, TransactionType.TRANSFER, idB, idA, 100.0);
                    if (executeSafeTransaction(tx2, accMgr, true)) {
                        synchronized (rep) { rep.completedCount++; }
                    }
                });
                t1.start();
                t2.start();
                try {
                    t1.join();
                    t2.join();
                } catch (InterruptedException ignored) {}
                rep.circularWaitOccurred = false;
                rep.details = "Consistent lock ordering (ascending account ID) eliminated circular wait.";
            } else {
                ReentrantLock mtx1 = new ReentrantLock();
                ReentrantLock mtx2 = new ReentrantLock();

                Thread t1 = new Thread(() -> {
                    try {
                        if (mtx1.tryLock(50, TimeUnit.MILLISECONDS)) {
                            try {
                                Thread.sleep(20);
                                if (mtx2.tryLock(40, TimeUnit.MILLISECONDS)) {
                                    try {
                                        synchronized (rep) { rep.completedCount++; }
                                    } finally {
                                        mtx2.unlock();
                                    }
                                } else {
                                    synchronized (rep) { rep.circularWaitOccurred = true; }
                                }
                            } finally {
                                mtx1.unlock();
                            }
                        }
                    } catch (InterruptedException ignored) {}
                });

                Thread t2 = new Thread(() -> {
                    try {
                        if (mtx2.tryLock(50, TimeUnit.MILLISECONDS)) {
                            try {
                                Thread.sleep(20);
                                if (mtx1.tryLock(40, TimeUnit.MILLISECONDS)) {
                                    try {
                                        synchronized (rep) { rep.completedCount++; }
                                    } finally {
                                        mtx1.unlock();
                                    }
                                } else {
                                    synchronized (rep) { rep.circularWaitOccurred = true; }
                                }
                            } finally {
                                mtx2.unlock();
                            }
                        }
                    } catch (InterruptedException ignored) {}
                });

                t1.start();
                t2.start();
                try {
                    t1.join();
                    t2.join();
                } catch (InterruptedException ignored) {}

                rep.details = rep.circularWaitOccurred
                        ? "Circular wait detected between reverse locks. Timed out to prevent infinite hang."
                        : "Operations completed without deadlock.";
            }

            long endTime = System.nanoTime();
            rep.executionTimeMs = (endTime - startTime) / 1_000_000.0;
            return rep;
        }

        public void displayRaceReport(RaceReport r) {
            System.out.println("\n====================== RACE CONDITION DEMONSTRATION ======================");
            System.out.println("Total Concurrent Operations   : " + r.totalOps);
            System.out.printf("Initial Account Balance       : $%.2f%n", r.initialBalance);
            System.out.printf("Expected Final Balance        : $%.2f%n", r.expectedFinalBalance);
            System.out.println("------------------------------------------------------------------------");
            System.out.printf("Actual Unsynchronized Balance : $%.2f%n", r.actualUnsafeBalance);
            System.out.printf("Actual Synchronized Balance   : $%.2f%n", r.actualSafeBalance);
            System.out.println("Lost Updates in Unsynchronized: " + r.lostUpdates);
            System.out.println("Data Race Detected            : " + (r.raceDetected ? "YES (Data Inconsistency Observed!)" : "NO"));
            System.out.println("Synchronization Status        : " + (Math.abs(r.actualSafeBalance - r.expectedFinalBalance) < 1e-4 ? "CONSISTENT & THREAD-SAFE" : "CORRUPTED"));
            System.out.println("========================================================================");
        }

        public void displayDeadlockReport(DeadlockReport d) {
            System.out.println("\n===================== DEADLOCK DEMONSTRATION & PREVENTION =====================");
            System.out.println("Deadlock Prevention Rule : " + (d.preventionEnabled ? "ENABLED (Lock Ordering by Account ID)" : "DISABLED (Arbitrary Lock Order)"));
            System.out.println("Deadlock / Circular Wait : " + (d.circularWaitOccurred ? "DETECTED (Circular Wait Triggered)" : "PREVENTED / NONE"));
            System.out.println("Transactions Completed   : " + d.completedCount + " / 2");
            System.out.printf("Elapsed Time             : %.2f ms%n", d.executionTimeMs);
            System.out.println("Resolution Details       : " + d.details);
            System.out.println("===============================================================================");
        }

        public void displayBatchReport(BatchSyncReport b) {
            System.out.println("\n---------------- Synchronized Workload Execution ----------------");
            System.out.println("Total Transactions Processed : " + b.totalTransactions);
            System.out.println("Successful Transactions     : " + b.successfulTransactions);
            System.out.println("Failed Transactions         : " + b.failedTransactions);
            System.out.println("Synchronization Active      : " + (b.syncEnabled ? "YES" : "NO"));
            System.out.println("Deadlock Prevention Active  : " + (b.deadlockPrevention ? "YES" : "NO"));
            System.out.printf("Execution Elapsed Time      : %.2f ms%n", b.executionTimeMs);
            System.out.println("----------------------------------------------------------------");
        }
    }
}
