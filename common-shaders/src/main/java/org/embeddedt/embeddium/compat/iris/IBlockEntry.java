package org.embeddedt.embeddium.compat.iris;

import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public interface IBlockEntry {
    Iterable<IBlockEntry> expandEntries();
    NamespacedId id();
    boolean isTag();
    Map<String, String> propertyPredicates();

    default Set<Integer> metadataIds() {
        return Collections.emptySet();
    }
}
