package org.embeddedt.embeddium.impl.texture;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

/** Raw setup binds never enter Minecraft's fixed-size texture-unit cache. */
public final class VintageTextureUpload implements AutoCloseable {
    private final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    private final int buffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
    private static final int[] PARAMETERS = {GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_ROW_LENGTH,
            GL11.GL_UNPACK_SKIP_PIXELS, GL11.GL_UNPACK_SKIP_ROWS, GL11.GL_UNPACK_SWAP_BYTES};
    private final int[] values = new int[PARAMETERS.length];

    public VintageTextureUpload() {
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        for (int i = 0; i < PARAMETERS.length; i++) {
            values[i] = GL11.glGetInteger(PARAMETERS[i]);
            GL11.glPixelStorei(PARAMETERS[i], i == 0 ? 4 : 0);
        }
    }

    @Override
    public void close() {
        for (int i = 0; i < PARAMETERS.length; i++) {
            GL11.glPixelStorei(PARAMETERS[i], values[i]);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, buffer);
        GL13.glActiveTexture(active);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding);
    }
}
