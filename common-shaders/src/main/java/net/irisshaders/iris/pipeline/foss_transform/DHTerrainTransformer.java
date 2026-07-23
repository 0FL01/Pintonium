package net.irisshaders.iris.pipeline.foss_transform;

import net.irisshaders.iris.pipeline.transform.parameter.DHParameters;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.taumc.glsl.Transformer;

import static net.irisshaders.iris.pipeline.foss_transform.ShaderTransformer.addIfNotExists;
import static net.irisshaders.iris.pipeline.foss_transform.ShaderTransformer.applyIntelHd4000Workaround;
import static net.irisshaders.iris.pipeline.foss_transform.ShaderTransformer.replaceGlMultiTexCoordBounded;

public final class DHTerrainTransformer {
    private DHTerrainTransformer() {
    }

    public static void patchDHTerrain(Transformer transformer, DHParameters parameters, boolean core) {
        ShaderTransformer.commonPatch(transformer, parameters, core);

        transformer.replaceExpression("gl_TextureMatrix[0]", "mat4(1.0)");
        transformer.replaceExpression("gl_TextureMatrix[1]", "mat4(1.0)");
        transformer.rename("gl_ProjectionMatrix", "iris_ProjectionMatrix");

        if (parameters.type == ShaderType.VERTEX) {
            transformer.rename("gl_MultiTexCoord2", "gl_MultiTexCoord1");
            transformer.replaceExpression("gl_MultiTexCoord0", "vec4(0.0, 0.0, 0.0, 1.0)");
            transformer.replaceExpression("gl_MultiTexCoord1", "vec4(_vert_tex_light_coord, 0.0, 1.0)");
            replaceGlMultiTexCoordBounded(transformer, 4, 7);
        }

        transformer.rename("gl_Color", "_vert_color");
        if (parameters.type == ShaderType.VERTEX) {
            transformer.rename("gl_Normal", "_vert_normal");
        }

        transformer.replaceExpression("gl_NormalMatrix", "iris_NormalMatrix");
        transformer.injectVariable("uniform mat3 iris_NormalMatrix;");
        transformer.injectVariable("uniform mat4 iris_ModelViewMatrixInverse;");
        transformer.injectVariable("uniform mat4 iris_ProjectionMatrixInverse;");

        transformer.rename("gl_ModelViewMatrix", "iris_ModelViewMatrix");
        transformer.rename("gl_ModelViewMatrixInverse", "iris_ModelViewMatrixInverse");
        transformer.rename("gl_ProjectionMatrixInverse", "iris_ProjectionMatrixInverse");

        if (parameters.type == ShaderType.VERTEX) {
            if (transformer.containsCall("ftransform")) {
                transformer.injectFunction("vec4 ftransform() { return gl_ModelViewProjectionMatrix * gl_Vertex; }");
            }

            transformer.injectVariable("uniform mat4 iris_ProjectionMatrix;");
            transformer.injectVariable("uniform mat4 iris_ModelViewMatrix;");
            transformer.injectFunction("vec4 getVertexPosition() { return vec4(modelOffset + _vert_position, 1.0); }");
            transformer.replaceExpression("gl_Vertex", "getVertexPosition()");
            injectVertInit(transformer);
        } else {
            transformer.injectVariable("uniform mat4 iris_ModelViewMatrix;");
            transformer.injectVariable("uniform mat4 iris_ProjectionMatrix;");
        }

        transformer.replaceExpression("gl_ModelViewProjectionMatrix", "(iris_ProjectionMatrix * iris_ModelViewMatrix)");
        applyIntelHd4000Workaround(transformer);
    }

    private static void injectVertInit(Transformer transformer) {
        transformer.injectVariable("vec3 _vert_position;");
        transformer.injectVariable("vec2 _vert_tex_light_coord;");
        transformer.injectVariable("int dhMaterialId;");
        transformer.injectVariable("vec4 _vert_color;");
        transformer.injectVariable("vec3 _vert_normal;");
        transformer.injectVariable("uniform float mircoOffset;");
        transformer.injectVariable("uniform vec3 modelOffset;");
        transformer.injectVariable("const vec3 irisNormals[6] = vec3[](vec3(0,-1,0), vec3(0,1,0), vec3(0,0,-1), vec3(0,0,1), vec3(-1,0,0), vec3(1,0,0));");

        transformer.injectFunction("void _vert_init() {" +
                "uint meta = vPosition.a;" +
                "uint mirco = (meta & 0xFF00u) >> 8u;" +
                "float mx = (mirco & 1u) != 0u ? mircoOffset : 0.0;" +
                "mx = (mirco & 2u) != 0u ? -mx : mx;" +
                "float my = (mirco & 4u) != 0u ? mircoOffset : 0.0;" +
                "my = (mirco & 8u) != 0u ? -my : my;" +
                "float mz = (mirco & 16u) != 0u ? mircoOffset : 0.0;" +
                "mz = (mirco & 32u) != 0u ? -mz : mz;" +
                "uint lights = meta & 0xFFu;" +
                "_vert_position = vec3(vPosition.xyz) + vec3(mx, 0.0, mz);" +
                "_vert_normal = irisNormals[irisExtra.y];" +
                "dhMaterialId = int(irisExtra.x);" +
                "_vert_tex_light_coord = vec2((float(lights / 16u) + 0.5) / 16.0, (mod(float(lights), 16.0) + 0.5) / 16.0);" +
                "_vert_color = iris_color;" +
                "}");

        addIfNotExists(transformer, "vPosition", "layout(location = 0) in uvec4 vPosition;");
        addIfNotExists(transformer, "iris_color", "layout(location = 1) in vec4 iris_color;");
        addIfNotExists(transformer, "irisExtra", "layout(location = 2) in uvec4 irisExtra;");
        transformer.prependMain("_vert_init();");
    }
}
