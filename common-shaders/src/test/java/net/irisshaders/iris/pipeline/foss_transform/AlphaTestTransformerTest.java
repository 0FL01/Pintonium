package net.irisshaders.iris.pipeline.foss_transform;

import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTests;
import net.irisshaders.iris.gl.state.ShaderAttributeInputs;
import net.irisshaders.iris.pipeline.transform.Patch;
import net.irisshaders.iris.pipeline.transform.parameter.Parameters;
import net.irisshaders.iris.pipeline.transform.parameter.SodiumParameters;
import net.irisshaders.iris.pipeline.transform.parameter.VanillaParameters;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlphaTestTransformerTest {
    private static final String FRAGMENT = """
            #version %s
            void main() {
                gl_FragData[0] = vec4(1.0);
            }
            """;

    @Test
    void injectsAlphaTestForLegacyAndCoreProfiles() throws Exception {
        assertAlphaTest(transformVanilla(FRAGMENT.formatted("130")));
        assertAlphaTest(transformVanilla(FRAGMENT.formatted("330 core")));
    }

    @Test
    void injectsCutoutForLuminaStyleSodiumTerrain() throws Exception {
        assertCutoutAlphaTest(transformSodium(FRAGMENT.formatted("130"), AlphaTests.ONE_TENTH_ALPHA));
    }

    @Test
    void keepsLuminaStyleSolidAndCutoutProgramsIndependent() throws Exception {
        String fragment = FRAGMENT.formatted("130");

        assertNoAlphaTest(transformSodium(fragment, AlphaTest.ALWAYS));
        assertCutoutAlphaTest(transformSodium(fragment, AlphaTests.ONE_TENTH_ALPHA));

        assertCutoutAlphaTest(transformSodium(fragment, AlphaTests.ONE_TENTH_ALPHA));
        assertNoAlphaTest(transformSodium(fragment, AlphaTest.ALWAYS));
    }

    @Test
    void usesSourceAlphaForAnisotropicallyFilteredCutouts() throws Exception {
        String fragment = """
                #version 130
                uniform sampler2D tex;
                in vec2 texCoord;
                vec4 textureAF(sampler2D sampler, vec2 uv) {
                    vec4 color = texture2D(sampler, uv);
                    color.a = sqrt(color.a);
                    return color;
                }
                void main() {
                    gl_FragData[0] = textureAF(tex, texCoord);
                }
                """;

        String transformed = transformSodium(fragment, AlphaTests.ONE_TENTH_ALPHA);
        String compact = transformed.replaceAll("\\s+", "");

        assertTrue(compact.contains("texture(tex,texCoord).a>0.1"), transformed);
        assertFalse(compact.contains("iris_FragData0.a>0.1"), transformed);
    }

    private static String transformVanilla(String fragment) throws Exception {
        Parameters parameters = new VanillaParameters(
                Patch.VANILLA,
                null,
                AlphaTests.ONE_TENTH_ALPHA,
                false,
                false,
                new ShaderAttributeInputs(false, false, false, false, false),
                false,
                false);

        return transform(fragment, parameters);
    }

    private static String transformSodium(String fragment, AlphaTest alphaTest) throws Exception {
        Parameters parameters = new SodiumParameters(
                Patch.SODIUM,
                null,
                alphaTest,
                new ShaderAttributeInputs(true, true, false, true, true),
                null);

        return transform(fragment, parameters);
    }

    private static String transform(String fragment, Parameters parameters) throws Exception {
        EnumMap<ShaderType, String> sources = new EnumMap<>(ShaderType.class);
        sources.put(ShaderType.FRAGMENT, fragment);

        Method transformInternal = ShaderTransformer.class.getDeclaredMethod(
                "transformInternal", String.class, Map.class, Patch.class, Parameters.class);
        transformInternal.setAccessible(true);

        try {
            @SuppressWarnings("unchecked")
            Map<ShaderType, String> transformed = (Map<ShaderType, String>) transformInternal.invoke(
                    null, "alpha_test", sources, parameters.patch, parameters);
            return transformed.get(ShaderType.FRAGMENT);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static void assertAlphaTest(String shader) {
        assertTrue(shader.contains("uniform float iris_currentAlphaTest"), shader);
        assertTrue(shader.contains("discard"), shader);
        assertTrue(shader.contains("iris_FragData0"), shader);
    }

    private static void assertCutoutAlphaTest(String shader) {
        assertAlphaTest(shader);
        assertTrue(shader.replaceAll("\\s+", "").contains("iris_FragData0.a>0.1"), shader);
    }

    private static void assertNoAlphaTest(String shader) {
        assertFalse(shader.contains("discard"), shader);
        assertFalse(shader.contains("iris_currentAlphaTest"), shader);
    }
}
