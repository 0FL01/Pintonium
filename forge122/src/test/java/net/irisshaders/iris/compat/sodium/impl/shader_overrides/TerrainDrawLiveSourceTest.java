package net.irisshaders.iris.compat.sodium.impl.shader_overrides;

import net.irisshaders.iris.pipeline.foss_transform.TerrainDrawEligibility;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only audit of captured simple VS/FS through the actual final source patch and eligibility gate. */
public class TerrainDrawLiveSourceTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Supply captured terrain .vsh/.fsh paths");
        for (String arg : args) {
            ShaderType type = arg.endsWith(".vsh") ? ShaderType.VERTEX : ShaderType.FRAGMENT;
            String source = VintageOilShader.patch(Files.readString(Path.of(arg)), type, true);
            String reason = TerrainDrawEligibility.rejectionReason(source);
            if (reason != null) throw new AssertionError(arg + ": " + reason);
            System.out.println("PASS final terrain source eligibility: " + arg);
        }
    }
}
