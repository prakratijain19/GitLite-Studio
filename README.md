# GitLite-Studio

GitLite-Studio is a desktop version control system built with Java and JavaFX. It provides a robust, locally managed version control simulator (GitLite) paired with a modern graphical user interface.

## Architecture Overview

The application is structured into three distinct layers to ensure clear separation of concerns, testability, and maintainability.

### 1. Presentation Layer (`app.controller` & `app/view/*.fxml`)
The GUI is built using JavaFX. The controllers are deliberately kept thin. They capture user interactions (like button clicks or file selections), delegate the complex operations to the Service layer, and render the resulting data or catch and display any exceptions.
- **HomeController**: The main hub for opening/initializing repositories and launching other views.
- **CommitController**: Displays staging area and commits changes.
- **HistoryController**: Displays the commit history DAG in a structured table.
- **BranchController**: Lists, creates, and checks out branches.
- **MergeController**: Merges branches and surfaces any conflict warnings.
- **DiffController**: Shows line-level differences for files.

### 2. Service Layer (`app.service`)
This layer contains all the core business logic and Git semantics. It is completely independent of the UI and the underlying storage format.
- **BranchService**: Manages branch creation, tracking, and tip advancement.
- **CheckoutService**: Restores working tree states and switches branches.
- **CommitService**: Freezes the staging index into permanent commits, handling multi-parent merge commits.
- **DiffService**: Implements the Longest Common Subsequence (LCS) algorithm to generate line-level diffs.
- **HistoryService**: Traverses the commit DAG (using BFS to support merge commits) to trace history and find merge bases.
- **MergeService**: Performs fast-forward and three-way merges. Detects conflicts, injects markers into the working tree, and transitions into a `MERGE_HEAD` state.
- **StagingService**: Hashes working tree files and prepares the index.
- **StatusService**: Compares the working tree, index, and HEAD to categorize untracked, unstaged, and staged changes.

### 3. Storage Layer (`app.storage`)
Handles all disk I/O and object serialization within the `.gitlite` directory.
- **ObjectStorage**: Manages content-addressed blobs (SHA-256).
- **CommitStorage**: Serializes `Commit` objects to disk.
- **IndexStorage**: Reads and writes the staging area manifest.
- **FileStorage**: Manages raw `.gitlite` files such as `HEAD`, `MERGE_HEAD`, and branch tip files.

## Features Implemented
- Repository initialization and discovery.
- Staging and committing files.
- Branching and checking out different states.
- Multi-parent commit history traversal.
- Fast-forward and full Three-way merges.
- Merge conflict detection with working tree `<<<<<<<` markers.
- Visual history, diff, and merge control interfaces.

## How to Build and Run
This project uses Maven. To compile and run the JavaFX application, execute:

```bash
mvn clean javafx:run
```

## Testing Coverage
The service layer is heavily unit-tested using **JUnit 5**, with tests executing in isolated temporary directories using `@TempDir`. 

Currently tested services:
- `BranchServiceTest`
- `CheckoutServiceTest`
- `CommitServiceTest`
- `DiffServiceTest`
- `HashServiceTest`
- `HistoryServiceTest`
- `MergeServiceTest`
- `RepositoryServiceTest`
- `StagingServiceTest`
- `StatusServiceTest`

To run the test suite:
```bash
mvn clean test
```
