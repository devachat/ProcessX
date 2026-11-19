package processx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class CpuScheduler {

    public enum TransactionType {
        DEPOSIT,
        WITHDRAW,
        TRANSFER,
        BALANCE_INQUIRY
    }

    public enum TransactionStatus {
        SUBMITTED,
        READY,
        RUNNING,
        SUCCESS,
        FAILED_INSUFFICIENT_FUNDS,
        FAILED_INVALID_ACCOUNT
    }

    public static class Account {
        public final int accountId;
        public final String holderName;
        public double balance;
        public final List<String> history = new ArrayList<>();

        public Account(int id, String name, double initBal) {
            this.accountId = id;
            this.holderName = name;
            this.balance = initBal;
            this.history.add("Account opened with balance: " + String.format("%.2f", initBal));
        }

        public synchronized boolean deposit(double amount) {
            if (amount <= 0) return false;
            this.balance += amount;
            this.history.add("Deposited: " + String.format("%.2f", amount) + " | Balance: " + String.format("%.2f", this.balance));
            return true;
        }

        public synchronized boolean withdraw(double amount) {
            if (amount <= 0 || this.balance < amount) return false;
            this.balance -= amount;
            this.history.add("Withdrew: " + String.format("%.2f", amount) + " | Balance: " + String.format("%.2f", this.balance));
            return true;
        }
    }

    public static class Transaction {
        public int txId;
        public TransactionType type;
        public int sourceAccountId;
        public int targetAccountId;
        public double amount;
        public TransactionStatus status;

        public int arrivalTime;
        public int burstTime;
        public int remainingTime;
        public int priority;
        public int memoryNeeded;
        public int diskTrackNeeded;

        public int startTime;
        public int completionTime;
        public int waitingTime;
        public int turnaroundTime;
        public int responseTime;
        public boolean hasStarted;

        public Transaction(int id, TransactionType t, int src, int dest, double amt,
                           int arr, int burst, int prio, int mem, int track) {
            this.txId = id;
            this.type = t;
            this.sourceAccountId = src;
            this.targetAccountId = dest;
            this.amount = amt;
            this.status = TransactionStatus.SUBMITTED;
            this.arrivalTime = arr;
            this.burstTime = burst;
            this.remainingTime = burst;
            this.priority = prio;
            this.memoryNeeded = mem;
            this.diskTrackNeeded = track;
            this.startTime = -1;
            this.completionTime = 0;
            this.waitingTime = 0;
            this.turnaroundTime = 0;
            this.responseTime = -1;
            this.hasStarted = false;
        }

        public Transaction(int id, TransactionType t, int src, int dest, double amt) {
            this(id, t, src, dest, amt, 0, 1, 1, 64, 50);
        }

        public Transaction(Transaction other) {
            this.txId = other.txId;
            this.type = other.type;
            this.sourceAccountId = other.sourceAccountId;
            this.targetAccountId = other.targetAccountId;
            this.amount = other.amount;
            this.status = other.status;
            this.arrivalTime = other.arrivalTime;
            this.burstTime = other.burstTime;
            this.remainingTime = other.remainingTime;
            this.priority = other.priority;
            this.memoryNeeded = other.memoryNeeded;
            this.diskTrackNeeded = other.diskTrackNeeded;
            this.startTime = other.startTime;
            this.completionTime = other.completionTime;
            this.waitingTime = other.waitingTime;
            this.turnaroundTime = other.turnaroundTime;
            this.responseTime = other.responseTime;
            this.hasStarted = other.hasStarted;
        }

        public String getTypeString() {
            if (type == TransactionType.DEPOSIT) return "DEPOSIT";
            if (type == TransactionType.WITHDRAW) return "WITHDRAW";
            if (type == TransactionType.TRANSFER) return "TRANSFER";
            return "INQUIRY";
        }

        public String getStatusString() {
            if (status == TransactionStatus.SUBMITTED) return "SUBMITTED";
            if (status == TransactionStatus.READY) return "READY";
            if (status == TransactionStatus.RUNNING) return "RUNNING";
            if (status == TransactionStatus.SUCCESS) return "SUCCESS";
            if (status == TransactionStatus.FAILED_INSUFFICIENT_FUNDS) return "FAIL_NO_FUNDS";
            return "FAIL_INVALID_ACC";
        }
    }

    public static class AccountManager {
        private final java.util.Map<Integer, Account> accounts = new java.util.concurrent.ConcurrentHashMap<>();

        public synchronized boolean createAccount(int id, String name, double initialBalance) {
            if (accounts.containsKey(id)) return false;
            accounts.put(id, new Account(id, name, initialBalance));
            return true;
        }

        public Account getAccount(int id) {
            return accounts.get(id);
        }

        public boolean accountExists(int id) {
            return accounts.containsKey(id);
        }

        public void initializeDefaultAccounts() {
            createAccount(101, "Janya Jaiswal", 5000.0);
            createAccount(102, "Devansh Chaturvedi", 7500.0);
            createAccount(103, "Ananya Shandilya", 6200.0);
            createAccount(104, "Prakhar Awasthi", 8400.0);
        }

        public synchronized void displayAccounts() {
            System.out.println("\n======================== BANK ACCOUNTS ========================");
            System.out.printf("%-10s%-25s%-15s%n", "Acc ID", "Account Holder", "Balance ($)");
            System.out.println("--------------------------------------------------------------");
            for (Account acc : accounts.values()) {
                synchronized (acc) {
                    System.out.printf("%-10d%-25s%-15.2f%n", acc.accountId, acc.holderName, acc.balance);
                }
            }
            System.out.println("==============================================================");
        }

        public synchronized void clear() {
            accounts.clear();
        }
    }

    public static class TransactionManager {
        private final List<Transaction> transactions = new ArrayList<>();
        private int counter = 1;

        public synchronized int addTransaction(TransactionType type, int src, int dest, double amount,
                                               int arrival, int burst, int prio, int mem, int track) {
            int id = counter++;
            transactions.add(new Transaction(id, type, src, dest, amount, arrival, burst, prio, mem, track));
            return id;
        }

        public synchronized int addTransaction(TransactionType type, int src, int dest, double amount) {
            return addTransaction(type, src, dest, amount, 0, 2, 1, 64, 50);
        }

        public synchronized List<Transaction> getTransactions() {
            return transactions;
        }

        public synchronized void generateWorkload(int count) {
            transactions.clear();
            counter = 1;
            int[] accs = {101, 102, 103, 104};

            for (int i = 0; i < count; ++i) {
                TransactionType type = TransactionType.values()[i % 4];
                int src = accs[i % 4];
                int dest = accs[(i + 1) % 4];
                double amt = 100.0 * (1 + (i % 5));
                int arr = i * 2;
                int burst = 2 + (i % 4);
                int prio = 1 + (i % 3);
                int mem = 32 * (1 + (i % 4));
                int track = (i * 37) % 200;
                addTransaction(type, src, dest, amt, arr, burst, prio, mem, track);
            }
        }

        public synchronized void displayWorkload() {
            System.out.println("\n======================== TRANSACTION WORKLOAD ========================");
            System.out.printf("%-6s%-10s%-8s%-8s%-10s%-6s%-6s%-6s%-6s%-6s%n",
                    "TxID", "Type", "Source", "Target", "Amount", "Arr", "Burst", "Prio", "Mem", "Track");
            System.out.println("----------------------------------------------------------------------");
            for (Transaction tx : transactions) {
                System.out.printf("%-6d%-10s%-8d%-8s%-10.1f%-6d%-6d%-6d%-6d%-6d%n",
                        tx.txId,
                        tx.getTypeString(),
                        tx.sourceAccountId,
                        (tx.type == TransactionType.TRANSFER ? String.valueOf(tx.targetAccountId) : "-"),
                        tx.amount,
                        tx.arrivalTime,
                        tx.burstTime,
                        tx.priority,
                        tx.memoryNeeded,
                        tx.diskTrackNeeded);
            }
            System.out.println("======================================================================");
        }

        public synchronized void clear() {
            transactions.clear();
            counter = 1;
        }
    }

    public static class GanttItem {
        public int txId;
        public int startTime;
        public int endTime;

        public GanttItem(int txId, int start, int end) {
            this.txId = txId;
            this.startTime = start;
            this.endTime = end;
        }
    }

    public static class CPUMetrics {
        public String algorithmName;
        public List<GanttItem> ganttChart = new ArrayList<>();
        public List<Transaction> scheduledList = new ArrayList<>();
        public double avgWaiting;
        public double avgTurnaround;
        public double avgResponse;
        public double cpuUtil;
        public double throughput;

        public void printGantt() {
            System.out.println("\n--- Gantt Chart (" + algorithmName + ") ---\n|");
            for (GanttItem item : ganttChart) {
                if (item.txId == -1) System.out.print(" IDLE |");
                else System.out.print(" TX" + item.txId + " |");
            }
            System.out.println();
            if (!ganttChart.isEmpty()) {
                System.out.print(ganttChart.get(0).startTime);
                for (GanttItem item : ganttChart) {
                    System.out.printf("%6d", item.endTime);
                }
                System.out.println();
            }
        }

        public void printMetrics() {
            System.out.println("\n------------------ " + algorithmName + " Scheduling Metrics ------------------");
            System.out.printf("%-8s%-10s%-8s%-8s%-10s%-12s%-10s%n",
                    "TxID", "Arrival", "Burst", "Finish", "Waiting", "Turnaround", "Response");
            System.out.println("------------------------------------------------------------------");
            for (Transaction tx : scheduledList) {
                System.out.printf("%-8d%-10d%-8d%-8d%-10d%-12d%-10d%n",
                        tx.txId, tx.arrivalTime, tx.burstTime, tx.completionTime,
                        tx.waitingTime, tx.turnaroundTime, tx.responseTime);
            }
            System.out.println("------------------------------------------------------------------");
            System.out.printf("Average Waiting Time    : %.2f ms%n", avgWaiting);
            System.out.printf("Average Turnaround Time : %.2f ms%n", avgTurnaround);
            System.out.printf("Average Response Time   : %.2f ms%n", avgResponse);
            System.out.printf("CPU Utilization         : %.2f %%%n", cpuUtil);
            System.out.printf("Throughput              : %.2f transactions/unit time%n", throughput);
            System.out.println("------------------------------------------------------------------");
        }
    }

    private static List<Transaction> cloneList(List<Transaction> in) {
        List<Transaction> out = new ArrayList<>();
        for (Transaction t : in) {
            out.add(new Transaction(t));
        }
        return out;
    }

    public static CPUMetrics scheduleFCFS(List<Transaction> rawWorkload) {
        CPUMetrics res = new CPUMetrics();
        res.algorithmName = "FCFS (First-Come, First-Served)";
        if (rawWorkload == null || rawWorkload.isEmpty()) return res;

        List<Transaction> workload = cloneList(rawWorkload);
        workload.sort((a, b) -> Integer.compare(a.arrivalTime, b.arrivalTime));

        int time = 0;
        int busy = 0;
        double sumWait = 0, sumTAT = 0, sumResp = 0;

        for (Transaction tx : workload) {
            if (time < tx.arrivalTime) {
                res.ganttChart.add(new GanttItem(-1, time, tx.arrivalTime));
                time = tx.arrivalTime;
            }
            tx.startTime = time;
            tx.responseTime = tx.startTime - tx.arrivalTime;
            tx.completionTime = time + tx.burstTime;
            tx.turnaroundTime = tx.completionTime - tx.arrivalTime;
            tx.waitingTime = tx.turnaroundTime - tx.burstTime;

            res.ganttChart.add(new GanttItem(tx.txId, time, tx.completionTime));
            time = tx.completionTime;
            busy += tx.burstTime;

            sumWait += tx.waitingTime;
            sumTAT += tx.turnaroundTime;
            sumResp += tx.responseTime;
        }

        int n = workload.size();
        res.scheduledList = workload;
        res.avgWaiting = sumWait / n;
        res.avgTurnaround = sumTAT / n;
        res.avgResponse = sumResp / n;
        res.cpuUtil = (time > 0) ? (((double) busy / time) * 100.0) : 0.0;
        res.throughput = (time > 0) ? ((double) n / time) : 0.0;
        return res;
    }

    public static CPUMetrics scheduleSJF(List<Transaction> rawWorkload, boolean preemptive) {
        CPUMetrics res = new CPUMetrics();
        res.algorithmName = preemptive ? "Shortest Remaining Time First (SRTF)" : "Shortest Job First (SJF)";
        if (rawWorkload == null || rawWorkload.isEmpty()) return res;

        List<Transaction> workload = cloneList(rawWorkload);
        int n = workload.size();
        for (Transaction tx : workload) {
            tx.remainingTime = tx.burstTime;
            tx.startTime = -1;
            tx.hasStarted = false;
        }

        int time = 0, completed = 0, busy = 0;
        double sumWait = 0, sumTAT = 0, sumResp = 0;

        if (!preemptive) {
            boolean[] done = new boolean[n];
            while (completed < n) {
                int best = -1;
                int minBurst = Integer.MAX_VALUE;
                for (int i = 0; i < n; ++i) {
                    if (!done[i] && workload.get(i).arrivalTime <= time) {
                        if (workload.get(i).burstTime < minBurst) {
                            minBurst = workload.get(i).burstTime;
                            best = i;
                        }
                    }
                }

                if (best == -1) {
                    int nextArr = Integer.MAX_VALUE;
                    for (int i = 0; i < n; ++i) {
                        if (!done[i] && workload.get(i).arrivalTime > time) {
                            nextArr = Math.min(nextArr, workload.get(i).arrivalTime);
                        }
                    }
                    res.ganttChart.add(new GanttItem(-1, time, nextArr));
                    time = nextArr;
                    continue;
                }

                Transaction tx = workload.get(best);
                tx.startTime = time;
                tx.responseTime = tx.startTime - tx.arrivalTime;
                tx.completionTime = time + tx.burstTime;
                tx.turnaroundTime = tx.completionTime - tx.arrivalTime;
                tx.waitingTime = tx.turnaroundTime - tx.burstTime;

                res.ganttChart.add(new GanttItem(tx.txId, time, tx.completionTime));
                time = tx.completionTime;
                busy += tx.burstTime;
                done[best] = true;
                completed++;

                sumWait += tx.waitingTime;
                sumTAT += tx.turnaroundTime;
                sumResp += tx.responseTime;
            }
        } else {
            int lastId = -2;
            int segStart = 0;

            while (completed < n) {
                int best = -1;
                int minRem = Integer.MAX_VALUE;
                for (int i = 0; i < n; ++i) {
                    Transaction t = workload.get(i);
                    if (t.remainingTime > 0 && t.arrivalTime <= time) {
                        if (t.remainingTime < minRem) {
                            minRem = t.remainingTime;
                            best = i;
                        }
                    }
                }

                if (best == -1) {
                    if (lastId != -1) {
                        if (lastId != -2) res.ganttChart.add(new GanttItem(lastId, segStart, time));
                        lastId = -1;
                        segStart = time;
                    }
                    time++;
                    continue;
                }

                Transaction tx = workload.get(best);
                if (!tx.hasStarted) {
                    tx.startTime = time;
                    tx.responseTime = tx.startTime - tx.arrivalTime;
                    tx.hasStarted = true;
                }

                if (tx.txId != lastId) {
                    if (lastId != -2) res.ganttChart.add(new GanttItem(lastId, segStart, time));
                    lastId = tx.txId;
                    segStart = time;
                }

                tx.remainingTime--;
                busy++;
                time++;

                if (tx.remainingTime == 0) {
                    tx.completionTime = time;
                    tx.turnaroundTime = tx.completionTime - tx.arrivalTime;
                    tx.waitingTime = tx.turnaroundTime - tx.burstTime;
                    sumWait += tx.waitingTime;
                    sumTAT += tx.turnaroundTime;
                    sumResp += tx.responseTime;
                    completed++;
                }
            }
            if (lastId != -2) res.ganttChart.add(new GanttItem(lastId, segStart, time));
        }

        res.scheduledList = workload;
        res.avgWaiting = sumWait / n;
        res.avgTurnaround = sumTAT / n;
        res.avgResponse = sumResp / n;
        res.cpuUtil = (time > 0) ? (((double) busy / time) * 100.0) : 0.0;
        res.throughput = (time > 0) ? ((double) n / time) : 0.0;
        return res;
    }

    public static CPUMetrics scheduleRoundRobin(List<Transaction> rawWorkload, int quantum) {
        CPUMetrics res = new CPUMetrics();
        res.algorithmName = "Round Robin (RR, Quantum=" + quantum + ")";
        if (rawWorkload == null || rawWorkload.isEmpty()) return res;

        List<Transaction> workload = cloneList(rawWorkload);
        int n = workload.size();
        for (Transaction tx : workload) {
            tx.remainingTime = tx.burstTime;
            tx.startTime = -1;
            tx.hasStarted = false;
        }

        workload.sort((a, b) -> Integer.compare(a.arrivalTime, b.arrivalTime));

        Queue<Integer> readyQ = new LinkedList<>();
        int time = 0, completed = 0, busy = 0, arrIdx = 0;

        while (arrIdx < n && workload.get(arrIdx).arrivalTime <= time) {
            readyQ.add(arrIdx++);
        }

        while (completed < n) {
            if (readyQ.isEmpty()) {
                if (arrIdx < n) {
                    int nextArr = workload.get(arrIdx).arrivalTime;
                    res.ganttChart.add(new GanttItem(-1, time, nextArr));
                    time = nextArr;
                    while (arrIdx < n && workload.get(arrIdx).arrivalTime <= time) {
                        readyQ.add(arrIdx++);
                    }
                }
                continue;
            }

            int idx = readyQ.poll();
            Transaction tx = workload.get(idx);

            if (!tx.hasStarted) {
                tx.startTime = time;
                tx.responseTime = tx.startTime - tx.arrivalTime;
                tx.hasStarted = true;
            }

            int exec = Math.min(quantum, tx.remainingTime);
            res.ganttChart.add(new GanttItem(tx.txId, time, time + exec));
            time += exec;
            tx.remainingTime -= exec;
            busy += exec;

            while (arrIdx < n && workload.get(arrIdx).arrivalTime <= time) {
                readyQ.add(arrIdx++);
            }

            if (tx.remainingTime > 0) {
                readyQ.add(idx);
            } else {
                tx.completionTime = time;
                tx.turnaroundTime = tx.completionTime - tx.arrivalTime;
                tx.waitingTime = tx.turnaroundTime - tx.burstTime;
                completed++;
            }
        }

        double sumWait = 0, sumTAT = 0, sumResp = 0;
        for (Transaction tx : workload) {
            sumWait += tx.waitingTime;
            sumTAT += tx.turnaroundTime;
            sumResp += tx.responseTime;
        }

        res.scheduledList = workload;
        res.avgWaiting = sumWait / n;
        res.avgTurnaround = sumTAT / n;
        res.avgResponse = sumResp / n;
        res.cpuUtil = (time > 0) ? (((double) busy / time) * 100.0) : 0.0;
        res.throughput = (time > 0) ? ((double) n / time) : 0.0;
        return res;
    }

    public static CPUMetrics schedulePriority(List<Transaction> rawWorkload, boolean preemptive) {
        CPUMetrics res = new CPUMetrics();
        res.algorithmName = preemptive ? "Preemptive Priority Scheduling" : "Priority Scheduling";
        if (rawWorkload == null || rawWorkload.isEmpty()) return res;

        List<Transaction> workload = cloneList(rawWorkload);
        int n = workload.size();
        for (Transaction tx : workload) {
            tx.remainingTime = tx.burstTime;
            tx.startTime = -1;
            tx.hasStarted = false;
        }

        int time = 0, completed = 0, busy = 0;
        double sumWait = 0, sumTAT = 0, sumResp = 0;

        if (!preemptive) {
            boolean[] done = new boolean[n];
            while (completed < n) {
                int best = -1;
                int highPrio = Integer.MAX_VALUE;
                for (int i = 0; i < n; ++i) {
                    if (!done[i] && workload.get(i).arrivalTime <= time) {
                        if (workload.get(i).priority < highPrio) {
                            highPrio = workload.get(i).priority;
                            best = i;
                        }
                    }
                }

                if (best == -1) {
                    int nextArr = Integer.MAX_VALUE;
                    for (int i = 0; i < n; ++i) {
                        if (!done[i] && workload.get(i).arrivalTime > time) {
                            nextArr = Math.min(nextArr, workload.get(i).arrivalTime);
                        }
                    }
                    res.ganttChart.add(new GanttItem(-1, time, nextArr));
                    time = nextArr;
                    continue;
                }

                Transaction tx = workload.get(best);
                tx.startTime = time;
                tx.responseTime = tx.startTime - tx.arrivalTime;
                tx.completionTime = time + tx.burstTime;
                tx.turnaroundTime = tx.completionTime - tx.arrivalTime;
                tx.waitingTime = tx.turnaroundTime - tx.burstTime;

                res.ganttChart.add(new GanttItem(tx.txId, time, tx.completionTime));
                time = tx.completionTime;
                busy += tx.burstTime;
                done[best] = true;
                completed++;

                sumWait += tx.waitingTime;
                sumTAT += tx.turnaroundTime;
                sumResp += tx.responseTime;
            }
        } else {
            int lastId = -2;
            int segStart = 0;

            while (completed < n) {
                int best = -1;
                int highPrio = Integer.MAX_VALUE;
                for (int i = 0; i < n; ++i) {
                    Transaction t = workload.get(i);
                    if (t.remainingTime > 0 && t.arrivalTime <= time) {
                        if (t.priority < highPrio) {
                            highPrio = t.priority;
                            best = i;
                        }
                    }
                }

                if (best == -1) {
                    if (lastId != -1) {
                        if (lastId != -2) res.ganttChart.add(new GanttItem(lastId, segStart, time));
                        lastId = -1;
                        segStart = time;
                    }
                    time++;
                    continue;
                }

                Transaction tx = workload.get(best);
                if (!tx.hasStarted) {
                    tx.startTime = time;
                    tx.responseTime = tx.startTime - tx.arrivalTime;
                    tx.hasStarted = true;
                }

                if (tx.txId != lastId) {
                    if (lastId != -2) res.ganttChart.add(new GanttItem(lastId, segStart, time));
                    lastId = tx.txId;
                    segStart = time;
                }

                tx.remainingTime--;
                busy++;
                time++;

                if (tx.remainingTime == 0) {
                    tx.completionTime = time;
                    tx.turnaroundTime = tx.completionTime - tx.arrivalTime;
                    tx.waitingTime = tx.turnaroundTime - tx.burstTime;
                    sumWait += tx.waitingTime;
                    sumTAT += tx.turnaroundTime;
                    sumResp += tx.responseTime;
                    completed++;
                }
            }
            if (lastId != -2) res.ganttChart.add(new GanttItem(lastId, segStart, time));
        }

        res.scheduledList = workload;
        res.avgWaiting = sumWait / n;
        res.avgTurnaround = sumTAT / n;
        res.avgResponse = sumResp / n;
        res.cpuUtil = (time > 0) ? (((double) busy / time) * 100.0) : 0.0;
        res.throughput = (time > 0) ? ((double) n / time) : 0.0;
        return res;
    }

    public static void compareAll(List<Transaction> workload, int quantum) {
        CPUMetrics rFCFS = scheduleFCFS(workload);
        CPUMetrics rSJF = scheduleSJF(workload, false);
        CPUMetrics rRR = scheduleRoundRobin(workload, quantum);
        CPUMetrics rPrio = schedulePriority(workload, false);

        System.out.println("\n================================ CPU SCHEDULING ALGORITHM COMPARISON ================================");
        System.out.printf("%-28s%-16s%-18s%-16s%-16s%-16s%n",
                "Algorithm", "Avg Waiting", "Avg Turnaround", "Avg Response", "CPU Util (%)", "Throughput");
        System.out.println("----------------------------------------------------------------------------------------------------");

        printMetricsRow(rFCFS);
        printMetricsRow(rSJF);
        printMetricsRow(rRR);
        printMetricsRow(rPrio);
        System.out.println("====================================================================================================");
    }

    private static void printMetricsRow(CPUMetrics r) {
        System.out.printf("%-28s%-16.2f%-18.2f%-16.2f%-16.2f%-16.2f%n",
                r.algorithmName, r.avgWaiting, r.avgTurnaround, r.avgResponse, r.cpuUtil, r.throughput);
    }
}
