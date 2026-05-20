package net.irisshaders.iris.shaderpack.materialmap;

import org.embeddedt.embeddium.compat.iris.IBlockEntry;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class BlockEntry implements IBlockEntry {
    private final NamespacedId id;
    private final Set<Integer> metadataIds;
    private final Map<String, String> propertyPredicates;

    public BlockEntry(NamespacedId id, Set<Integer> metadataIds) {
        this(id, metadataIds, Collections.emptyMap());
    }

    public BlockEntry(NamespacedId id, Set<Integer> metadataIds, Map<String, String> propertyPredicates) {
        this.id = id;
        this.metadataIds = metadataIds;
        this.propertyPredicates = propertyPredicates;
    }

    @Override
    public Iterable<IBlockEntry> expandEntries() {
        return Collections.singletonList(this);
    }

    @Override
    public NamespacedId id() {
        return this.id;
    }

    @Override
    public Set<Integer> metadataIds() {
        return this.metadataIds;
    }

    @Override
    public boolean isTag() {
        return false;
    }

    @Override
    public Map<String, String> propertyPredicates() {
        return this.propertyPredicates;
    }
}
