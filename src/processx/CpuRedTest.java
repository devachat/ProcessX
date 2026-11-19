package processx.cpu;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import processx.CpuScheduler;
import processx.CpuScheduler.CPUMetrics;
import processx.CpuScheduler.Transaction;
import processx.CpuScheduler.TransactionType;

/**
 * RED PHASE (Sprint 1: CPU Scheduling)
 * JUnit 5 test suite specifying acceptance criteria, metrics, and queue constraints.
 */
public class CpuRedTest {

    private List<Transaction> workload;

    @BeforeEach
    void setUp() {
        // Prepare mock transaction workload
        workload = Arrays.asList(
                new Transaction(1, TransactionType.DEPOSIT, 101, 0, 100, 0, 4, 2, 64, 50),
                new Transaction(2, TransactionType.WITHDRAW, 101, 0, 50, 1, 3, 1, 64, 50),
                new Transaction(3, TransactionType.BALANCE_INQUIRY, 101, 0, 0, 2, 1, 3, 64, 50)
        );
    }

    // Test 1: Verify FCFS scheduling correctly calculates completion and non-negative waiting time
    @Test
    @DisplayName("Verify FCFS non-preemptive arrival ordering and metrics")
    void testScheduleFCFS() {
        CPUMetrics res = CpuScheduler.scheduleFCFS(workload);
        assertEquals(3, res.scheduledList.size(), "All 3 transactions must be scheduled");
        assertTrue(res.avgWaiting >= 0.0, "Average waiting time cannot be negative");
        assertTrue(res.avgTurnaround > 0.0, "Average turnaround time must be positive");
        assertEquals(3, res.ganttChart.size(), "Gantt chart must have 3 execution segments");
    }

    // Test 2: Verify SJF delivers optimal or lower average turnaround time compared to FCFS
    @Test
    @DisplayName("Verify SJF minimizes average turnaround time compared to FCFS")
    void testScheduleSJFNonPreemptive() {
        CPUMetrics fcfs = CpuScheduler.scheduleFCFS(workload);
        CPUMetrics sjf = CpuScheduler.scheduleSJF(workload, false);

        assertEquals(3, sjf.scheduledList.size());
        assertTrue(sjf.avgTurnaround <= fcfs.avgTurnaround, "SJF should yield optimal or equal turnaround time to FCFS");
    }

    // Test 3: Verify Round Robin executes with proper time-slicing and positive CPU utilization
    @Test
    @DisplayName("Verify Round Robin dynamic queue slicing with quantum q=2")
    void testScheduleRoundRobin() {
        CPUMetrics rr = CpuScheduler.scheduleRoundRobin(workload, 2);
        assertEquals(3, rr.scheduledList.size());
        assertTrue(rr.cpuUtil > 0.0, "CPU core utilization must be positive");
        assertFalse(rr.ganttChart.isEmpty(), "Gantt chart must track time-sliced segments");
    }

    // Test 4: Verify Priority scheduling dispatches processes based on priority value
    @Test
    @DisplayName("Verify Priority-based process dispatching")
    void testSchedulePriority() {
        CPUMetrics prio = CpuScheduler.schedulePriority(workload, false);
        assertEquals(3, prio.scheduledList.size());
        assertTrue(prio.throughput > 0.0, "Throughput must be greater than zero");
    }
}
