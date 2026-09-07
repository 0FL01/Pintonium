package net.irisshaders.iris.pipeline;

public final class DepthCopyExperimentTest {
	public static void main(String[] args) {
		if (DepthCopyExperiment.useBlit(-1, true)) throw new AssertionError("Outside capture");
		int blits = 0, sampledBlits = 0, sampledCopies = 0;
		for (int frame = 0; frame < 8000; frame++) {
			if (DepthCopyExperiment.useBlit(frame, false)) throw new AssertionError("Unavailable/uninitialized copy-image");
			boolean blit = DepthCopyExperiment.useBlit(frame, true);
			if (blit) blits++;
			if (frame % 4 == 0) {
				if (blit) sampledBlits++; else sampledCopies++;
			}
			if (blit != DepthCopyExperiment.useBlit(frame / 4 * 4, true)) throw new AssertionError("Unstable block");
		}
		if (blits != 4000 || sampledBlits != 1000 || sampledCopies != 1000) throw new AssertionError("Biased sampling");
		System.out.println("DepthCopyExperimentTest PASS");
	}
}
