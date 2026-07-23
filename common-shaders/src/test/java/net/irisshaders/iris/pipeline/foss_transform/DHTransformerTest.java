package net.irisshaders.iris.pipeline.foss_transform;

import net.irisshaders.iris.pipeline.transform.Patch;
import net.irisshaders.iris.pipeline.transform.parameter.DHParameters;
import net.irisshaders.iris.pipeline.transform.parameter.Parameters;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DHTransformerTest {
    private static final String LEGACY_VERTEX = """
            #version 120
            void main() {
                gl_Position = ftransform();
                gl_FrontColor = gl_Color;
            }
            """;

    private static final String CORE_VERTEX = """
            #version 330 core
            void main() {
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            }
            """;

    private static final String TERRAIN_FRAGMENT = """
            #version 120
            uniform sampler2D texture;
            varying vec2 texCoord;
            varying vec4 color;

            void main() {
                vec4 albedoTexture = texture2D(texture, texCoord);
                gl_FragData[0] = albedoTexture * color;
            }
            """;

    @Test
    void transformsTerrainInLegacyAndCoreProfiles() throws Exception {
        String legacy = transform(Patch.DH_TERRAIN, LEGACY_VERTEX);
        assertTerrainTransform(legacy);
        assertTrue(legacy.contains("vec4 iris_ftransform"), legacy);
        assertTerrainTransform(transform(Patch.DH_TERRAIN, CORE_VERTEX));
    }

    @Test
    void terrainLightCoordinatesUseDh32SkyThenBlockOrder() throws Exception {
        String shader = transform(Patch.DH_TERRAIN, LEGACY_VERTEX);
        String compact = shader.replaceAll("\\s+", "");
        int skyLight = compact.indexOf("float(lights/16u)");
        int blockLight = compact.indexOf("mod(float(lights),16.0)");

        assertTrue(skyLight >= 0, shader);
        assertTrue(blockLight > skyLight, shader);
    }

    @Test
    void transformsGenericGeometryInLegacyAndCoreProfiles() throws Exception {
        String legacy = transform(Patch.DH_GENERIC, LEGACY_VERTEX);
        assertGenericTransform(legacy);
        assertTrue(legacy.contains("vec4 iris_ftransform"), legacy);
        assertGenericTransform(transform(Patch.DH_GENERIC, CORE_VERTEX));
    }

    @Test
    void terrainFragmentPreservesShaderpackAlbedoSampling() throws Exception {
        String shader = transform(Patch.DH_TERRAIN, ShaderType.FRAGMENT, TERRAIN_FRAGMENT);

        assertTrue(shader.contains("texture"), shader);
        assertTrue(shader.contains("iris_renamed_texture"), shader);
        assertTrue(shader.contains("texCoord"), shader);
        assertFalse(shader.contains("vec4 albedoTexture = vec4 ( 1.0 )"), shader);
    }

    private static String transform(Patch patch, String vertex) throws Exception {
        return transform(patch, ShaderType.VERTEX, vertex);
    }

    private static String transform(Patch patch, ShaderType type, String source) throws Exception {
        EnumMap<ShaderType, String> sources = new EnumMap<>(ShaderType.class);
        sources.put(type, source);

        Method transformInternal = ShaderTransformer.class.getDeclaredMethod(
                "transformInternal", String.class, Map.class, Patch.class, Parameters.class);
        transformInternal.setAccessible(true);

        try {
            @SuppressWarnings("unchecked")
            Map<ShaderType, String> transformed = (Map<ShaderType, String>) transformInternal.invoke(
                    null, "dh_test", sources, patch, new DHParameters(patch, null));
            return transformed.get(type);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static void assertTerrainTransform(String shader) {
        assertTrue(shader.contains("void _vert_init"));
        assertTrue(shader.contains("uvec4 vPosition"));
        assertTrue(shader.contains("uvec4 irisExtra"));
        assertTrue(shader.contains("modelOffset"));
        assertTrue(shader.contains("dhMaterialId"));
    }

    private static void assertGenericTransform(String shader) {
        assertTrue(shader.contains("void _vert_init"));
        assertTrue(shader.contains("vec3 aScale"));
        assertTrue(shader.contains("ivec3 aTranslateChunk"));
        assertTrue(shader.contains("int aMaterial"));
        assertTrue(shader.contains("dhMaterialId"));
    }
}
