package simulator.profiler;

public record PerformanceSnapshot(long totalCycles, long instructionsRetired, double cpi, long bytesFetched,
                                  long bytesConsumed, double averageQueueOccupancy, long queueEmptyCycles,
                                  long queueFullCycles, long biuActiveCycles, long euActiveCycles, long overlapCycles,
                                  long euStallCycles, long biuStallCycles, long memoryReads, long memoryWrites,
                                  long controlTransferFlushes, long flushedBytes) {
    public String toJson() { return "{\"totalCycles\":"+totalCycles+",\"instructionsRetired\":"+instructionsRetired+",\"cpi\":"+cpi+",\"bytesFetched\":"+bytesFetched+",\"bytesConsumed\":"+bytesConsumed+",\"averageQueueOccupancy\":"+averageQueueOccupancy+",\"queueEmptyCycles\":"+queueEmptyCycles+",\"queueFullCycles\":"+queueFullCycles+",\"biuActiveCycles\":"+biuActiveCycles+",\"euActiveCycles\":"+euActiveCycles+",\"overlapCycles\":"+overlapCycles+",\"euStallCycles\":"+euStallCycles+",\"biuStallCycles\":"+biuStallCycles+",\"memoryReads\":"+memoryReads+",\"memoryWrites\":"+memoryWrites+",\"controlTransferFlushes\":"+controlTransferFlushes+",\"flushedBytes\":"+flushedBytes+"}"; }
}
