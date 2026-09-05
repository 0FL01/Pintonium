package net.irisshaders.iris.compat.sodium.impl.shader_overrides;

import org.embeddedt.embeddium.impl.gl.shader.ShaderType;

import java.nio.file.Files;
import java.nio.file.Path;

/** Standalone shader-source regression; does not require a Minecraft/GL bootstrap. */
public class VintageOilShaderTest {
    public static void main(String[] args) throws Exception {
        String vertex = VintageOilShader.patch("""
                #version 330 core
                in vec4 mc_Entity;
                in vec4 a_Color;
                out vec4 glColor;
                void main() { glColor = a_Color; gl_Position = vec4(mc_Entity.xy, 0.0, 1.0); }
                """, ShaderType.VERTEX, true).replaceAll("\\s+", "");
        if (!vertex.contains("mc_Entity.y==257.0?0.8:1.0")
                || !vertex.contains("pintonium_entity.y=mc_Entity.y==257.0?1.0:mc_Entity.y")
                || !vertex.contains("glColor=a_Color")) {
            throw new AssertionError("Oil marker must be decoded without changing vertex AO/color: " + vertex);
        }
        // Complementary ignores glColor.a here. Changing vertex alpha alone leaves oil opaque.
        String source = """
                #version 330 core
                uniform sampler2D tex, specular;
                in vec2 texCoord;
                in vec4 glColor;
                out vec4 color;
                vec4 helper(sampler2D tex, vec2 texCoord) { return texture(tex, texCoord); }
                void main() {
                    vec4 colorP = texture(tex, texCoord);
                    color = colorP * vec4(glColor.rgb, 1.0);
                    color.rgb += texture(specular, texCoord).rgb;
                }
                """;
        String fragment = VintageOilShader.patch(source, ShaderType.FRAGMENT, true).replaceAll("\\s+", "");
        if (!fragment.contains("pintonium_applyOilOpacity(texture(tex,texCoord))")
                || !fragment.contains("vec4(color.rgb,color.a*pintonium_oilOpacity)")
                || !fragment.contains("texture(specular,texCoord).rgb")
                || !fragment.contains("returntexture(tex,texCoord);")) {
            throw new AssertionError("Only main's albedo sample should receive oil opacity: " + fragment);
        }
        if (!source.equals(VintageOilShader.patch(source, ShaderType.FRAGMENT, false))) {
            throw new AssertionError("Do not inject an unmatched varying across geometry/tessellation stages");
        }
        if (args.length == 1) {
            Path output = Path.of("build", "oil-shader-check");
            Files.createDirectories(output);
            try (var files = Files.list(Path.of(args[0]))) {
                for (Path file : files.filter(p -> p.getFileName().toString().contains("_sodium_")).toList()) {
                    String name = file.getFileName().toString();
                    ShaderType type = name.endsWith(".vsh") ? ShaderType.VERTEX : ShaderType.FRAGMENT;
                    Files.writeString(output.resolve(name), VintageOilShader.patch(Files.readString(file), type, true));
                }
            }
        }
        System.out.println("PASS oil alpha sampling, fluid marker decoding and AO/PBR isolation");
    }
}
