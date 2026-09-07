package net.irisshaders.iris.pipeline;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.irisshaders.iris.IrisCommon;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;
import static net.irisshaders.iris.IrisLogging.IRIS_LOGGER;

/** Bounded debug capture, owned by the world pipeline; all calls stay on the render thread. */
public final class GpuProfiler {
	public enum Count {
		MIP_REQUESTS, MIP_GENERATED, MIP_SKIPPED, HISTORY_PUBLISHES,
		HISTORY_COPIES, HISTORY_ALIAS_READS, HISTORY_EAGER_PUBLISHES,
		HISTORY_MIP_FALLBACKS, HISTORY_UNTRACKED_FALLBACKS
	}
	private static GpuProfiler active;
	private final Map<String, long[]> totals = new LinkedHashMap<>();
	private final long[] counts = new long[Count.values().length];
	private final Map<String, long[]> drawCounts = new LinkedHashMap<>();
	private static String drawStage;
	private GpuTimestampRing ring;
	private long started, lastLog;
	private int frame, captureFrames, sampledFrames;
	private boolean initialized, stopped, capturing, sampling;

	public void beginFrame() {
		active = this;
		if (stopped) return;
		long now = System.nanoTime();
		if (!initialized) {
			initialized = true;
			if (!IrisCommon.getIrisConfig().areDebugOptionsEnabled()) { stopped = true; return; }
			var caps = GL.getCapabilities();
			if (!(caps.OpenGL33 || caps.GL_ARB_timer_query)
				|| GL15C.glGetQueryi(GL33C.GL_TIMESTAMP, GL15C.GL_QUERY_COUNTER_BITS) == 0) {
				IRIS_LOGGER.info("[GpuProfile] Disabled: GPU timestamps unavailable");
				stopped = true;
				return;
			}
			ring = new GpuTimestampRing(new GpuTimestampRing.Backend() {
				public int create() { return GL15C.glGenQueries(); }
				public void stamp(int q) { GL33C.glQueryCounter(q, GL33C.GL_TIMESTAMP); }
				public boolean available(int q) { return GL15C.glGetQueryObjecti(q, GL15C.GL_QUERY_RESULT_AVAILABLE) != 0; }
				public long result(int q) { return GL33C.glGetQueryObjectui64(q, GL15C.GL_QUERY_RESULT); }
				public void delete(int q) { GL15C.glDeleteQueries(q); }
			}, 512);
			started = lastLog = now;
			IRIS_LOGGER.info("[GpuProfile] Armed: warmup=15s capture=30s drain<=5s stride=4 ring=512 pairs; production cutout-only, no automatic experiments; reload rearms");
		}
		frame++;
		ring.poll(frame, (name, ns) -> {
			long[] value = totals.get(name);
			if (value == null) {
				if (totals.size() >= 256) return;
				value = new long[3];
				totals.put(name, value);
			}
			value[0]++;
			value[1] += ns;
			value[2] = Math.max(value[2], ns);
		});
		long age = now - started;
		capturing = age >= 15_000_000_000L && age < 45_000_000_000L;
		sampling = capturing && frame % 4 == 0;
		if (capturing) captureFrames++;
		if (sampling) sampledFrames++;
		if (age >= 45_000_000_000L && (ring.pending() == 0 || age >= 50_000_000_000L)) {
			stop("complete");
		} else if (age >= 20_000_000_000L && now - lastLog >= 5_000_000_000L) {
			report("progress");
			lastLog = now;
		}
	}

	public static int begin(String name) {
		return active != null && active.sampling ? active.ring.begin(name, active.frame) : -1;
	}
	public static void end(int slot) {
		if (slot >= 0 && active != null) active.ring.end(slot);
	}
	public static void count(Count count) {
		if (active != null && active.capturing) active.counts[count.ordinal()]++;
	}
	public static boolean isCapturing() { return active != null && active.capturing; }
	public static String setDrawStage(String stage) {
		String previous = drawStage;
		drawStage = stage;
		return previous;
	}
	/** One CPU submission, not fragment invocations or a visibility estimate. */
	public static void terrainBatch(int draws) {
		if (!isCapturing() || drawStage == null) return;
		long[] value = active.drawCounts.get(drawStage);
		if (value == null) {
			if (active.drawCounts.size() >= 32) return;
			value = new long[3];
			active.drawCounts.put(drawStage, value);
		}
		value[0]++;
		value[1] += draws;
	}
	/** Pass invocations provide the denominator for actual submission counts. */
	public static void terrainPass() {
		if (!isCapturing() || drawStage == null) return;
		long[] value = active.drawCounts.get(drawStage);
		if (value == null) {
			if (active.drawCounts.size() >= 32) return;
			value = new long[3];
			active.drawCounts.put(drawStage, value);
		}
		value[2]++;
	}
	public void endFrame() { capturing = sampling = false; if (active == this) { active = null; drawStage = null; } }

	private void report(String reason) {
		IRIS_LOGGER.info("[GpuProfile] " + reason + " frames=" + captureFrames + " sampledFrames=" + sampledFrames
			+ " droppedScopes=" + ring.dropped() + " pending=" + ring.pending()
			+ " counters=" + Arrays.toString(Count.values()) + " values=" + Arrays.toString(counts));
		drawCounts.forEach((name, v) -> IRIS_LOGGER.info("[GpuProfile] " + name
			+ " terrainMultiDrawCalls=" + v[0] + " subDraws=" + v[1] + " passInvocations=" + v[2] + " captureFrames=" + captureFrames));
		totals.entrySet().stream().sorted(Comparator.<Map.Entry<String, long[]>>comparingDouble(
			e -> (double) e.getValue()[1] / e.getValue()[0]).reversed()).limit(reason.equals("progress") ? 24 : 256).forEach(e -> {
			long[] v = e.getValue();
			IRIS_LOGGER.info(String.format(Locale.ROOT, "[GpuProfile] %s GPU mean=%.4fms max=%.4fms samples=%d",
				e.getKey(), v[1] / (double) v[0] / 1e6, v[2] / 1e6, v[0]));
		});
	}
	private void stop(String reason) {
		capturing = sampling = false;
		stopped = true;
		if (ring != null) { report(reason); ring.close(); }
	}
	public void destroy() {
		if (!stopped) stop("destroy/reload (partial)");
		if (active == this) { active = null; drawStage = null; }
	}
}
