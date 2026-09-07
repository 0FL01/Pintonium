package net.irisshaders.iris.pipeline;

/** Bounded A/B schedule aligned to the profiler's four-frame sampling stride. */
public final class DepthCopyExperiment {
	private DepthCopyExperiment() { }

	public static boolean useBlit(int captureFrame, boolean copyImageReady) {
		return captureFrame >= 0 && copyImageReady && (captureFrame / 4 & 1) != 0;
	}
}
