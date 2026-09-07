package net.irisshaders.iris.pipeline;

/** Render-thread-only timestamp pairs. No GL dependency, so lifecycle is testable. */
public final class GpuTimestampRing implements AutoCloseable {
	public interface Backend {
		int create();
		void stamp(int query);
		boolean available(int query);
		long result(int query);
		void delete(int query);
	}
	public interface Sink { void accept(String name, long nanos); }
	private final Backend backend;
	private final int[] starts, ends, state, frames;
	private final String[] names;
	private int cursor;
	private boolean closed;
	private long dropped;

	public GpuTimestampRing(Backend backend, int capacity) {
		if (capacity <= 0) throw new IllegalArgumentException("capacity");
		this.backend = backend;
		starts = new int[capacity];
		ends = new int[capacity];
		state = new int[capacity];
		frames = new int[capacity];
		names = new String[capacity];
	}

	public int begin(String name, int frame) {
		if (closed) return -1;
		for (int n = 0; n < state.length; n++) {
			int i = cursor;
			cursor = (cursor + 1) % state.length;
			if (state[i] != 0) continue;
			if (starts[i] == 0) starts[i] = backend.create();
			if (ends[i] == 0) ends[i] = backend.create();
			names[i] = name;
			frames[i] = frame;
			state[i] = 1;
			backend.stamp(starts[i]);
			return i;
		}
		dropped++;
		return -1;
	}

	public void end(int slot) {
		if (closed || slot < 0) return;
		if (state[slot] != 1) throw new IllegalStateException("Unopened timestamp pair");
		backend.stamp(ends[slot]);
		state[slot] = 2;
	}

	public void poll(int frame, Sink sink) {
		if (closed) return;
		for (int i = 0; i < state.length; i++) {
			if (state[i] != 2 || frame - frames[i] < 3) continue;
			if (!backend.available(ends[i]) || !backend.available(starts[i])) continue;
			long elapsed = backend.result(ends[i]) - backend.result(starts[i]);
			if (elapsed >= 0) sink.accept(names[i], elapsed);
			state[i] = 0;
			names[i] = null;
		}
	}

	public long dropped() { return dropped; }
	public int pending() {
		int count = 0;
		for (int value : state) if (value != 0) count++;
		return count;
	}

	@Override
	public void close() {
		if (closed) return;
		closed = true;
		for (int i = 0; i < state.length; i++) {
			if (starts[i] != 0) backend.delete(starts[i]);
			if (ends[i] != 0) backend.delete(ends[i]);
			state[i] = 0;
			names[i] = null;
		}
	}
}
