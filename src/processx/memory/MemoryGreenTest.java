package processx.memory;

import processx.MemoryManagement.MemoryManager;
import processx.MemoryManagement.MemoryStats;
import processx.MemoryManagement.MemoryStrategy;

public class MemoryGreenTest {

    public static void run() {
        System.out.println("\n--------------------------------------------------------------");
        System.out.println(" >>> [MEMORY MANAGEMENT: GREEN PHASE] <<<");
        System.out.println("--------------------------------------------------------------");

        MemoryManager mm = new MemoryManager(500);
        boolean a1 = mm.allocate(1, 100, MemoryStrategy.FIRST_FIT, "TX1");
        boolean a2 = mm.allocate(2, 200, MemoryStrategy.BEST_FIT, "TX2");
        boolean a3 = mm.allocate(3, 150, MemoryStrategy.WORST_FIT, "TX3");

        MemoryStats s = mm.getStats();
        boolean pass = a1 && a2 && a3 && (s.allocatedMemory == 450);

        System.out.printf(" Allocated: %d KB / %d KB | Free: %d KB | Holes: %d%n",
                s.allocatedMemory, s.totalMemory, s.freeMemory, s.holeCount);
        System.out.println(" Status: " + (pass ? "GREEN (Passing partition allocation)" : "FAIL"));
    }

    public static void main(String[] args) {
        run();
    }
}
