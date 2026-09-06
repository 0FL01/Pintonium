package net.irisshaders.iris.pipeline;

import static net.irisshaders.iris.IrisLogging.IRIS_LOGGER;

import java.util.ArrayList;
import java.util.List;

import org.embeddedt.embeddium.impl.gl.shader.ShaderType;

import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

/**
 * Targeted dead-pass elimination for Complementary Unbound r5.9.
 *
 * <p>With TAA/bloom/motion-blur/world-blur off and FXAA detached from TAA state,
 * composite3 is an identity copy, composite4 fills black into a buffer the next
 * pass overwrites unconditionally, and composite6 copies a buffer onto itself
 * while feeding black to a buffer nothing live reads. Dropping these passes is
 * exactly equivalent to shipping the pack without those files: flip state is
 * recomputed at construction for the remaining passes.</p>
 *
 * <p>The filter engages only when every gate below holds; any mismatch (other
 * pack, changed options, edited shaders) disables it silently and the pipeline
 * runs unchanged. Option changes rebuild the pipeline, so gates are always
 * re-evaluated against current values.</p>
 */
public final class ComplementaryDeadPassFilter {
	private ComplementaryDeadPassFilter() {
	}

	/**
	 * Returns the composite sources to build, with provably dead passes nulled out.
	 * The input array is never mutated; when nothing is filtered it is returned as-is.
	 */
	public static ProgramSource[] filterComposite(ProgramSource[] sources, ShaderPack pack) {
		if (sources == null || sources.length < 7 || pack == null
				|| pack.getShaderPackOptions() == null) {
			return sources;
		}

		OptionValues values = pack.getShaderPackOptions().getOptionValues();
		if (values == null || values.getOptionSet() == null
				|| !hasKeys(values, "WORLD_BLUR", "BLOOM_ENABLED", "MOTION_BLUR_EFFECT",
						"TAA_DEFINE", "FXAA_TAA_INTERACTION", "COLORED_LIGHTING")) {
			return sources;
		}

		List<Integer> skipped = new ArrayList<>(3);

		// composite3: WORLD_BLUR=0 reduces main() to texelFetch(colortex0) -> DRAWBUFFERS:0.
		if ("0".equals(values.getStringValueOrDefault("WORLD_BLUR"))
				&& contains(sources[3], "DoWorldBlur")) {
			skipped.add(3);
		}

		// composite4: BLOOM_ENABLED=-1 + MOTION_BLUR_EFFECT=-1 reduces main() to a black
		// fill of colortex3, which composite5 overwrites unconditionally (blend forced off).
		if ("-1".equals(values.getStringValueOrDefault("BLOOM_ENABLED"))
				&& "-1".equals(values.getStringValueOrDefault("MOTION_BLUR_EFFECT"))
				&& contains(sources[4], "BloomTile(")) {
			skipped.add(4);
		}

		// composite6: TAA off reduces main() to a colortex3 self-copy plus black into
		// colortex2. The self-copy is a flip-parity no-op once the pass is gone, and the
		// only live colortex2 reader (FXAA motion skip) compiles out at interaction 0.
		if ("-1".equals(values.getStringValueOrDefault("TAA_DEFINE"))
				&& !values.getBooleanValueOrDefault("TAA")
				&& "0".equals(values.getStringValueOrDefault("FXAA_TAA_INTERACTION"))
				&& contains(sources[6], "DoTAA")) {
			skipped.add(6);
		}

		if (skipped.isEmpty()) {
			return sources;
		}

		ProgramSource[] filtered = sources.clone();
		for (int index : skipped) {
			filtered[index] = null;
		}

		IRIS_LOGGER.info("[Perf] Complementary dead composite passes skipped: {}", skipped);
		return filtered;
	}

	private static boolean hasKeys(OptionValues values, String... keys) {
		for (String key : keys) {
			if (!values.getOptionSet().getStringOptions().containsKey(key)) {
				return false;
			}
		}
		return values.getOptionSet().getBooleanOptions().containsKey("TAA");
	}

	private static boolean contains(ProgramSource source, String marker) {
		if (source == null) {
			return false;
		}
		String fragment = source.getSourceNullable(ShaderType.FRAGMENT);
		return fragment != null && fragment.contains(marker);
	}
}
