# Regelsuche integration evidence

Regelsuche is the first larger consumer project for the extractor. It exposed two gaps that are now covered by the core scanner:

- seed files may be maintained as JSON as well as YAML, so existing project metadata can be consumed without format conversion;
- generated project evidence is indexed separately from Java classes and Markdown docs.

The generated `evidence.json` artifact is intended for project-specific context that is important for AI-assisted understanding but does not naturally belong in `classes.json`, `tests.json`, or `docs.json`. Current evidence types include:

- `discovery-evidence` for `docs/generated/discovery/**/evidence.json`;
- `benchmark-source` for JMH benchmark source files under `src/jmh/java`;
- `github-workflow` for workflow metadata under `.github/workflows`.

Consumer projects should still keep curated capabilities and claims small. The extractor should provide the bulk of observable evidence from the repository itself.
