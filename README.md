# AI Knowledge Extractor

Deterministic build-integrated knowledge extraction for AI-assisted code understanding.

This project provides a core Java library plus Gradle and Maven plugins. It generates stable JSON files under `build/ai-knowledge/` so tools can inspect modules, classes, tests, docs, dependencies, capabilities, claims, complexity metrics, optimization hints and benchmark profiles.

Initial scope:

- deterministic repository knowledge extraction
- Gradle task `generateAiKnowledgeIndex`
- Maven goal `ai-knowledge:generate`
- complexity analysis via `analyzeAiComplexity` / `ai-knowledge:analyze`
- knowledge-smell optimization via `optimizeAiKnowledge` / `ai-knowledge:optimize`
- deterministic context-profile benchmark via `benchmarkAiKnowledge` / `ai-knowledge:benchmark`

The current benchmark layer is deterministic and does not call external model APIs.

License: Apache-2.0
