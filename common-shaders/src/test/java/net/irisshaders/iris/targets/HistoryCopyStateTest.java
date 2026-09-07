package net.irisshaders.iris.targets;

import java.util.Arrays;
import java.util.Random;

/** Standalone CPU model: javac this file and HistoryCopyState.java, then run main. */
public final class HistoryCopyStateTest {
	public static void main(String[] args) {
		testLazyOwnership();
		HistoryCopyState state = new HistoryCopyState();
		check(state.needsCopy(), "new textures have no equality proof");
		state.copied();
		for (int frame = 0; frame < 100; frame++) {
			check(!state.needsCopy(), "unchanged history must really eliminate copies");
		}
		state.mayWrite();
		check(state.needsCopy(), "partial/blended write invalidates equality");
		state.copied();
		state.resized();
		check(state.needsCopy(), "resize invalidates storage contents");
		state.copied();
		state.untrackedAccess();
		for (int frame = 0; frame < 100; frame++) {
			check(state.needsCopy(), "unknown image/compute/exported writes always fall back");
			state.copied();
		}
		state.resized();
		state.copied();
		check(state.needsCopy(), "resize must not revoke unknown-access fallback");

		// Compare BOTH sides against an unconditional alt->main implementation.
		Random random = new Random(12345);
		state = new HistoryCopyState();
		int[][] reference = new int[2][16];
		int[][] optimized = new int[2][16];
		int skipped = 0;
		for (int frame = 0; frame < 10000; frame++) {
			if (frame == 9000) state.untrackedAccess();
			if (frame % 97 == 0) {
				int size = 1 + random.nextInt(64);
				reference = new int[2][size];
				optimized = new int[2][size];
				state.resized();
			}
			if (random.nextBoolean()) {
				int side = random.nextInt(2);
				int pixel = random.nextInt(reference[side].length);
				int value = random.nextInt();
				// Last 1000 frames model writes through an image or cached external handle:
				// no bind notification, including after storage has been resized.
				if (frame < 9000) state.mayWrite();
				// Arbitrary one-pixel writes also represent blending and masked clears.
				reference[side][pixel] = value;
				optimized[side][pixel] = value;
			}
			check(Arrays.deepEquals(reference, optimized), "reads of either side before final");
			System.arraycopy(reference[1], 0, reference[0], 0, reference[1].length);
			if (state.needsCopy()) {
				System.arraycopy(optimized[1], 0, optimized[0], 0, optimized[1].length);
				state.copied();
			} else {
				skipped++;
			}
			check(Arrays.deepEquals(reference, optimized), "both histories after final");
		}
		check(skipped > 0, "eligibility is not an always-disabled gate");
		// A swap fails the contract whenever the previous main differs from latest alt.
		check(!Arrays.equals(new int[]{2, 1}, new int[]{2, 2}), "swap is not copy");
		System.out.println("HistoryCopyStateTest passed; redundant copies skipped: " + skipped);
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static void testLazyOwnership() {
		Pair pair = new Pair();
		pair.write(1, 0, 42);
		for (int frame = 0; frame < 100; frame++) {
			pair.finish();
			pair.state.prepareMainSampling();
			check(pair.state.canSampleMainFromAlt(), "level-zero-only main sampler really binds latest alt");
			pair.checkBoth();
		}
		check(pair.copies == 0, "read-only history must not merely move a copy to each next frame");
		check(pair.state.isMainAliasedToAlt(), "both logical base levels have the latest owner");
		pair.write(1, 1, 99);
		check(pair.copies == 1, "partial alt write needs old main first");
		pair.checkBoth();
		pair.finish();
		pair.write(0, 2, 11);
		pair.checkBoth();
		check(pair.copies == 2, "partial main write must start from latest base level");

		pair.finish();
		pair.state.mipmapsRequested();
		check(!pair.state.isMainAliasedToAlt(), "mipmap request splits ownership before modifying either chain");
		pair.mainMip = 7;
		pair.write(1, 0, 81);
		pair.finish();
		pair.state.mipmapsRequested();
		pair.altMip = 73;
		pair.write(1, 0, 82);
		pair.finish();
		pair.state.prepareMainSampling();
		check(!pair.state.canSampleMainFromAlt(), "main sampler cannot alias a texture with distinct stale mipmaps");
		check(pair.mainMip == 7 && pair.altMip == 73, "main fallback must preserve its own stale mip chain");
		pair.checkBoth();

		Random random = new Random(98765);
		pair = new Pair();
		for (int frame = 0; frame < 10000; frame++) {
			if (frame == 5000) pair.state.mipmapsRequested();
			if (frame == 9000) pair.state.untrackedAccess();
			if (frame % 101 == 0) {
				int copies = pair.copies;
				pair.state.resized();
				pair.reference = new int[2][8];
				pair.physical = new int[2][8];
				check(pair.copies == copies, "resize drops dead pending storage without copying");
			}
			for (int op = 0; op < 5; op++) {
				switch (random.nextInt(4)) {
					case 0 -> pair.write(random.nextInt(2), random.nextInt(8), random.nextInt());
					case 1 -> {
						pair.state.prepareMainSampling();
						int owner = pair.state.canSampleMainFromAlt() ? 1 : 0;
						check(Arrays.equals(pair.reference[0], pair.physical[owner]), "main sampler resolves physical owner");
						if (frame >= 5000) check(owner == 0, "resize does not erase distinct mip-chain history");
					}
					case 2 -> check(Arrays.equals(pair.reference[1], pair.physical[1]), "alt read needs no materialization");
					case 3 -> { } // no access
				}
				pair.checkBoth();
			}
			pair.finish();
			pair.checkBoth();
			if (frame >= 9000) check(!pair.state.isMainAliasedToAlt(), "unknown access forces eager final copy");
		}
		System.out.println("Lazy ownership differential test passed (10000 frames, both sides and stale mip chains)");
	}

	private static final class Pair {
		final HistoryCopyState state = new HistoryCopyState();
		int[][] reference = new int[2][8];
		int[][] physical = new int[2][8];
		int mainMip = -1; // no allocated higher levels initially
		int altMip = -1;
		int copies;

		void write(int side, int pixel, int value) {
			state.mayWrite(); // precedes draw/use; no coverage assumption
			check(!state.canSampleMainFromAlt(), "write+read binds distinct physical sides after COW");
			reference[side][pixel] = value;
			physical[side][pixel] = value;
		}

		void finish() {
			System.arraycopy(reference[1], 0, reference[0], 0, 8);
			state.publish(() -> {
				System.arraycopy(physical[1], 0, physical[0], 0, 8);
				copies++;
			});
		}

		void checkBoth() {
			int owner = state.isMainAliasedToAlt() ? 1 : 0;
			check(Arrays.equals(reference[0], physical[owner]), "logical main ownership");
			check(Arrays.equals(reference[1], physical[1]), "logical alt ownership");
		}
	}
}
