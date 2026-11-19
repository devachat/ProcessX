package processx;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MemoryManagement {

    public enum MemoryStrategy {
        FIRST_FIT,
        BEST_FIT,
        WORST_FIT,
        NEXT_FIT
    }

    public static class MemoryBlock {
        public int blockId;
        public int startAddress;
        public int size;
        public boolean isAllocated;
        public int assignedTxId;
        public String processName;

        public MemoryBlock(int id, int start, int sz, boolean alloc, int tx, String name) {
            this.blockId = id;
            this.startAddress = start;
            this.size = sz;
            this.isAllocated = alloc;
            this.assignedTxId = tx;
            this.processName = name;
        }

        public MemoryBlock(MemoryBlock o) {
            this(o.blockId, o.startAddress, o.size, o.isAllocated, o.assignedTxId, o.processName);
        }
    }

    public static class MemoryStats {
        public int totalMemory;
        public int allocatedMemory;
        public int freeMemory;
        public double utilization;
        public int holeCount;
        public int largestHole;
        public double avgHoleSize;
        public int externalFragmentationBytes;
        public double externalFragmentationPercent;
    }

    public static class MemoryManager {
        private final int totalMemorySize;
        private final List<MemoryBlock> blocks = new ArrayList<>();
        private int nextBlockId;
        private int nextFitPointer;

        public MemoryManager(int totalSize) {
            this.totalMemorySize = totalSize;
            this.nextBlockId = 1;
            this.nextFitPointer = 0;
            reset();
        }

        public MemoryManager() {
            this(1024);
        }

        public void reset() {
            blocks.clear();
            nextBlockId = 1;
            nextFitPointer = 0;
            blocks.add(new MemoryBlock(nextBlockId++, 0, totalMemorySize, false, -1, "Free"));
        }

        private int allocateFirstFit(int txId, int size, String name) {
            for (int i = 0; i < blocks.size(); ++i) {
                MemoryBlock b = blocks.get(i);
                if (!b.isAllocated && b.size >= size) {
                    int orig = b.size;
                    int start = b.startAddress;

                    b.isAllocated = true;
                    b.assignedTxId = txId;
                    b.processName = name;
                    b.size = size;

                    if (orig > size) {
                        MemoryBlock remainder = new MemoryBlock(nextBlockId++, start + size, orig - size, false, -1, "Free");
                        blocks.add(i + 1, remainder);
                    }
                    return b.blockId;
                }
            }
            return -1;
        }

        private int allocateBestFit(int txId, int size, String name) {
            int bestIdx = -1;
            int minLeftover = Integer.MAX_VALUE;

            for (int i = 0; i < blocks.size(); ++i) {
                MemoryBlock b = blocks.get(i);
                if (!b.isAllocated && b.size >= size) {
                    int leftover = b.size - size;
                    if (leftover < minLeftover) {
                        minLeftover = leftover;
                        bestIdx = i;
                    }
                }
            }

            if (bestIdx != -1) {
                MemoryBlock b = blocks.get(bestIdx);
                int orig = b.size;
                int start = b.startAddress;

                b.isAllocated = true;
                b.assignedTxId = txId;
                b.processName = name;
                b.size = size;

                if (orig > size) {
                    MemoryBlock remainder = new MemoryBlock(nextBlockId++, start + size, orig - size, false, -1, "Free");
                    blocks.add(bestIdx + 1, remainder);
                }
                return b.blockId;
            }
            return -1;
        }

        private int allocateWorstFit(int txId, int size, String name) {
            int worstIdx = -1;
            int maxLeftover = -1;

            for (int i = 0; i < blocks.size(); ++i) {
                MemoryBlock b = blocks.get(i);
                if (!b.isAllocated && b.size >= size) {
                    int leftover = b.size - size;
                    if (leftover > maxLeftover) {
                        maxLeftover = leftover;
                        worstIdx = i;
                    }
                }
            }

            if (worstIdx != -1) {
                MemoryBlock b = blocks.get(worstIdx);
                int orig = b.size;
                int start = b.startAddress;

                b.isAllocated = true;
                b.assignedTxId = txId;
                b.processName = name;
                b.size = size;

                if (orig > size) {
                    MemoryBlock remainder = new MemoryBlock(nextBlockId++, start + size, orig - size, false, -1, "Free");
                    blocks.add(worstIdx + 1, remainder);
                }
                return b.blockId;
            }
            return -1;
        }

        private int allocateNextFit(int txId, int size, String name) {
            if (blocks.isEmpty()) return -1;
            int total = blocks.size();
            int startPos = nextFitPointer % total;

            for (int step = 0; step < total; ++step) {
                int i = (startPos + step) % total;
                MemoryBlock b = blocks.get(i);
                if (!b.isAllocated && b.size >= size) {
                    int orig = b.size;
                    int start = b.startAddress;

                    b.isAllocated = true;
                    b.assignedTxId = txId;
                    b.processName = name;
                    b.size = size;

                    if (orig > size) {
                        MemoryBlock remainder = new MemoryBlock(nextBlockId++, start + size, orig - size, false, -1, "Free");
                        blocks.add(i + 1, remainder);
                        nextFitPointer = i + 1;
                    } else {
                        nextFitPointer = (i + 1) % blocks.size();
                    }
                    return b.blockId;
                }
            }
            return -1;
        }

        private void coalesceFree() {
            if (blocks.size() <= 1) return;
            List<MemoryBlock> merged = new ArrayList<>();
            merged.add(blocks.get(0));

            for (int i = 1; i < blocks.size(); ++i) {
                MemoryBlock prev = merged.get(merged.size() - 1);
                MemoryBlock curr = blocks.get(i);
                if (!prev.isAllocated && !curr.isAllocated) {
                    prev.size += curr.size;
                } else {
                    merged.add(curr);
                }
            }
            blocks.clear();
            blocks.addAll(merged);
            if (nextFitPointer >= blocks.size()) {
                nextFitPointer = 0;
            }
        }

        public boolean allocate(int txId, int size, MemoryStrategy strategy, String name) {
            int id = -1;
            if (strategy == MemoryStrategy.FIRST_FIT) id = allocateFirstFit(txId, size, name);
            else if (strategy == MemoryStrategy.BEST_FIT) id = allocateBestFit(txId, size, name);
            else if (strategy == MemoryStrategy.WORST_FIT) id = allocateWorstFit(txId, size, name);
            else if (strategy == MemoryStrategy.NEXT_FIT) id = allocateNextFit(txId, size, name);
            return id != -1;
        }

        public boolean allocate(int txId, int size, MemoryStrategy strategy) {
            return allocate(txId, size, strategy, "TX");
        }

        public boolean deallocate(int txId) {
            boolean found = false;
            for (MemoryBlock b : blocks) {
                if (b.isAllocated && b.assignedTxId == txId) {
                    b.isAllocated = false;
                    b.assignedTxId = -1;
                    b.processName = "Free";
                    found = true;
                }
            }
            if (found) coalesceFree();
            return found;
        }

        public void compact() {
            List<MemoryBlock> compacted = new ArrayList<>();
            int currentAddr = 0;
            int freeTotal = 0;

            for (MemoryBlock b : blocks) {
                if (b.isAllocated) {
                    MemoryBlock shifted = new MemoryBlock(b);
                    shifted.startAddress = currentAddr;
                    currentAddr += b.size;
                    compacted.add(shifted);
                } else {
                    freeTotal += b.size;
                }
            }

            if (freeTotal > 0) {
                MemoryBlock freeBlock = new MemoryBlock(nextBlockId++, currentAddr, freeTotal, false, -1, "Free (Compacted)");
                compacted.add(freeBlock);
            }

            blocks.clear();
            blocks.addAll(compacted);
            nextFitPointer = 0;
        }

        public MemoryStats getStats() {
            MemoryStats s = new MemoryStats();
            s.totalMemory = totalMemorySize;
            s.allocatedMemory = 0;
            s.freeMemory = 0;
            s.holeCount = 0;
            s.largestHole = 0;

            Set<Integer> uniqueHoleSizes = new HashSet<>();

            for (MemoryBlock b : blocks) {
                if (b.isAllocated) {
                    s.allocatedMemory += b.size;
                } else {
                    s.freeMemory += b.size;
                    s.holeCount++;
                    s.largestHole = Math.max(s.largestHole, b.size);
                    uniqueHoleSizes.add(b.size);
                }
            }

            s.utilization = (totalMemorySize > 0) ? (((double) s.allocatedMemory / totalMemorySize) * 100.0) : 0.0;
            s.avgHoleSize = (s.holeCount > 0) ? ((double) s.freeMemory / s.holeCount) : 0.0;

            if (s.holeCount > 1) {
                s.externalFragmentationBytes = s.freeMemory - s.largestHole;
                s.externalFragmentationPercent = (totalMemorySize > 0) ? (((double) s.externalFragmentationBytes / totalMemorySize) * 100.0) : 0.0;
            } else {
                s.externalFragmentationBytes = 0;
                s.externalFragmentationPercent = 0.0;
            }
            return s;
        }

        public void displayMemoryMap() {
            System.out.println("\n============================== PHYSICAL MEMORY MAP ==============================");
            System.out.printf("%-10s%-15s%-12s%-12s%-12s%-16s%n",
                    "Block ID", "Address Range", "Size (KB)", "Status", "TxID", "Owner");
            System.out.println("--------------------------------------------------------------------------------");

            for (MemoryBlock b : blocks) {
                String range = "[" + b.startAddress + "-" + (b.startAddress + b.size - 1) + "]";
                System.out.printf("%-10d%-15s%-12d%-12s%-12s%-16s%n",
                        b.blockId,
                        range,
                        b.size,
                        (b.isAllocated ? "ALLOCATED" : "FREE"),
                        (b.isAllocated ? String.valueOf(b.assignedTxId) : "-"),
                        b.processName);
            }
            System.out.println("--------------------------------------------------------------------------------");
            System.out.print("Visual Layout: |");
            for (MemoryBlock b : blocks) {
                if (b.isAllocated) {
                    System.out.print(" TX" + b.assignedTxId + " (" + b.size + "KB) |");
                } else {
                    System.out.print(" FREE (" + b.size + "KB) |");
                }
            }
            System.out.println("\n================================================================================");
        }

        public void displayStats(String header) {
            MemoryStats s = getStats();
            System.out.println("\n------------------ " + header + " ------------------");
            System.out.printf("Total Memory Space          : %d KB%n", s.totalMemory);
            System.out.printf("Allocated Memory            : %d KB%n", s.allocatedMemory);
            System.out.printf("Free Memory Available       : %d KB%n", s.freeMemory);
            System.out.printf("Memory Utilization Rate     : %.2f %%%n", s.utilization);
            System.out.printf("Number of Free Holes        : %d%n", s.holeCount);
            System.out.printf("Largest Contiguous Hole     : %d KB%n", s.largestHole);
            System.out.printf("Average Hole Size           : %.2f KB%n", s.avgHoleSize);
            System.out.printf("External Fragmentation      : %d KB (%.2f %%)%n",
                    s.externalFragmentationBytes, s.externalFragmentationPercent);
            System.out.println("---------------------------------------------------------");
        }

        public void displayStats() {
            displayStats("Memory Status");
        }

        public String getStrategyName(MemoryStrategy st) {
            if (st == MemoryStrategy.FIRST_FIT) return "First Fit";
            if (st == MemoryStrategy.BEST_FIT) return "Best Fit";
            if (st == MemoryStrategy.WORST_FIT) return "Worst Fit";
            return "Next Fit";
        }

        public static void compareStrategies(int totalMem, List<int[]> workload) {
            MemoryStrategy[] list = {
                    MemoryStrategy.FIRST_FIT,
                    MemoryStrategy.BEST_FIT,
                    MemoryStrategy.WORST_FIT,
                    MemoryStrategy.NEXT_FIT
            };

            System.out.println("\n============================= MEMORY ALLOCATION ALGORITHM COMPARISON =============================");
            System.out.printf("%-15s%-12s%-10s%-16s%-10s%-15s%-20s%n",
                    "Algorithm", "Allocated", "Free", "Util (%)", "Holes", "Largest Hole", "Ext Frag (KB / %)");
            System.out.println("--------------------------------------------------------------------------------------------------");

            for (MemoryStrategy st : list) {
                MemoryManager mm = new MemoryManager(totalMem);
                for (int[] item : workload) {
                    mm.allocate(item[0], item[1], st, "P" + item[0]);
                }
                MemoryStats s = mm.getStats();
                String extFragStr = String.format("%d KB (%.2f%%)", s.externalFragmentationBytes, s.externalFragmentationPercent);
                System.out.printf("%-15s%-12d%-10d%-16.2f%-10d%-15d%-20s%n",
                        mm.getStrategyName(st),
                        s.allocatedMemory,
                        s.freeMemory,
                        s.utilization,
                        s.holeCount,
                        s.largestHole,
                        extFragStr);
            }
            System.out.println("==================================================================================================");
        }
    }
}
