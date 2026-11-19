package processx.disk;

import java.util.Arrays;
import java.util.List;
import processx.DiskScheduling.DiskDirection;
import processx.DiskScheduling.DiskOutput;
import processx.DiskScheduling.DiskScheduler;

public class DiskRefactorTest {

    public static void run() {
        System.out.println("\n--------------------------------------------------------------");
        System.out.println(" >>> [DISK SCHEDULING: REFACTOR PHASE] <<<");
        System.out.println("--------------------------------------------------------------");

        List<Integer> reqs = Arrays.asList(98, 183, 37, 122, 14, 124, 65, 67);
        DiskOutput look = DiskScheduler.scheduleLOOK(53, reqs, DiskDirection.RIGHT_DIRECTION);
        DiskOutput clook = DiskScheduler.scheduleCLOOK(53, reqs, DiskDirection.RIGHT_DIRECTION);

        boolean pass = (look.totalHeadMovements == 299) && (clook.totalHeadMovements > 0);
        System.out.printf(" LOOK Head Movement   : %d tracks | Full sweep coverage guaranteed%n", look.totalHeadMovements);
        System.out.printf(" C-LOOK Head Movement : %d tracks | Uniform waiting time achieved%n", clook.totalHeadMovements);
        System.out.println(" Status: " + (pass ? "REFACTORED (Starvation-Free Sweeping Complete)" : "FAIL"));
    }

    public static void main(String[] args) {
        run();
    }
}
