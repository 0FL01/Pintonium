package org.embeddedt.embeddium.impl.resource;

import net.minecraft.util.ResourceLocation;
import org.embeddedt.embeddium.compat.mc.MCResourceLocation;

import java.util.Locale;

public class VintageResourceLocation implements MCResourceLocation {
    private final ResourceLocation location;

    public VintageResourceLocation(ResourceLocation location) {
        this.location = location;
    }

    public ResourceLocation unwrap() {
        return this.location;
    }

    public static ResourceLocation unwrap(MCResourceLocation location) {
        if (location instanceof VintageResourceLocation) {
            return ((VintageResourceLocation) location).unwrap();
        }

        return new ResourceLocation(location.getNamespace(), location.getPath());
    }

    @Override
    public String getPath() {
        return this.location.getPath();
    }

    @Override
    public String getNamespace() {
        return this.location.getNamespace();
    }

    @Override
    public String toDebugFileName() {
        return (this.getNamespace() + "_" + this.getPath())
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_");
    }

    @Override
    public String toString() {
        return this.location.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof VintageResourceLocation) {
            return this.location.equals(((VintageResourceLocation) obj).location);
        }
        return this.location.equals(obj);
    }

    @Override
    public int hashCode() {
        return this.location.hashCode();
    }
}
