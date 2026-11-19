package processx.cpu;

import java.util.Arrays;
import java.util.List;
import processx.CpuScheduler;
import processx.CpuScheduler.CPUMetrics;
import processx.CpuScheduler.Transaction;
import processx.CpuScheduler.TransactionType;

public class CpuGreenTest {

    public static void run() {
        System.out.println("\n--------------------------------------------------------------");
        System.out.println(" >>> [CPU SCHEDULING: GREEN PHASE] <<<");
        System.out.println("--------------------------------------------------------------");

        List<Transaction> workload = Arrays.asList(
                new Transaction(1, TransactionType.DEPOSIT, 101, 0, 100, 0, 4, 2, 64, 50),
                new Transaction(2, TransactionType.WITHDRAW, 101, 0, 50, 1, 3, 1, 64, 50),
                new Transaction(3, TransactionType.BALANCE_INQUIRY, 101, 0, 0, 2, 1, 3, 64, 50)
        );

        CPUMetrics fcfs = CpuScheduler.scheduleFCFS(workload);
        CPUMetrics sjf = CpuScheduler.scheduleSJF(workload, false);

        boolean passFCFS = (fcfs.scheduledList.size() == 3) && (fcfs.avgTurnaround > 0);
        boolean passSJF = (sjf.scheduledList.size() == 3) && (sjf.avgTurnaround <= fcfs.avgTurnaround);

        System.out.printf(" FCFS Execution : %d tasks | Avg TAT: %.2f ms | Avg Wait: %.2f ms -> %s%n",
                fcfs.scheduledList.size(), fcfs.avgTurnaround, fcfs.avgWaiting, (passFCFS ? "PASS" : "FAIL"));
        System.out.printf(" SJF Execution  : %d tasks | Avg TAT: %.2f ms (Optimal <= FCFS) -> %s%n",
                sjf.scheduledList.size(), sjf.avgTurnaround, (passSJF ? "PASS" : "FAIL"));
    }

    public static void main(String[] args) {
        run();
    }
}
