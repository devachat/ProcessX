package processx.cpu;

import java.util.Arrays;
import java.util.List;
import processx.CpuScheduler;
import processx.CpuScheduler.CPUMetrics;
import processx.CpuScheduler.Transaction;
import processx.CpuScheduler.TransactionType;

public class CpuRefactorTest {

    public static void run() {
        System.out.println("\n--------------------------------------------------------------");
        System.out.println(" >>> [CPU SCHEDULING: REFACTOR PHASE] <<<");
        System.out.println("--------------------------------------------------------------");

        List<Transaction> workload = Arrays.asList(
                new Transaction(1, TransactionType.DEPOSIT, 101, 0, 100, 0, 4, 2, 64, 50),
                new Transaction(2, TransactionType.WITHDRAW, 101, 0, 50, 1, 3, 1, 64, 50),
                new Transaction(3, TransactionType.BALANCE_INQUIRY, 101, 0, 0, 2, 1, 3, 64, 50)
        );

        CPUMetrics rr = CpuScheduler.scheduleRoundRobin(workload, 2);
        CPUMetrics prio = CpuScheduler.schedulePriority(workload, false);

        System.out.printf(" Round Robin (q=2) : CPU Util = %.2f%% | Throughput = %.2f tx/unit%n",
                rr.cpuUtil, rr.throughput);
        System.out.printf(" Priority Scheduling: Scheduled %d transactions%n", prio.scheduledList.size());
        rr.printGantt();
        System.out.println(" Status: REFACTORED (Clean execution)");
    }

    public static void main(String[] args) {
        run();
    }
}
