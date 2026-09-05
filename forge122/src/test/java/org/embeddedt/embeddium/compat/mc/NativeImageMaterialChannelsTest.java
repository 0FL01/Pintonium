package org.embeddedt.embeddium.compat.mc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** Standalone CPU regression check; no Minecraft bootstrap or GL context required. */
public class NativeImageMaterialChannelsTest {
    public static void main(String[] args) throws Exception {
        // Smoothness=240, dielectric F0=10, no SSS/emission; alpha zero is data.
        int abgr = 0x00000AF0;
        NativeImage image = new NativeImage(1, 1, false);
        image.setPixelRGBA(0, 0, abgr);
        if (image.getRGB(0, 0) != 0x00F00A00 || image.getPixelRGBA(0, 0) != abgr) {
            throw new AssertionError("Material channels swapped during pixel conversion");
        }

        BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);
        source.setRGB(0, 0, 0x00F00A00);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        if (!ImageIO.write(source, "png", png)) throw new AssertionError("PNG writer unavailable");
        NativeImage loaded = NativeImage.read(new ByteArrayInputStream(png.toByteArray()));
        if (loaded.getPixelRGBA(0, 0) != abgr) {
            throw new AssertionError("Transparent material data lost while decoding PNG");
        }
        System.out.println("PASS native material channel conversion and alpha-zero PNG decoding");
    }
}
