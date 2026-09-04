# Supported release lines

AI Knowledge Extractor can maintain more than one release line at the same time. A release line is
an `X.Y.x` series with its own branch, compatibility contract and release policy. The authoritative
machine-readable list is [`.github/release-lines.json`](../.github/release-lines.json); CI and the
release workflow reject branches or versions that do not match that file.

## Current lines

| Release line | Branch | Status | Artifact contract | Intended changes |
| --- | --- | --- | --- | --- |
| `0.2.x` | `main` | Active | `schema-v2` | Features, correctness fixes, security fixes and documentation |
| `0.1.x` | `maintenance/0.1.x` | Maintenance | `schema-v1` | Critical correctness, security and downstream compatibility fixes only |

A next patch snapshot in the `0.1.x` series is newer than the preceding `0.1.x` release **inside
that maintenance line**. It is not an update or downgrade of `main`, which develops the separate
`0.2.x` line. Pull requests and version numbers must always be interpreted together with their base
branch and artifact contract.

## Support levels

### Active

The active line lives on `main` and is the only line whose releases may be marked as the latest
GitHub release. It receives normal feature development, compatible fixes, security fixes and
current documentation.

Release automation advances the active line only to the next patch in its configured X.Y series.
Starting a new minor or major line is an explicit reviewed support-policy change, not an incidental
side effect of publishing a release.

### Maintenance

A maintenance line lives on `maintenance/X.Y.x`. It receives narrowly scoped fixes needed to keep
existing consumers reliable without importing the active line's new metrics, schemas or behavior.
Its releases are always created with `latest=false`, so a later maintenance patch cannot replace the
active line in GitHub's “Latest” badge.

A maintenance line must remain in the same X.Y series. The policy rejects a next-development
version from a different series.

### End of life

An end-of-life entry documents a line that is no longer supported. It uses
`nextVersionPolicy: "none"`; release automation rejects publication from that branch. Historical
tags and release assets remain available, but no fixes are promised.

## Compatibility policy before 1.0

The project is still below 1.0. Consequently, different minor lines may intentionally have
different output schemas, metrics or analysis semantics. Consumers should pin an exact released
version and consult the release notes before moving between minor lines.

Within one supported X.Y line:

- patch releases must preserve the line's documented artifact contract;
- correctness and security fixes may change erroneous output, but must not silently adopt another
  line's schema or scoring semantics;
- any unavoidable compatibility break requires explicit release notes and normally a new minor
  line rather than a maintenance patch.

The current contracts are identified in `.github/release-lines.json`. For the active 0.2.x line,
see also [`schema-v2-contract.md`](schema-v2-contract.md).

## Backport policy

A change is suitable for a maintenance line when it is one of the following:

- a security fix;
- a data-loss, build-reliability or release-integrity fix;
- a consumer-blocking correctness fix;
- a narrowly required compatibility fix for an existing supported consumer;
- documentation that corrects instructions for that line.

New extractors, metrics, schema fields, scoring changes and broad refactorings belong on the active
line unless they are independently justified and reviewed as a compatibility-preserving backport.
Every backport is built and tested on its maintenance branch; success on `main` is not sufficient.

## Release and pull-request behavior

The release workflow resolves the selected branch through `.github/release-lines.json` and derives:

- whether the branch is allowed to publish;
- which X.Y versions belong to it;
- whether its GitHub release may be marked latest;
- which branch receives the next-development change;
- whether the next version must remain in the same series.

After publication, exactly one draft next-development PR is generated with the target branch in its
title. A separate workflow verifies that its diff contains only version metadata, dispatches CI for
the exact head commit, records the result as a commit status and merges it only after success. This
avoids duplicate owner/bot PRs and makes a maintenance transition visibly different from a change
to `main`.

For environments without direct workflow-dispatch access, a reviewed change to
`.github/release-request.json` can request either a dry run or a real release. The strict request
contains the release and next-patch versions, a unique request ID and the exact previously qualified
commit. The workflow rejects any additional diff. See [`release.md`](release.md) for the schema and
procedure.

## Starting another simultaneously supported line

Starting a new X.Y line is a deliberate repository change. For example, when moving active
development from `0.2.x` to `0.3.x` while retaining `0.2.x`:

1. finish and tag the final intended active-line patch;
2. create `maintenance/0.2.x` from the appropriate supported commit;
3. change the `main` entry in `.github/release-lines.json` to series `0.3` and add a separate
   `maintenance/0.2.x` entry with `releaseLatest: false`;
4. update all `main` development metadata to the first `0.3.x` snapshot in the same reviewed PR;
5. copy or merge the generic release-line tooling and matching policy into the new maintenance
   branch;
6. update the README and this document;
7. run CI on both branches before publishing from either line.

The ordinary release workflow cannot perform this transition. CI validates unique branches and
series, exactly one active/latest line, maintenance branch naming, version-to-branch matching and
end-of-life restrictions.

## Consumer guidance

Use an exact release version in Gradle or Maven. Do not depend on a snapshot or assume that the
numerically largest version from a different minor line is the correct upgrade. Choose the line by
its compatibility contract first, then choose the newest released patch in that line.

Distribution and authentication details are documented in [`publishing.md`](publishing.md).
