package processx.memory;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import processx.MemoryManagement.MemoryManager;
import processx.MemoryManagement.MemoryStats;
import processx.MemoryManagement.MemoryStrategy;

/**
 * RED PHASE (Sprint 2: Memory Management)
 * JUnit 5 test suite verifying allocation boundaries, partition mapping, and compaction.
 */
public class MemoryRedTest {

    private MemoryManager memMgr;

    @BeforeEach
    void setUp() {
        // Initialize 500 KB contiguous physical memory pool
        memMgr = new MemoryManager(500);
    }

    // Test 1: Verify allocation requests exceeding pool capacity are safely rejected
    @Test
    @DisplayName("Verify rejection of memory allocation exceeding pool capacity")
    void testAllocationExceedingCapacityFails() {
        boolean allocated = memMgr.allocate(99, 600, MemoryStrategy.FIRST_FIT, "OVERFLOW");
        assertFalse(allocated, "Allocation exceeding pool capacity must be rejected");
    }

    // Test 2: Verify dynamic contiguous partition allocation across multiple strategies
    @Test
    @DisplayName("Verify contiguous dynamic memory partition allocation")
    void testDynamicPartitionAllocation() {
        boolean a1 = memMgr.allocate(1, 100, MemoryStrategy.FIRST_FIT, "TX1");
        boolean a2 = memMgr.allocate(2, 200, MemoryStrategy.BEST_FIT, "TX2");
        boolean a3 = memMgr.allocate(3, 150, MemoryStrategy.WORST_FIT, "TX3");

        assertTrue(a1 && a2 && a3, "All three partition allocations must succeed");
        MemoryStats stats = memMgr.getStats();
        assertEquals(450, stats.allocatedMemory, "Allocated memory should equal sum of blocks (450 KB)");
        assertEquals(50, stats.freeMemory, "Remaining free memory should equal 50 KB");
    }

    // Test 3: Verify deallocation creates holes and compaction eliminates external fragmentation
    @Test
    @DisplayName("Verify deallocation, hole formation, and dynamic compaction")
    void testDeallocationAndCompaction() {
        memMgr.allocate(1, 100, MemoryStrategy.FIRST_FIT, "TX1");
        memMgr.allocate(2, 200, MemoryStrategy.BEST_FIT, "TX2");
        memMgr.allocate(3, 150, MemoryStrategy.WORST_FIT, "TX3");

        // Deallocate middle block (TX2: 200 KB) -> produces fragmented hole
        boolean dealloc = memMgr.deallocate(2);
        assertTrue(dealloc, "Deallocation of TX2 must succeed");

        MemoryStats preCompact = memMgr.getStats();
        assertTrue(preCompact.holeCount > 1, "Should have multiple disjoint free holes before compaction");

        // Perform memory compaction to consolidate fragmented free holes
        memMgr.compact();
        MemoryStats postCompact = memMgr.getStats();
        assertEquals(1, postCompact.holeCount, "Compaction must consolidate all holes into 1 contiguous hole");
        assertEquals(0, postCompact.externalFragmentationBytes, "External fragmentation must be reduced to 0 KB");
        assertEquals(250, postCompact.freeMemory, "Total free space must remain 250 KB");
    }
}
