# Mixed Java and TypeScript extraction fixture

This directory is deliberately not included in the root Gradle build. It is a compact repository
shape for documentation and integration tests: `web/src/users.ts` calls an HTTP operation and the
Java architecture source declares the matching Spring endpoint.

Run the extractor from the repository root as described in
[`docs/integrator-quickstart.md`](../../docs/integrator-quickstart.md). The normalized operation is
`GET /api/users/{}`. The example contains no Git-history fixture because commit-based change
coupling is outside the product scope.
