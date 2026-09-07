package net.irisshaders.iris.pipeline;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Invocation-local dependencies; logical aliases share the physical texture's version. */
final class CompositeTextureVersions {
	private final Map<Integer, Version> versions = new HashMap<>();

	record Texture(int logicalIndex, int physicalId) {
		static Texture read(int index, int main, int alt, boolean readsAlt) {
			return new Texture(index, readsAlt ? alt : main);
		}

		static Texture write(int index, int main, int alt, boolean readsAlt) {
			return new Texture(index, readsAlt ? main : alt);
		}
	}

	record PassResources(List<Texture> mipmapReads, List<Texture> rasterWrites, boolean unknownWrites) {
		PassResources {
			mipmapReads = List.copyOf(mipmapReads);
			rasterWrites = List.copyOf(rasterWrites);
		}
	}

	void beginInvocation() {
		// External geometry, clears, copies and storage reallocations are not tracked here.
		versions.clear();
	}

	boolean needsMipmaps(Texture texture) {
		Version version = versions.computeIfAbsent(texture.physicalId(), ignored -> new Version());
		return version.content != version.mipmap;
	}

	void mipmapsGenerated(Texture texture) {
		Version version = versions.computeIfAbsent(texture.physicalId(), ignored -> new Version());
		version.mipmap = version.content;
	}

	void written(Texture texture) {
		versions.computeIfAbsent(texture.physicalId(), ignored -> new Version()).content++;
	}

	void unknownWrite() {
		for (Version version : versions.values()) {
			version.content++;
		}
	}

	void rasterWritten(PassResources resources) {
		if (resources.unknownWrites()) {
			unknownWrite();
		} else {
			resources.rasterWrites().forEach(this::written);
		}
	}

	private static final class Version {
		long content;
		long mipmap = -1;
	}
}
