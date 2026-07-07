package app.model;

import java.time.LocalDateTime;

/**
 * The on-disk configuration record for a repository, persisted as
 * {@code .gitlite/config}.
 *
 * <p>This is a deliberate <strong>persistence DTO</strong>, kept separate from
 * the {@link Repository} domain model. The two describe different concerns:
 * {@code Repository} is the in-memory identity and behaviour of a repository,
 * while {@code RepositoryConfig} is precisely the set of fields we choose to
 * write to and read from disk. Decoupling them lets the storage format evolve
 * (add or rename config keys) without disturbing the domain model, and keeps the
 * immutable, private-constructor {@code Repository} free of the no-arg/binding
 * concessions JSON deserialization would otherwise impose.
 *
 * <p>It is implemented as a {@code record}, which Jackson binds directly (both
 * ways) via the record's components — giving us an immutable, boilerplate-free
 * DTO that serializes cleanly.
 *
 * @param defaultBranch the name of the branch created at initialization.
 * @param createdAt     the timestamp the repository was initialized.
 * @param userName      the identity recorded for the repository's user.
 */
public record RepositoryConfig(
        String defaultBranch,
        LocalDateTime createdAt,
        String userName) {
}
