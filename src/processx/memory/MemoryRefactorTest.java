package processx.memory;

import processx.MemoryManagement.MemoryManager;
import processx.MemoryManagement.MemoryStats;
import processx.MemoryManagement.MemoryStrategy;

public class MemoryRefactorTest {

    public static void run() {
        System.out.println("\n--------------------------------------------------------------");
        System.out.println(" >>> [MEMORY MANAGEMENT: REFACTOR PHASE] <<<");
        System.out.println("--------------------------------------------------------------");

        MemoryManager mm = new MemoryManager(500);
        mm.allocate(1, 100, MemoryStrategy.FIRST_FIT, "TX1");
        mm.allocate(2, 200, MemoryStrategy.BEST_FIT, "TX2");
        mm.allocate(3, 150, MemoryStrategy.WORST_FIT, "TX3");

        mm.deallocate(2);
        MemoryStats sPre = mm.getStats();
        System.out.printf(" Before Compaction : Holes = %d | External Fragmentation = %d KB%n",
                sPre.holeCount, sPre.externalFragmentationBytes);

        mm.compact();
        MemoryStats sPost = mm.getStats();
        boolean pass = (sPost.holeCount == 1) && (sPost.externalFragmentationBytes == 0);

        System.out.printf(" After Compaction  : Holes = %d | External Fragmentation = %d KB | Free Contiguous = %d KB%n",
                sPost.holeCount, sPost.externalFragmentationBytes, sPost.largestHole);
        System.out.println(" Status: " + (pass ? "REFACTORED (Zero Fragmentation Achieved)" : "FAIL"));
    }

    public static void main(String[] args) {
        run();
    }
}
