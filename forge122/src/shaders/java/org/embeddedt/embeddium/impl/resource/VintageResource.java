package org.embeddedt.embeddium.impl.resource;

import net.minecraft.client.resources.IResource;
import org.embeddedt.embeddium.compat.mc.MCResource;

import java.io.IOException;
import java.io.InputStream;

public class VintageResource implements MCResource {
    private final IResource resource;

    public VintageResource(IResource resource) {
        this.resource = resource;
    }

    @Override
    public InputStream open() throws IOException {
        return this.resource.getInputStream();
    }
}
