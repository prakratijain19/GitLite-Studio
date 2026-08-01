
https://github.com/user-attachments/assets/86aabae4-44c2-471d-9465-f7ab0360e7c9
# GitLite-Studio

![Build](https://github.com/prakratijain19/GitLite-Studio/actions/workflows/build.yml/badge.svg)
![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)
![Java 17](https://img.shields.io/badge/Java-17-orange.svg)
![Tests](https://img.shields.io/badge/tests-125%20passing-brightgreen.svg)

GitLite-Studio is a desktop version control system built from scratch in Java — no JGit, no wrapping the real `git` binary. It reimplements Git's core internals (content-addressed storage, commit DAGs, three-way merges) and pairs them with a JavaFX GUI, built to understand *how* Git works internally rather than just how to use it.

<!--
DEMO VIDEO: 

https://github.com/user-attachments/assets/8ca57f6e-9e82-45d9-8e51-958cd779e593


README — the only path that renders a real inline player is dragging the file
into GitHub's own editor, which replaces this comment with something like:
https://github.com/user-attachments/assets/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
-->


## Features

- Repository initialization and discovery
- Staging and committing files, with content-addressed blob storage (SHA-256)
- Branching and checking out different states
- Multi-parent commit history, traversed via BFS to support merges
- Fast-forward and full three-way merges, with conflict markers (`<<<<<<<`) injected into the working tree
- Line-level diffs via a Longest Common Subsequence (LCS) implementation
- Visual history, diff, and merge interfaces (JavaFX)
- 125 unit tests covering the entire service layer, run in isolated temp directories on every push via GitHub Actions

## Architecture

The application is split into three layers, kept strictly separate so the core Git logic has no dependency on the UI or the on-disk format.

```
┌─────────────────────────────────────────────┐
│  Presentation Layer  (app.controller, *.fxml)│
│  JavaFX views + thin controllers             │
└───────────────────┬───────────────────────────┘
                    │ delegates to
┌───────────────────▼───────────────────────────┐
│  Service Layer  (app.service)                 │
│  Git semantics, merge/diff/history algorithms │
└───────────────────┬───────────────────────────┘
                    │ reads/writes via
┌───────────────────▼───────────────────────────┐
│  Storage Layer  (app.storage)                 │
│  .gitlite/ on-disk format, object hashing     │
└─────────────────────────────────────────────────┘
```

### 1. Presentation layer — `app.controller`, `app/view/*.fxml`
Controllers are deliberately thin: they capture user interactions, delegate to the service layer, and render results or exceptions. No business logic lives here.

- **HomeController** — opens/initializes repositories, launches other views
- **CommitController** — staging area and commit creation
- **HistoryController** — commit history DAG in a structured table
- **BranchController** — lists, creates, and checks out branches
- **MergeController** — merges branches, surfaces conflict warnings
- **DiffController** — line-level diffs for files

### 2. Service layer — `app.service`
All core Git semantics live here, independent of both the UI and the storage format.

- **BranchService** — branch creation, tracking, tip advancement
- **CheckoutService** — restores working tree states, switches branches
- **CommitService** — freezes the staging index into commits, handles multi-parent merge commits
- **DiffService** — LCS-based line-level diff generation
- **HistoryService** — traverses the commit DAG (BFS) to trace history and find merge bases
- **MergeService** — fast-forward and three-way merges; detects conflicts, injects markers, transitions into `MERGE_HEAD` state
- **StagingService** — hashes working tree files, prepares the index
- **StatusService** — compares working tree, index, and HEAD to categorize changes

### 3. Storage layer — `app.storage`
Handles all disk I/O and object serialization inside the `.gitlite` directory.

- **ObjectStorage** — content-addressed blobs (SHA-256)
- **CommitStorage** — serializes `Commit` objects to disk
- **IndexStorage** — reads/writes the staging area manifest
- **FileStorage** — raw `.gitlite` files (`HEAD`, `MERGE_HEAD`, branch tips)

## How to Build and Run

Requires JDK 17+ and Maven.

```bash
mvn clean javafx:run
```

## Testing

The service layer is heavily unit-tested with **JUnit 5**, using `@TempDir` so every test runs against an isolated, real filesystem rather than mocks. All 125 tests run on every push and pull request via GitHub Actions.

```bash
mvn clean test
```

Covered services: `BranchService`, `CheckoutService`, `CommitService`, `DiffService`, `HashService`, `HistoryService`, `MergeService`, `RepositoryService`, `StagingService`, `StatusService`.

## Known Limitations / Roadmap

GitLite-Studio implements Git's core local workflow; it doesn't attempt to be a full Git replacement.

- No remote support (no `push`/`pull`/`fetch`) — everything is local
- No rebase or interactive history rewriting
- No `.gitignore`-style pattern matching yet — all files in the working tree are tracked
- Merge conflict resolution is manual (edit + re-stage), no in-app conflict editor yet

## License

MIT — see [LICENSE](LICENSE).
