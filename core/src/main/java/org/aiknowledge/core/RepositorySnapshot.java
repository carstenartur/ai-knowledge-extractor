package org.aiknowledge.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable deterministic extraction aggregate. */
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

    /** Language-neutral Java, JavaScript, TypeScript, and future source units. */
    public final ArrayList sourceUnits = new ArrayList();

    /** Types, callables, fields, and other named language-neutral symbols. */
    public final ArrayList symbols = new ArrayList();

    /** Typed language-internal and cross-language relations. */
    public final ArrayList relations = new ArrayList();

    /** Client calls and server endpoint contracts. */
    public final ArrayList boundaries = new ArrayList();

    /** Recoverable provider limitations and extraction warnings. */
    public final ArrayList warnings = new ArrayList();
}
