package net.irisshaders.iris.pipeline;

import java.util.HashSet;
import java.util.Set;

/** Run directly with javac/java; deliberately no Minecraft, LWJGL, or test framework. */
public final class GpuTimestampRingTest {
	private static final class Fake implements GpuTimestampRing.Backend {
		int next, stamps, reads, polls, deletes;
		final Set<Integer> available = new HashSet<>();
		final Set<Integer> live = new HashSet<>();
		public int create() { live.add(++next); return next; }
		public void stamp(int q) { check(live.contains(q), "stamp deleted query"); stamps++; }
		public boolean available(int q) { polls++; return available.contains(q); }
		public long result(int q) {
			check(available.contains(q), "blocking result read");
			reads++;
			return q * 100L;
		}
		public void delete(int q) { check(live.remove(q), "double delete"); deletes++; }
	}

	public static void main(String[] args) {
		Fake gl = new Fake();
		var ring = new GpuTimestampRing(gl, 2);
		int outer = ring.begin("outer", 0);
		int inner = ring.begin("inner", 0);
		ring.end(inner);
		ring.end(outer);
		check(ring.begin("full", 1) == -1 && ring.dropped() == 1, "never reuse pending");
		check(gl.next == 4 && gl.stamps == 4, "bounded allocation");
		long[] samples = {0};
		GpuTimestampRing.Sink sink = (name, ns) -> { check(ns == 100, "timestamp difference"); samples[0]++; };
		ring.poll(2, sink);
		check(gl.polls == 0, "do not poll young results");
		ring.poll(3, sink);
		check(gl.reads == 0 && ring.pending() == 2, "unavailable stays pending");
		gl.available.add(2);
		ring.poll(4, sink);
		check(gl.reads == 0, "both timestamps must be available");
		gl.available.add(1);
		ring.poll(5, sink);
		check(samples[0] == 1 && ring.pending() == 1, "partial drain");
		int reused = ring.begin("reused", 5);
		check(reused == outer && gl.next == 4, "reuse only consumed slot");
		ring.end(reused);
		gl.available.add(3);
		gl.available.add(4);
		ring.poll(8, sink);
		check(samples[0] == 3 && ring.pending() == 0, "drain nested pairs");
		ring.begin("abandoned", 9);
		ring.close();
		ring.close();
		ring.end(-1);
		ring.poll(20, sink);
		check(gl.deletes == 4 && gl.live.isEmpty(), "destroy open/pending queries once");
		check(ring.begin("closed", 21) == -1, "closed ring cannot allocate");
		Fake delayed = new Fake();
		var saturated = new GpuTimestampRing(delayed, 3);
		for (int frame = 0; frame < 10_000; frame++) {
			saturated.end(saturated.begin("delayed", frame));
			saturated.poll(frame, sink);
		}
		check(delayed.next == 6 && delayed.reads == 0 && saturated.dropped() == 9997, "bounded under indefinite delay");
		saturated.close();
		check(delayed.live.isEmpty(), "pending cleanup on reload");
		// Layer scopes stay per stage, not per chunk. Exercise a larger nested capture
		// with exceptional exits and the real capacity/stride, without a GL context.
		Fake layered = new Fake();
		var detailed = new GpuTimestampRing(layered, 512);
		long[] resolved = {0};
		for (int frame = 0; frame < 2000; frame++) {
			layered.available.addAll(layered.live);
			detailed.poll(frame, (name, ns) -> resolved[0]++);
			if (frame % 4 != 0) continue;
			int parent = detailed.begin("gbuffer-inclusive", frame);
			try {
				for (int stage = 0; stage < 39; stage++) {
					int child = detailed.begin("layer", frame);
					try {
						if (stage == 38) throw new IllegalArgumentException("render failure");
					} finally {
						detailed.end(child);
					}
				}
			} catch (IllegalArgumentException expected) {
				check(expected.getMessage().equals("render failure"), "expected failure");
			} finally {
				detailed.end(parent);
			}
		}
		check(resolved[0] == 20_000 && detailed.pending() == 0 && detailed.dropped() == 0,
			"detailed capture drains including exceptional layer exit");
		check(layered.next <= 1024, "real capacity remains bounded");
		detailed.close();
		check(layered.live.isEmpty(), "detailed capture cleanup");
		// Weather entry contains the deferred transition, but its body starts afterwards.
		Fake weatherGl = new Fake();
		var weather = new GpuTimestampRing(weatherGl, 512);
		int entry = weather.begin("weather/entry-with-deferred-inclusive", 0);
		int transition = weather.begin("weather/deferred-transition-inclusive", 0);
		int depth = weather.begin("depth/pre-translucent-copy", 0);
		weather.end(depth);
		weather.end(transition);
		int body = weather.begin("gbuffer/weather-body-inclusive", 0);
		weather.end(body);
		body = -1;
		weather.end(body); // WrapMethod finally after the normal RETURN hook.
		weather.end(entry);
		check(weatherGl.stamps == 8, "normal return/finally must not double-stamp body");
		weatherGl.available.addAll(weatherGl.live);
		Set<String> weatherNames = new HashSet<>();
		weather.poll(3, (name, ns) -> weatherNames.add(name));
		check(weatherNames.equals(Set.of("weather/entry-with-deferred-inclusive",
			"weather/deferred-transition-inclusive", "depth/pre-translucent-copy",
			"gbuffer/weather-body-inclusive")) && weather.pending() == 0,
			"transition and body resolve independently inside weather entry");
		weather.close();
		check(weatherGl.live.isEmpty(), "weather capture cleanup");
		Fake terrainGl = new Fake();
		var terrain = new GpuTimestampRing(terrainGl, 512);
		Set<String> submitted = new HashSet<>();
		for (int frame = 0; frame < 256; frame += 4) {
			String label = "gbuffer/terrain/solid-inclusive/" + TerrainDrawExperiment.label(frame) + "|block=" + frame / 32;
			submitted.add(label);
			terrain.end(terrain.begin(label, frame));
		}
		terrainGl.available.addAll(terrainGl.live);
		Set<String> received = new HashSet<>();
		long[] terrainSamples = {0};
		terrain.poll(1024, (name, ns) -> { received.add(name); terrainSamples[0]++; });
		check(received.equals(submitted) && terrainSamples[0] == 64,
			"delayed terrain queries retain original mode/block, not resolution frame");
		check(terrain.dropped() == 0 && terrain.pending() == 0, "factorial capture drains");
		terrain.close();
		check(terrainGl.live.isEmpty(), "terrain capture cleanup");
		System.out.println("GpuTimestampRingTest PASS (nested, delayed, saturation, availability, reuse, destroy)");
	}

	private static void check(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}
}
