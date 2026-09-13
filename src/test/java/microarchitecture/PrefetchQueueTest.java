package microarchitecture;

import cpu.biu.PrefetchQueue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrefetchQueueTest {
    @Test
    void empty_queue() {
        PrefetchQueue q = new PrefetchQueue();
        assertTrue(q.isEmpty());
        assertEquals(6, q.capacity());
    }

    @Test
    void enqueue_dequeue_fifo() {
        PrefetchQueue q = new PrefetchQueue();
        q.enqueue(0xB8);
        q.enqueue(0x34);
        assertEquals(2, q.size());
        assertEquals(0xB8, q.dequeue());
        assertEquals(0x34, q.dequeue());
        assertTrue(q.isEmpty());
    }

    @Test
    void capacity_limit() {
        PrefetchQueue q = new PrefetchQueue();
        for (int i = 0; i < 6; i++) q.enqueue(0x01);
        assertTrue(q.isFull());
        assertThrows(IllegalStateException.class, () -> q.enqueue(0xFF));
    }

    @Test
    void underflow_throws() {
        PrefetchQueue q = new PrefetchQueue();
        assertThrows(IllegalStateException.class, q::dequeue);
    }

    @Test
    void peek() {
        PrefetchQueue q = new PrefetchQueue();
        q.enqueue(0xAB);
        assertEquals(0xAB, q.peek());
        assertEquals(0xAB, q.peek(0));
    }

    @Test
    void peek_offset() {
        PrefetchQueue q = new PrefetchQueue();
        q.enqueue(0x01);
        q.enqueue(0x02);
        assertEquals(0x01, q.peek(0));
        assertEquals(0x02, q.peek(1));
    }

    @Test
    void peek_index_out_of_bounds() {
        PrefetchQueue q = new PrefetchQueue();
        q.enqueue(0x01);
        assertThrows(IndexOutOfBoundsException.class, () -> q.peek(1));
    }

    @Test
    void clear_resets() {
        PrefetchQueue q = new PrefetchQueue();
        q.enqueue(0x01);
        q.enqueue(0x02);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
    }

    @Test
    void available_bytes() {
        PrefetchQueue q = new PrefetchQueue();
        assertEquals(0, q.availableBytes());
        q.enqueue(0x01);
        assertEquals(1, q.availableBytes());
    }

    @Test
    void contents_copy() {
        PrefetchQueue q = new PrefetchQueue();
        q.enqueue(0xAA);
        q.enqueue(0xBB);
        int[] contents = q.getContents();
        assertEquals(2, contents.length);
        assertEquals(0xAA, contents[0]);
        assertEquals(0xBB, contents[1]);
    }
}
