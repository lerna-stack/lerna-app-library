# Releasing

This document shows how to release a new version.

## 1. Create a new branch

```shell
git checkout main
git pull
git checkout -b release/vN.N.N # replace N.N.N to a new release version
```

## 2. Create a new PR

Create a new PR with the [`releasing.md` PR Template](./.github/PULL_REQUEST_TEMPLATE/releasing.md) after pushing the branch:

```shell
git push
```

Ask a maintainer to review the PR after submitting it.
