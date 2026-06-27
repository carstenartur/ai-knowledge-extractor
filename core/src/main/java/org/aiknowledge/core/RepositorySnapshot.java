package org.aiknowledge.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RepositorySnapshot {
    public final Map index = new LinkedHashMap();
    public final ArrayList modules = new ArrayList();
    public final ArrayList classes = new ArrayList();
    public final ArrayList tests = new ArrayList();
    public final ArrayList docs = new ArrayList();
    public final ArrayList dependencies = new ArrayList();
    public final ArrayList capabilities = new ArrayList();
    public final ArrayList claims = new ArrayList();
    public final ArrayList evidence = new ArrayList();
}
