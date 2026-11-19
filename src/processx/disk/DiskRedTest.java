package processx.disk;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import processx.DiskScheduling.DiskDirection;
import processx.DiskScheduling.DiskOutput;
import processx.DiskScheduling.DiskScheduler;
import processx.DiskScheduling.TransactionLogger;

/**
 * RED PHASE (Sprint 4: Disk Scheduling & Audit Logging)
 * JUnit 5 test suite verifying head seek optimization and audit logging.
 */
public class DiskRedTest {

    private List<Integer> requests;
    private int initialHead;

    @BeforeEach
    void setUp() {
        // Standard benchmark track sequence starting at cylinder 53
        requests = Arrays.asList(98, 183, 37, 122, 14, 124, 65, 67);
        initialHead = 53;
    }

    // Test 1: Verify baseline FCFS calculates correct total head movement
    @Test
    @DisplayName("Verify FCFS baseline head movements on standard benchmark sequence")
    void testScheduleFCFS() {
        DiskOutput out = DiskScheduler.scheduleFCFS(initialHead, requests);
        assertEquals(640, out.totalHeadMovements, "FCFS head movement on benchmark sequence must be 640 tracks");
        assertEquals(8, out.seekOrder.size(), "All 8 requests must be in seek sequence");
    }

    // Test 2: Verify SSTF seek-time minimization compared to FCFS
    @Test
    @DisplayName("Verify SSTF minimizes total head movements compared to FCFS")
    void testScheduleSSTFReducesHeadMovement() {
        DiskOutput fcfs = DiskScheduler.scheduleFCFS(initialHead, requests);
        DiskOutput sstf = DiskScheduler.scheduleSSTF(initialHead, requests);

        assertEquals(236, sstf.totalHeadMovements, "SSTF head movement must be 236 tracks");
        assertTrue(sstf.totalHeadMovements < fcfs.totalHeadMovements, "SSTF must reduce head travel vs FCFS");
    }

    // Test 3: Verify LOOK monotonic elevator sweep services requests efficiently
    @Test
    @DisplayName("Verify LOOK elevator sweep direction and movement")
    void testScheduleLOOK() {
        DiskOutput look = DiskScheduler.scheduleLOOK(initialHead, requests, DiskDirection.RIGHT_DIRECTION);
        assertEquals(299, look.totalHeadMovements, "LOOK head movement must be 299 tracks");
        assertEquals(8, look.seekOrder.size(), "LOOK must service all 8 tracks without starvation");
    }

    // Test 4: Verify Transaction Logger correctly writes audit log records to disk
    @Test
    @DisplayName("Verify persistent transaction audit logging to text file")
    void testTransactionLoggerFileWrite() {
        TransactionLogger logger = new TransactionLogger();
        logger.log("DEPOSIT", 1, "Deposited $500");
        logger.log("WITHDRAW", 2, "Withdrew $200");
        String filename = "junit_test_log.txt";
        logger.saveToFile(filename);

        File f = new File(filename);
        assertTrue(f.exists(), "Log file must exist on disk");
        assertTrue(f.length() > 0, "Log file must not be empty");
        assertEquals(2, logger.count(), "Logger must retain 2 audit entries");
        f.delete();
    }
}
