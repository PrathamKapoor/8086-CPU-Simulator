package cpu.biu;

import java.util.Arrays;

public final class PrefetchQueue {
    public static final int CAPACITY = 6;
    private final int[] bytes = new int[CAPACITY];
    private int size = 0;

    public int size() { return size; }
    public int capacity() { return CAPACITY; }
    public boolean isEmpty() { return size == 0; }
    public boolean isFull() { return size == CAPACITY; }

    public void enqueue(int b) {
        if (isFull()) {
            throw new IllegalStateException("Prefetch queue overflow: size=" + size);
        }
        bytes[size++] = b & 0xFF;
    }

    public int dequeue() {
        if (isEmpty()) {
            throw new IllegalStateException("Prefetch queue underflow");
        }
        int b = bytes[0];
        System.arraycopy(bytes, 1, bytes, 0, size - 1);
        size--;
        bytes[size] = 0; // clear removed slot
        return b;
    }

    public int peek() {
        if (isEmpty()) throw new IllegalStateException("Prefetch queue empty: peek");
        return bytes[0];
    }

    public int peek(int n) {
        if (n < 0 || n >= size) throw new IndexOutOfBoundsException("Prefetch peek n=" + n + " size=" + size);
        return bytes[n];
    }

    public void clear() {
        Arrays.fill(bytes, 0);
        size = 0;
    }

    public int availableBytes() {
        return size;
    }

    public int[] getContents() {
        return Arrays.copyOf(bytes, size);
    }
}
