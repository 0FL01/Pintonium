package org.taumc.celeritas.impl.world.cloned;

import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.embeddedt.embeddium.impl.util.position.SectionPos;
import org.taumc.celeritas.impl.render.terrain.HeldItemLight;

public class ChunkRenderContext {
    private final SectionPos sectionCoord;
    private final ClonedChunkSection[] sections;
    private final StructureBoundingBox volume;
    private final HeldItemLight heldItemLight;

    public ChunkRenderContext(SectionPos sectionCoord, ClonedChunkSection[] sections, StructureBoundingBox volume, HeldItemLight heldItemLight) {
        this.sectionCoord = sectionCoord;
        this.sections = sections;
        this.volume = volume;
        this.heldItemLight = heldItemLight;
    }

    public HeldItemLight getHeldItemLight() {
        return this.heldItemLight;
    }

    public ClonedChunkSection[] getSections() {
        return this.sections;
    }

    public SectionPos getOrigin() {
        return this.sectionCoord;
    }

    public StructureBoundingBox getVolume() {
        return this.volume;
    }
}
