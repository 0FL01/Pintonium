package net.irisshaders.iris.pipeline.foss_transform;

import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;
import org.taumc.glsl.shadowed.org.antlr.v4.runtime.tree.ErrorNode;
import org.taumc.glsl.shadowed.org.antlr.v4.runtime.tree.ParseTree;
import org.taumc.glsl.shadowed.org.antlr.v4.runtime.tree.TerminalNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Conservative proof of unobservable draw boundaries, not a shader rewrite or pack-name gate. */
public final class TerrainDrawEligibility {
    private static final Set<String> TYPES = Set.of("void", "bool", "int", "uint", "float",
            "vec2", "vec3", "vec4", "ivec2", "ivec3", "ivec4", "uvec2", "uvec3", "uvec4",
            "bvec2", "bvec3", "bvec4", "mat2", "mat3", "mat4", "mat2x2", "mat2x3", "mat2x4",
            "mat3x2", "mat3x3", "mat3x4", "mat4x2", "mat4x3", "mat4x4",
            "sampler2D", "sampler3D", "samplerCube", "sampler2DShadow", "sampler2DArray",
            "sampler2DArrayShadow", "isampler2D", "usampler2D");
    private static final Set<String> BUILTINS = Set.of("radians", "degrees", "sin", "cos", "tan",
            "asin", "acos", "atan", "sinh", "cosh", "tanh", "pow", "exp", "log", "exp2", "log2",
            "sqrt", "inversesqrt", "abs", "sign", "floor", "trunc", "round", "roundEven", "ceil",
            "fract", "mod", "modf", "min", "max", "clamp", "mix", "step", "smoothstep", "isnan", "isinf",
            "floatBitsToInt", "floatBitsToUint", "intBitsToFloat", "uintBitsToFloat",
            "length", "distance", "dot", "cross", "normalize", "faceforward", "reflect", "refract",
            "matrixCompMult", "outerProduct", "transpose", "determinant", "inverse", "lessThan",
            "lessThanEqual", "greaterThan", "greaterThanEqual", "equal", "notEqual", "any", "all", "not",
            "texture", "textureSize", "textureLod", "textureProj", "textureOffset", "texelFetch",
            "texelFetchOffset", "textureProjOffset", "textureLodOffset", "textureProjLod",
            "textureProjLodOffset", "textureGrad", "textureGradOffset", "textureProjGrad",
            "textureProjGradOffset", "dFdx", "dFdy", "fwidth");
    private static final Set<String> DRAW_INDEPENDENT_GLOBALS = Set.of("gl_Position", "gl_VertexID",
            "gl_InstanceID", "gl_FragCoord", "gl_FrontFacing", "gl_FragDepth", "gl_ClipDistance",
            "gl_PointSize", "gl_PointCoord", "gl_DepthRange");

    private TerrainDrawEligibility() {}

    /** Null means eligible. Unknown syntax, resource types, extensions and calls retain legacy draws. */
    public static String rejectionReason(String source) {
        try {
            var parsed = ShaderParser.parseShader(JcppProcessor.glslPreprocessSource(source, List.of()));
            String pre = ShaderTransformer.getFormattedShader(parsed.pre(), "").trim();
            if (!pre.matches("#version\\s+330\\s+core")) return "version/directives";
            Set<String> types = new HashSet<>(TYPES);
            Set<String> calls = new HashSet<>(BUILTINS);
            collectDeclarations(parsed.full(), types, calls);
            calls.addAll(types);
            return check(parsed.full(), types, calls);
        } catch (RuntimeException e) {
            return "parse/preprocess";
        }
    }

    private static void collectDeclarations(ParseTree tree, Set<String> types, Set<String> calls) {
        if (tree instanceof GLSLParser.Struct_specifierContext struct && struct.IDENTIFIER() != null)
            types.add(struct.IDENTIFIER().getText());
        if (tree instanceof GLSLParser.Function_definitionContext function)
            calls.add(function.function_prototype().IDENTIFIER().getText());
        for (int i = 0; i < tree.getChildCount(); i++) collectDeclarations(tree.getChild(i), types, calls);
    }

    private static String check(ParseTree tree, Set<String> types, Set<String> calls) {
        if (tree instanceof ErrorNode) return "syntax";
        if (tree instanceof GLSLParser.Type_specifier_nonarrayContext type
                && type.struct_specifier() == null && !types.contains(type.getText())) return "type:" + type.getText();
        if (tree instanceof GLSLParser.Postfix_expressionContext call && call.LEFT_PAREN() != null
                && call.postfix_expression() != null && !calls.contains(call.postfix_expression().getText()))
            return "call:" + call.postfix_expression().getText();
        if (tree instanceof TerminalNode node) {
            String text = node.getText();
            if (text.startsWith("gl_") && !DRAW_INDEPENDENT_GLOBALS.contains(text)) return "builtin:" + text;
            switch (node.getSymbol().getType()) {
                case GLSLLexer.BUFFER, GLSLLexer.SHARED, GLSLLexer.COHERENT, GLSLLexer.VOLATILE,
                        GLSLLexer.RESTRICT, GLSLLexer.READONLY, GLSLLexer.WRITEONLY, GLSLLexer.SUBROUTINE:
                    return "storage:" + text;
            }
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            String reason = check(tree.getChild(i), types, calls);
            if (reason != null) return reason;
        }
        return null;
    }
}
