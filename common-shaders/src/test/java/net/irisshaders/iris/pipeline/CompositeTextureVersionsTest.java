package net.irisshaders.iris.pipeline;

import java.util.List;

/** Standalone dependency checks; no Minecraft or GL context required. */
public class CompositeTextureVersionsTest {
	public static void main(String[] args) {
		var versions = new CompositeTextureVersions();
		var main = CompositeTextureVersions.Texture.read(0, 10, 11, false);
		var alt = CompositeTextureVersions.Texture.read(0, 10, 11, true);
		var writeAlt = CompositeTextureVersions.Texture.write(0, 10, 11, false);
		var writeMain = CompositeTextureVersions.Texture.write(0, 10, 11, true);
		if (!writeAlt.equals(alt) || !writeMain.equals(main)) {
			throw new AssertionError("Raster writes must select the side opposite reads");
		}

		versions.beginInvocation();
		check(versions.needsMipmaps(main), "First request generates");
		check(versions.needsMipmaps(main), "Checking a request must not mark generation complete");
		versions.mipmapsGenerated(main);
		check(!versions.needsMipmaps(main), "Repeated request skips");
		check(versions.needsMipmaps(alt), "Main generation must not validate alt");
		versions.mipmapsGenerated(alt);
		versions.rasterWritten(new CompositeTextureVersions.PassResources(List.of(main), List.of(writeAlt), false));
		check(!versions.needsMipmaps(main), "Alt writes must preserve main mipmaps");
		check(versions.needsMipmaps(alt), "Raster output write requires regeneration");
		versions.mipmapsGenerated(alt);
		check(!versions.needsMipmaps(alt), "Regeneration validates the new content version");

		var alias = new CompositeTextureVersions.Texture(7, 10);
		check(!versions.needsMipmaps(alias), "Physical aliases share generated mipmaps");
		versions.written(alias);
		check(versions.needsMipmaps(main), "Writing a logical alias invalidates the physical texture");
		check(!versions.needsMipmaps(alt), "Main alias write must preserve alt");
		versions.mipmapsGenerated(main);

		versions.unknownWrite();
		check(versions.needsMipmaps(main) && versions.needsMipmaps(alt), "Compute/unknown write invalidates all");
		versions.mipmapsGenerated(main);
		versions.mipmapsGenerated(alt);
		versions.rasterWritten(new CompositeTextureVersions.PassResources(List.of(), List.of(writeAlt), true));
		check(versions.needsMipmaps(main) && versions.needsMipmaps(alt), "Unknown raster side effects invalidate all");
		check(versions.needsMipmaps(new CompositeTextureVersions.Texture(8, 80)), "Untracked resources start untrusted");
		versions.mipmapsGenerated(main);
		versions.mipmapsGenerated(alt);

		versions.beginInvocation();
		check(versions.needsMipmaps(main) && versions.needsMipmaps(alt), "New invocation distrusts external writes/resize");
		System.out.println("PASS composite texture versions: repeated requests, writes, main/alt, aliases, unknown effects, invocation reset, output sides");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
