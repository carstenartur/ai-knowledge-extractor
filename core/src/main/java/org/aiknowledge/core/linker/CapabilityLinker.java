package org.aiknowledge.core.linker;

import org.aiknowledge.core.CapabilityEvidence;
import org.aiknowledge.core.RepositorySnapshot;

public final class CapabilityLinker {
    public void link(RepositorySnapshot snapshot) {
        CapabilityEvidence.addCapabilities(snapshot);
    }
}
