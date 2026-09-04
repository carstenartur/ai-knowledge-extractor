# Release process

AI Knowledge Extractor supports multiple release lines at the same time. The authoritative mapping
between an X.Y series, its branch and its support status is
[`.github/release-lines.json`](../.github/release-lines.json). Read
[`version-support.md`](version-support.md) before preparing or publishing a release.

## Release-line invariants

The policy and CI enforce the following rules:

- exactly one line has status `active`;
- the active line uses `main` and is the only line published as GitHub “Latest”;
- maintenance lines use `maintenance/X.Y.x`, stay within that X.Y series and publish with
  `latest=false`;
- end-of-life lines cannot publish;
- a real release from an unconfigured branch is rejected;
- an arbitrary branch may be used only for `dry_run=true` and can never become latest;
- automated post-release development versions remain in the same X.Y series.

Moving `main` to a new minor or major line is an ordinary reviewed policy, metadata and
documentation change. It is not performed by the release workflow. Retaining the previous series is
an explicit support decision that requires a maintenance branch and a separate policy entry.

## Before a release

1. Select the supported source branch in the GitHub Actions **Use workflow from** selector.
2. Ensure CI is green on that exact branch.
3. Confirm `.github/release-lines.json` maps the branch to the intended X.Y series and support
   status.
4. Confirm `release.properties` contains the release represented by the branch's snapshot metadata.
5. Confirm all versioned metadata agrees:
   - `gradle.properties`: `projectVersion=X.Y.Z-SNAPSHOT`
   - `CITATION.cff`: `version: "X.Y.Z-SNAPSHOT"`
   - `.zenodo.json`: `"version": "X.Y.Z-SNAPSHOT"`
   - Maven plugin descriptor: `<version>X.Y.Z-SNAPSHOT</version>`
   - Maven site: `<revision>X.Y.Z-SNAPSHOT</revision>`
   - Maven consumer examples: `<aiKnowledge.version>X.Y.Z-SNAPSHOT</aiKnowledge.version>`
6. Review user-facing documentation and release notes for that line.
7. Run the **Release** workflow with `dry_run=true` on the selected branch.
8. Run the same workflow with `dry_run=false` only after the dry run succeeds.

Do not select `main` merely because it is the default branch. A maintenance release must run from
its maintenance branch; the workflow validates the selected ref rather than silently replacing it
with `main`.

## Workflow inputs

The release version is derived from the selected branch's
`projectVersion=X.Y.Z-SNAPSHOT` value. It is not entered manually and must equal
`next.release.version=X.Y.Z`.

- `next_development_version`: optional exact next patch snapshot in the same supported X.Y line.
  When empty, the workflow increments the patch component.
- `skip_tests`: skips only the duplicate build at release-version metadata; the release-line and
  readiness gates still run.
- `dry_run`: validates metadata and builds artifacts without pushing refs, publishing packages,
  creating a GitHub release or opening a follow-up PR.

A cross-series next version is rejected. Start a new minor or major line through the reviewed
procedure in [`version-support.md`](version-support.md), not through a release-workflow input.

## Reviewed release requests

A release can also be requested without direct workflow-dispatch access. Create or update exactly
`.github/release-request.json` on the intended supported branch through a normal reviewed PR:

```json
{
  "schemaVersion": 1,
  "releaseVersion": "X.Y.Z",
  "nextDevelopmentVersion": "X.Y.(Z+1)-SNAPSHOT",
  "skipTests": false,
  "dryRun": true,
  "requestId": "line-x.y.z-dry-run",
  "qualifiedCommit": "0123456789abcdef0123456789abcdef01234567"
}
```

`qualifiedCommit` is the exact branch commit qualified before the request-only change. After merge,
the workflow proves that this commit is an ancestor and that the complete diff consists solely of
`.github/release-request.json`. Unknown or duplicate fields, malformed versions, cross-line next
versions, invalid booleans and noncanonical identifiers fail closed.

Use a dry-run request first. A subsequent reviewed update may set `dryRun` to `false`, use a new
`requestId`, and qualify the then-current branch commit. The request file is invocation metadata; it
does not change the selected line's compatibility contract.

## Release workflow behavior

For the selected supported branch, the workflow:

1. derives and validates the release and next-development versions;
2. resolves branch authorization, support status, latest behavior and next-PR base from
   `.github/release-lines.json`;
3. runs the complete release-readiness gate available on that line;
4. updates Gradle, Maven, citation and Zenodo metadata to the release version;
5. adds release-only date metadata;
6. builds and verifies the release artifacts;
7. creates `release/vX.Y.Z` and annotated tag `vX.Y.Z`;
8. publishes project artifacts and creates the GitHub release;
9. marks only the active-line release as latest;
10. prepares the next patch snapshot on a line-specific branch such as
    `release/0.1.x/prepare-next-X.Y.Z-SNAPSHOT`;
11. creates one draft PR targeting the original source branch.

The `Verify release follow-up PR` workflow then:

1. identifies the generated PR by the originating workflow-run marker;
2. rejects cross-repository heads and any file outside the version-metadata allowlist;
3. verifies the branch/version pair against the support policy;
4. dispatches normal CI for the exact head SHA;
5. records a `release/follow-up-ci` status;
6. marks the PR ready and squash-merges it only after CI succeeds.

A failed exact-head CI leaves the PR as draft and unmerged. The automation does not create a second
owner-authored duplicate merely to obtain a CI run.

## Development and release metadata states

Development state:

- all versioned metadata uses `X.Y.Z-SNAPSHOT`;
- `release.properties` uses `X.Y.Z` without `-SNAPSHOT`;
- `.zenodo.json` has no `publication_date`;
- `CITATION.cff` has no `date-released`;
- the snapshot's X.Y series matches its configured branch.

Release state:

- all versioned metadata uses `X.Y.Z`;
- `.zenodo.json` contains `publication_date`;
- `CITATION.cff` contains `date-released`;
- tag `vX.Y.Z` and `release/vX.Y.Z` identify the same release commit.

## Maintenance releases

A maintenance release must contain only changes appropriate for the line's compatibility contract.
It does not import active-line schemas or analysis semantics merely to share a version number.
Release notes must identify the line and explain the backport boundary.

Maintenance releases are published with `latest=false`. This keeps the active release visible in
the repository badge even when a maintenance patch is published later in time.

## End-of-life transition

To stop supporting a line:

1. change its policy status to `end-of-life`;
2. set `nextVersionPolicy` to `none` and `releaseLatest` to `false`;
3. update the README and `version-support.md`;
4. merge the policy change before archiving or restricting the branch.

The release tooling then rejects future publication from that branch while preserving historical
tags and assets.

## Manual tags and partial releases

Do not create normal release tags manually. The Release workflow owns the release branch, annotated
tag, package publication, GitHub Release, checksums and next-development transition. Its state
checks support safe reruns after a partial failure and reject inconsistent tag/branch/release
combinations.
