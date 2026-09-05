package net.irisshaders.iris.compat.sodium.impl.shader_overrides;

import net.irisshaders.iris.pipeline.foss_transform.ShaderTransformer;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.taumc.celeritas.impl.render.terrain.compile.OilRendering;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;
import org.taumc.glsl.shadowed.org.antlr.v4.runtime.tree.ParseTree;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** World-terrain alpha only: neither vertex AO nor labPBR alpha is opacity. */
public final class VintageOilShader {
    private VintageOilShader() {
    }

    public static String patch(String source, ShaderType type, boolean simpleStages) {
        if (type != ShaderType.VERTEX && (type != ShaderType.FRAGMENT || !simpleStages)) {
            return source;
        }
        var parsed = ShaderParser.parseShader(JcppProcessor.glslPreprocessSource(source, List.of()));
        var shader = new Transformer(parsed.full());
        if (type == ShaderType.VERTEX) {
            String entityType = switch (shader.findType("mc_Entity")) {
                case 0, GLSLLexer.VEC2 -> "vec2";
                case GLSLLexer.VEC3 -> "vec3";
                case GLSLLexer.VEC4 -> "vec4";
                default -> null;
            };
            if (entityType != null) {
                shader.removeVariable("mc_Entity");
                shader.rename("mc_Entity", "pintonium_entity");
                shader.injectVariable(entityType + " pintonium_entity;");
                shader.injectVariable("in " + entityType + " mc_Entity;");
                shader.prependMain("pintonium_entity.y = mc_Entity.y == "
                        + OilRendering.RENDER_TYPE + ".0 ? 1.0 : mc_Entity.y;");
                shader.prependMain("pintonium_entity = mc_Entity;");
            }
            if (simpleStages) {
                shader.injectVariable("flat out float pintonium_oilOpacity;");
                shader.prependMain("pintonium_oilOpacity = " + (entityType == null ? "1.0" :
                        "mc_Entity.y == " + OilRendering.RENDER_TYPE + ".0 ? " + OilRendering.OPACITY + " : 1.0") + ";");
            }
        } else {
            shader.injectVariable("flat in float pintonium_oilOpacity;");
            Set<GLSLParser.Postfix_expressionContext> samples = new LinkedHashSet<>();
            // Only direct albedo reads in main: do not alter generic sampler helpers,
            // which may also be called for normals, specular or depth textures.
            collectMainSamples(parsed.full(), samples, false);
            if (!samples.isEmpty()) {
                shader.injectFunction("vec4 pintonium_applyOilOpacity(vec4 color) { return vec4(color.rgb, color.a * pintonium_oilOpacity); }");
                shader.mutateTree(tree -> {
                    for (var sample : samples) {
                        var replacement = ShaderParser.parseSnippet("pintonium_applyOilOpacity(" + sample.getText() + ")", GLSLParser::postfix_expression);
                        var parent = sample.getParent();
                        replacement.setParent(parent);
                        parent.children.set(parent.children.indexOf(sample), replacement);
                    }
                });
            }
        }
        return ShaderTransformer.getFormattedShader(parsed.full(),
                ShaderTransformer.getFormattedShader(parsed.pre(), ""));
    }

    private static void collectMainSamples(ParseTree tree, Set<GLSLParser.Postfix_expressionContext> samples, boolean inMain) {
        if (tree instanceof GLSLParser.Function_definitionContext function) {
            inMain = function.function_prototype().getText().startsWith("voidmain(");
        }
        if (inMain && tree instanceof GLSLParser.Postfix_expressionContext call
                && call.LEFT_PAREN() != null && call.postfix_expression() != null) {
            String function = call.postfix_expression().getText();
            var arguments = call.function_call_parameters();
            if (Set.of("texture", "textureLod", "textureGrad", "textureProj", "texelFetch", "textureAF").contains(function)
                    && arguments != null && !arguments.assignment_expression().isEmpty()
                    && Set.of("tex", "gtexture", "gcolor", "iris_renamed_texture")
                    .contains(arguments.assignment_expression(0).getText())) {
                samples.add(call);
            }
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collectMainSamples(tree.getChild(i), samples, inMain);
        }
    }
}
