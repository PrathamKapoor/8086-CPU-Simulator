package cpu.eu;

import cpu.CPU;
import cpu.registers.*;
import instruction.Instruction;
import microoperation.MicroOperation;
import microoperation.MicroOperationExecutor;
import microoperation.MicroOperationType;
import cpu.biu.PrefetchQueue;
import cpu.biu.BusState;
import memory.Memory;

public class ExecutionUnit {
    private final CPU cpu;
    private final PrefetchQueue prefetchQueue;
    private boolean decoding = false;
    private boolean executing = false;

    public ExecutionUnit(CPU cpu, PrefetchQueue prefetchQueue) {
        this.cpu = cpu;
        this.prefetchQueue = prefetchQueue;
    }

    public boolean canConsume() {
        return prefetchQueue.availableBytes() > 0;
    }

    public int consumeByte() {
        return prefetchQueue.dequeue();
    }

    public int peekByte() {
        return prefetchQueue.peek();
    }

    public boolean isDecoding() {
        return decoding;
    }

    public boolean isExecuting() {
        return executing;
    }

    public void beginDecode() {
        decoding = true;
        executing = false;
    }

    public void endDecode() {
        decoding = false;
    }

    public void beginExecute() {
        decoding = false;
        executing = true;
    }

    public void endExecute() {
        executing = false;
    }

    public PrefetchQueue getPrefetchQueue() {
        return prefetchQueue;
    }

    public String getStateString() {
        if (executing) return "EXECUTE";
        if (decoding) return "DECODE";
        return "IDLE";
    }
}
