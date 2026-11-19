package processx;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DiskScheduling {

    public enum DiskDirection {
        LEFT_DIRECTION,
        RIGHT_DIRECTION
    }

    public static class DiskOutput {
        public String algorithmName;
        public int initialHead;
        public int trackLimit;
        public List<Integer> seekOrder = new ArrayList<>();
        public int totalHeadMovements;
        public double avgSeekDistance;

        public void print() {
            System.out.println("\n------------------ " + algorithmName + " Disk Scheduling ------------------");
            System.out.println("Initial Head Position : Track " + initialHead);
            System.out.print("Seek Sequence         : " + initialHead);
            for (int track : seekOrder) {
                System.out.print(" -> " + track);
            }
            System.out.println("\nTotal Head Movement   : " + totalHeadMovements + " tracks");
            System.out.printf("Average Seek Distance : %.2f tracks/request%n", avgSeekDistance);
            System.out.println("------------------------------------------------------------------------");
        }
    }

    public static class DiskScheduler {
        public static DiskOutput scheduleFCFS(int initialHead, List<Integer> requests) {
            DiskOutput out = new DiskOutput();
            out.algorithmName = "FCFS (First-Come, First-Served)";
            out.initialHead = initialHead;
            out.trackLimit = 200;
            out.totalHeadMovements = 0;

            int head = initialHead;
            for (int t : requests) {
                out.totalHeadMovements += Math.abs(t - head);
                head = t;
                out.seekOrder.add(t);
            }
            out.avgSeekDistance = requests.isEmpty() ? 0.0 : ((double) out.totalHeadMovements / requests.size());
            return out;
        }

        public static DiskOutput scheduleSSTF(int initialHead, List<Integer> requests) {
            DiskOutput out = new DiskOutput();
            out.algorithmName = "SSTF (Shortest Seek Time First)";
            out.initialHead = initialHead;
            out.trackLimit = 200;
            out.totalHeadMovements = 0;

            List<Integer> pending = new ArrayList<>(requests);
            int head = initialHead;

            while (!pending.isEmpty()) {
                int bestIdx = 0;
                int minDist = Math.abs(pending.get(0) - head);

                for (int i = 1; i < pending.size(); ++i) {
                    int dist = Math.abs(pending.get(i) - head);
                    if (dist < minDist) {
                        minDist = dist;
                        bestIdx = i;
                    }
                }

                int nextTrack = pending.get(bestIdx);
                out.totalHeadMovements += minDist;
                out.seekOrder.add(nextTrack);
                head = nextTrack;
                pending.remove(bestIdx);
            }
            out.avgSeekDistance = requests.isEmpty() ? 0.0 : ((double) out.totalHeadMovements / requests.size());
            return out;
        }

        public static DiskOutput scheduleSCAN(int initialHead, List<Integer> requests, int totalTracks, DiskDirection dir) {
            DiskOutput out = new DiskOutput();
            out.algorithmName = "SCAN (Elevator Algorithm)";
            out.initialHead = initialHead;
            out.trackLimit = totalTracks;
            out.totalHeadMovements = 0;

            if (requests.isEmpty()) {
                out.avgSeekDistance = 0.0;
                return out;
            }

            List<Integer> leftTracks = new ArrayList<>();
            List<Integer> rightTracks = new ArrayList<>();
            for (int t : requests) {
                if (t < initialHead) leftTracks.add(t);
                if (t > initialHead) rightTracks.add(t);
            }
            Collections.sort(leftTracks);
            Collections.sort(rightTracks);

            int head = initialHead;
            if (dir == DiskDirection.RIGHT_DIRECTION) {
                for (int t : rightTracks) {
                    out.totalHeadMovements += Math.abs(t - head);
                    head = t;
                    out.seekOrder.add(t);
                }
                if (!leftTracks.isEmpty()) {
                    int bound = totalTracks - 1;
                    out.totalHeadMovements += Math.abs(bound - head);
                    head = bound;
                    out.seekOrder.add(bound);

                    for (int i = leftTracks.size() - 1; i >= 0; --i) {
                        out.totalHeadMovements += Math.abs(leftTracks.get(i) - head);
                        head = leftTracks.get(i);
                        out.seekOrder.add(leftTracks.get(i));
                    }
                }
            } else {
                for (int i = leftTracks.size() - 1; i >= 0; --i) {
                    out.totalHeadMovements += Math.abs(leftTracks.get(i) - head);
                    head = leftTracks.get(i);
                    out.seekOrder.add(leftTracks.get(i));
                }
                if (!rightTracks.isEmpty()) {
                    int bound = 0;
                    out.totalHeadMovements += Math.abs(bound - head);
                    head = bound;
                    out.seekOrder.add(bound);

                    for (int t : rightTracks) {
                        out.totalHeadMovements += Math.abs(t - head);
                        head = t;
                        out.seekOrder.add(t);
                    }
                }
            }

            out.avgSeekDistance = requests.isEmpty() ? 0.0 : ((double) out.totalHeadMovements / requests.size());
            return out;
        }

        public static DiskOutput scheduleCSCAN(int initialHead, List<Integer> requests, int totalTracks, DiskDirection dir) {
            DiskOutput out = new DiskOutput();
            out.algorithmName = "C-SCAN (Circular SCAN)";
            out.initialHead = initialHead;
            out.trackLimit = totalTracks;
            out.totalHeadMovements = 0;

            if (requests.isEmpty()) {
                out.avgSeekDistance = 0.0;
                return out;
            }

            List<Integer> leftTracks = new ArrayList<>();
            List<Integer> rightTracks = new ArrayList<>();
            for (int t : requests) {
                if (t < initialHead) leftTracks.add(t);
                if (t > initialHead) rightTracks.add(t);
            }
            Collections.sort(leftTracks);
            Collections.sort(rightTracks);

            int head = initialHead;
            if (dir == DiskDirection.RIGHT_DIRECTION) {
                for (int t : rightTracks) {
                    out.totalHeadMovements += Math.abs(t - head);
                    head = t;
                    out.seekOrder.add(t);
                }
                if (!leftTracks.isEmpty()) {
                    int endBound = totalTracks - 1;
                    out.totalHeadMovements += Math.abs(endBound - head);
                    head = endBound;
                    out.seekOrder.add(endBound);

                    out.totalHeadMovements += endBound;
                    head = 0;
                    out.seekOrder.add(0);

                    for (int t : leftTracks) {
                        out.totalHeadMovements += Math.abs(t - head);
                        head = t;
                        out.seekOrder.add(t);
                    }
                }
            } else {
                for (int i = leftTracks.size() - 1; i >= 0; --i) {
                    out.totalHeadMovements += Math.abs(leftTracks.get(i) - head);
                    head = leftTracks.get(i);
                    out.seekOrder.add(leftTracks.get(i));
                }
                if (!rightTracks.isEmpty()) {
                    int startBound = 0;
                    out.totalHeadMovements += Math.abs(startBound - head);
                    head = startBound;
                    out.seekOrder.add(startBound);

                    int endBound = totalTracks - 1;
                    out.totalHeadMovements += endBound;
                    head = endBound;
                    out.seekOrder.add(endBound);

                    for (int i = rightTracks.size() - 1; i >= 0; --i) {
                        out.totalHeadMovements += Math.abs(rightTracks.get(i) - head);
                        head = rightTracks.get(i);
                        out.seekOrder.add(rightTracks.get(i));
                    }
                }
            }

            out.avgSeekDistance = requests.isEmpty() ? 0.0 : ((double) out.totalHeadMovements / requests.size());
            return out;
        }

        public static DiskOutput scheduleLOOK(int initialHead, List<Integer> requests, DiskDirection dir) {
            DiskOutput out = new DiskOutput();
            out.algorithmName = "LOOK";
            out.initialHead = initialHead;
            out.trackLimit = 200;
            out.totalHeadMovements = 0;

            if (requests.isEmpty()) {
                out.avgSeekDistance = 0.0;
                return out;
            }

            List<Integer> leftTracks = new ArrayList<>();
            List<Integer> rightTracks = new ArrayList<>();
            for (int t : requests) {
                if (t < initialHead) leftTracks.add(t);
                if (t >= initialHead) rightTracks.add(t);
            }
            Collections.sort(leftTracks);
            Collections.sort(rightTracks);

            int head = initialHead;
            if (dir == DiskDirection.RIGHT_DIRECTION) {
                for (int t : rightTracks) {
                    out.totalHeadMovements += Math.abs(t - head);
                    head = t;
                    out.seekOrder.add(t);
                }
                for (int i = leftTracks.size() - 1; i >= 0; --i) {
                    out.totalHeadMovements += Math.abs(leftTracks.get(i) - head);
                    head = leftTracks.get(i);
                    out.seekOrder.add(leftTracks.get(i));
                }
            } else {
                for (int i = leftTracks.size() - 1; i >= 0; --i) {
                    out.totalHeadMovements += Math.abs(leftTracks.get(i) - head);
                    head = leftTracks.get(i);
                    out.seekOrder.add(leftTracks.get(i));
                }
                for (int t : rightTracks) {
                    out.totalHeadMovements += Math.abs(t - head);
                    head = t;
                    out.seekOrder.add(t);
                }
            }

            out.avgSeekDistance = requests.isEmpty() ? 0.0 : ((double) out.totalHeadMovements / requests.size());
            return out;
        }

        public static DiskOutput scheduleCLOOK(int initialHead, List<Integer> requests, DiskDirection dir) {
            DiskOutput out = new DiskOutput();
            out.algorithmName = "C-LOOK (Circular LOOK)";
            out.initialHead = initialHead;
            out.trackLimit = 200;
            out.totalHeadMovements = 0;

            if (requests.isEmpty()) {
                out.avgSeekDistance = 0.0;
                return out;
            }

            List<Integer> leftTracks = new ArrayList<>();
            List<Integer> rightTracks = new ArrayList<>();
            for (int t : requests) {
                if (t < initialHead) leftTracks.add(t);
                if (t >= initialHead) rightTracks.add(t);
            }
            Collections.sort(leftTracks);
            Collections.sort(rightTracks);

            int head = initialHead;
            if (dir == DiskDirection.RIGHT_DIRECTION) {
                for (int t : rightTracks) {
                    out.totalHeadMovements += Math.abs(t - head);
                    head = t;
                    out.seekOrder.add(t);
                }
                for (int t : leftTracks) {
                    out.totalHeadMovements += Math.abs(t - head);
                    head = t;
                    out.seekOrder.add(t);
                }
            } else {
                for (int i = leftTracks.size() - 1; i >= 0; --i) {
                    out.totalHeadMovements += Math.abs(leftTracks.get(i) - head);
                    head = leftTracks.get(i);
                    out.seekOrder.add(leftTracks.get(i));
                }
                for (int i = rightTracks.size() - 1; i >= 0; --i) {
                    out.totalHeadMovements += Math.abs(rightTracks.get(i) - head);
                    head = rightTracks.get(i);
                    out.seekOrder.add(rightTracks.get(i));
                }
            }

            out.avgSeekDistance = requests.isEmpty() ? 0.0 : ((double) out.totalHeadMovements / requests.size());
            return out;
        }

        public static void compareAll(int head, List<Integer> requests, int totalTracks, DiskDirection dir) {
            DiskOutput rFCFS = scheduleFCFS(head, requests);
            DiskOutput rSSTF = scheduleSSTF(head, requests);
            DiskOutput rSCAN = scheduleSCAN(head, requests, totalTracks, dir);
            DiskOutput rCSCAN = scheduleCSCAN(head, requests, totalTracks, dir);
            DiskOutput rLOOK = scheduleLOOK(head, requests, dir);
            DiskOutput rCLOOK = scheduleCLOOK(head, requests, dir);

            System.out.println("\n============================== DISK SCHEDULING COMPARISON ==============================");
            System.out.printf("Initial Head: %d | Total Requests: %d | Track Limit: [0-%d]%n", head, requests.size(), totalTracks - 1);
            System.out.println("----------------------------------------------------------------------------------------");
            System.out.printf("%-32s%-25s%-22s%n", "Algorithm", "Total Head Movement", "Avg Seek Distance");
            System.out.println("----------------------------------------------------------------------------------------");

            printDiskRow(rFCFS);
            printDiskRow(rSSTF);
            printDiskRow(rSCAN);
            printDiskRow(rCSCAN);
            printDiskRow(rLOOK);
            printDiskRow(rCLOOK);
            System.out.println("========================================================================================");
        }

        private static void printDiskRow(DiskOutput r) {
            System.out.printf("%-32s%-25s%-22.2f tracks%n",
                    r.algorithmName,
                    (r.totalHeadMovements + " tracks"),
                    r.avgSeekDistance);
        }
    }

    public static class AuditRecord {
        public String timeStamp;
        public String category;
        public int txId;
        public String details;

        public AuditRecord(String ts, String cat, int id, String det) {
            this.timeStamp = ts;
            this.category = cat;
            this.txId = id;
            this.details = det;
        }
    }

    public static class TransactionLogger {
        private final List<AuditRecord> records = new ArrayList<>();
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        private String getTimestamp() {
            return LocalDateTime.now().format(FORMATTER);
        }

        public synchronized void log(String category, int txId, String details) {
            records.add(new AuditRecord(getTimestamp(), category, txId, details));
        }

        public synchronized void saveToFile(String filename) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                writer.println("===============================================================================");
                writer.println("        CONCURRENT BANKING SYSTEM - TRANSACTION & RESOURCE AUDIT LOG           ");
                writer.println("===============================================================================");
                writer.printf("%-22s%-15s%-8s%s%n", "Timestamp", "Category", "TxID", "Details");
                writer.println("-------------------------------------------------------------------------------");
                for (AuditRecord r : records) {
                    writer.printf("%-22s%-15s%-8s%s%n",
                            r.timeStamp,
                            r.category,
                            (r.txId != -1 ? String.valueOf(r.txId) : "-"),
                            r.details);
                }
                writer.println("===============================================================================");
            } catch (IOException e) {
                System.err.println("Error saving log file: " + e.getMessage());
            }
        }

        public synchronized void saveToFile() {
            saveToFile("transaction_log.txt");
        }

        public synchronized void displayLogs(int maxCount) {
            System.out.println("\n=========================== TRANSACTION AUDIT LOGS ===========================");
            System.out.printf("%-22s%-14s%-8s%s%n", "Timestamp", "Category", "TxID", "Details");
            System.out.println("------------------------------------------------------------------------------");

            int start = Math.max(0, records.size() - maxCount);
            for (int i = start; i < records.size(); ++i) {
                AuditRecord r = records.get(i);
                System.out.printf("%-22s%-14s%-8s%s%n",
                        r.timeStamp,
                        r.category,
                        (r.txId != -1 ? String.valueOf(r.txId) : "-"),
                        r.details);
            }
            System.out.println("==============================================================================");
        }

        public synchronized int count() {
            return records.size();
        }

        public synchronized void clear() {
            records.clear();
        }
    }

    public static class PerformanceSummary {
        public String cpuAlgo;
        public double cpuWait;
        public double cpuTAT;
        public double cpuResp;
        public double cpuUtil;
        public double cpuThroughput;

        public String memAlgo;
        public int memTotal;
        public int memAllocated;
        public double memUtil;
        public int memFrag;
        public int memHoles;

        public String diskAlgo;
        public int diskMovement;
        public double diskAvgSeek;

        public int totalTxs;
        public int successfulTxs;
        public double syncDuration;

        public void display() {
            System.out.println("\n===============================================================================");
            System.out.println("       CONCURRENT BANKING SYSTEM - COMPREHENSIVE OS PERFORMANCE REPORT         ");
            System.out.println("===============================================================================");

            System.out.println("\n[1] CPU SCHEDULING PERFORMANCE (" + cpuAlgo + ")");
            System.out.printf("  - Average Waiting Time    : %.2f ms%n", cpuWait);
            System.out.printf("  - Average Turnaround Time : %.2f ms%n", cpuTAT);
            System.out.printf("  - Average Response Time   : %.2f ms%n", cpuResp);
            System.out.printf("  - CPU Core Utilization    : %.2f %%%n", cpuUtil);
            System.out.printf("  - System Throughput       : %.2f tx/unit%n", cpuThroughput);

            System.out.println("\n[2] MEMORY MANAGEMENT PERFORMANCE (" + memAlgo + ")");
            System.out.printf("  - Total Main Memory       : %d KB%n", memTotal);
            System.out.printf("  - Total Allocated Memory  : %d KB%n", memAllocated);
            System.out.printf("  - Memory Utilization      : %.2f %%%n", memUtil);
            System.out.printf("  - External Fragmentation  : %d KB%n", memFrag);
            System.out.printf("  - Free Memory Holes       : %d%n", memHoles);

            System.out.println("\n[3] DISK I/O SCHEDULING PERFORMANCE (" + diskAlgo + ")");
            System.out.printf("  - Total Head Movement     : %d tracks%n", diskMovement);
            System.out.printf("  - Average Seek Distance   : %.2f tracks/req%n", diskAvgSeek);

            System.out.println("\n[4] CONCURRENCY & SYNCHRONIZATION PERFORMANCE");
            System.out.printf("  - Total Workload Tasks    : %d%n", totalTxs);
            System.out.printf("  - Successfully Executed   : %d%n", successfulTxs);
            double rate = (totalTxs > 0) ? (((double) successfulTxs / totalTxs) * 100.0) : 0.0;
            System.out.printf("  - Success Rate            : %.2f %%%n", rate);
            System.out.printf("  - Multithread Duration    : %.2f ms%n", syncDuration);
            System.out.println("===============================================================================");
        }
    }
}
