package net.irisshaders.iris.uniforms;

import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.shaderpack.IdMap;

public class VintageIdMapUniforms implements IdMapUniforms {
    @Override
    public void addIdMapUniforms(FrameUpdateNotifier notifier, UniformHolder uniforms, IdMap idMap, boolean isOldHandLight) {
        // TODO: expose held item IDs and light values once item/entity hooks are ported.
    }
}
