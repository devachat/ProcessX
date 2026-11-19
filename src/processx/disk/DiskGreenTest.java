package processx.disk;

import java.util.Arrays;
import java.util.List;
import processx.DiskScheduling.DiskOutput;
import processx.DiskScheduling.DiskScheduler;

public class DiskGreenTest {

    public static void run() {
        System.out.println("\n--------------------------------------------------------------");
        System.out.println(" >>> [DISK SCHEDULING: GREEN PHASE] <<<");
        System.out.println("--------------------------------------------------------------");

        List<Integer> reqs = Arrays.asList(98, 183, 37, 122, 14, 124, 65, 67);
        DiskOutput sstf = DiskScheduler.scheduleSSTF(53, reqs);

        boolean pass = (sstf.totalHeadMovements == 236);
        System.out.printf(" SSTF Total Head Movement : %d tracks (Reduced from 640 tracks)%n", sstf.totalHeadMovements);
        System.out.printf(" Average Seek Distance    : %.2f tracks/request%n", sstf.avgSeekDistance);
        System.out.println(" Status: " + (pass ? "GREEN (Head Movement Optimized)" : "FAIL"));
    }

    public static void main(String[] args) {
        run();
    }
}
