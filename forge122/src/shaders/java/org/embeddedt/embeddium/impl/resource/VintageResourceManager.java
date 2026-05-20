package org.embeddedt.embeddium.impl.resource;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import org.embeddedt.embeddium.compat.mc.MCResource;
import org.embeddedt.embeddium.compat.mc.MCResourceLocation;
import org.embeddedt.embeddium.compat.mc.MCResourceManager;
import org.embeddedt.embeddium.compat.mc.MCResourceProvider;

import java.io.IOException;
import java.util.Optional;

public class VintageResourceManager implements MCResourceManager, MCResourceProvider {
    private final IResourceManager resourceManager;

    public VintageResourceManager(IResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @Override
    public Optional<MCResource> getResource(MCResourceLocation location) {
        try {
            IResource resource = this.resourceManager.getResource(VintageResourceLocation.unwrap(location));
            return Optional.of(new VintageResource(resource));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
