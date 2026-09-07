package net.irisshaders.iris.pipeline.foss_transform;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TerrainDrawEligibilityTest {
    @Test
    void acceptsDrawIndependentShadersWithoutChangingThem() {
        assertNull(TerrainDrawEligibility.rejectionReason("""
                #version 330 core
                uniform sampler2D tex;
                in vec2 uv;
                layout(location=0) out vec4 color;
                struct Material { vec4 color; };
                vec4 shade(vec4 c) { return c * 0.5; }
                void main() {
                    color = shade(texture(tex, uv));
                    if (color.a <= 0.1) discard;
                    gl_FragDepth = gl_FragCoord.z;
                }
                """));
        assertNull(TerrainDrawEligibility.rejectionReason("""
                #version 330 core
                // gl_DrawID and imageStore in comments are not shader effects.
                void main() { gl_Position = vec4(float(gl_VertexID)); }
                """));
    }

    @Test
    void rejectsObservableBoundariesAndUnknownEffects() {
        for (String id : new String[]{"gl_PrimitiveID", "gl_DrawID", "gl_DrawIDARB", "gl_BaseVertex",
                "gl_BaseVertexARB", "gl_WarpIDNV", "gl_SubGroupInvocationARB"}) {
            assertNotNull(TerrainDrawEligibility.rejectionReason("#version 330 core\n"
                    + "#define PACK_ID " + id + "\nvoid main(){ gl_Position=vec4(float(PACK_ID)); }"), id);
        }
        for (String body : new String[]{
                "uniform image2D img; void main(){ imageStore(img, ivec2(0), vec4(0)); }",
                "buffer Data { float x; }; void main(){ x=1.0; }",
                "uniform atomic_uint a; void main(){ atomicCounterIncrement(a); }",
                "void main(){ gl_Position=vec4(float(clockARB())); }",
                "vec4 unknown(); void main(){ gl_Position=unknown(); }"}) {
            assertNotNull(TerrainDrawEligibility.rejectionReason("#version 330 core\n" + body), body);
        }
        assertNotNull(TerrainDrawEligibility.rejectionReason("#version 430 core\nvoid main(){}"));
        assertNotNull(TerrainDrawEligibility.rejectionReason("#version 330 core\n#extension GL_ARB_shader_draw_parameters : enable\nvoid main(){}"));
        assertNotNull(TerrainDrawEligibility.rejectionReason("not valid GLSL"));
    }

}
