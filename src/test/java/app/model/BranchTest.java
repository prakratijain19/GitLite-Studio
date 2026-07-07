package app.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Branch} model, pinning down its two subtle contract
 * points: the unborn (no-tip) state and name-based equality.
 */
class BranchTest {

    private static final String NAME = "master";
    private static final String COMMIT_A = "aaa111";
    private static final String COMMIT_B = "bbb222";

    @Test
    @DisplayName("unborn() has no tip")
    void unbornHasNoTip() {
        Branch branch = Branch.unborn(NAME);

        assertFalse(branch.hasCommits());
        assertEquals(Optional.empty(), branch.getTipCommitId());
    }

    @Test
    @DisplayName("at() points at the given commit")
    void atHasTip() {
        Branch branch = Branch.at(NAME, COMMIT_A);

        assertTrue(branch.hasCommits());
        assertEquals(Optional.of(COMMIT_A), branch.getTipCommitId());
    }

    @Test
    @DisplayName("withTip() returns a new advanced branch and leaves the original unchanged")
    void withTipIsImmutableAdvance() {
        Branch original = Branch.unborn(NAME);
        Branch advanced = original.withTip(COMMIT_A);

        assertFalse(original.hasCommits(), "original must be untouched");
        assertEquals(Optional.of(COMMIT_A), advanced.getTipCommitId());
    }

    @Test
    @DisplayName("equality is by name only, independent of the tip")
    void equalsByNameOnly() {
        assertEquals(Branch.unborn(NAME), Branch.at(NAME, COMMIT_A));
        assertEquals(Branch.at(NAME, COMMIT_A), Branch.at(NAME, COMMIT_B));
        assertEquals(Branch.unborn(NAME).hashCode(), Branch.at(NAME, COMMIT_A).hashCode());
        assertNotEquals(Branch.unborn(NAME), Branch.unborn("develop"));
    }

    @Test
    @DisplayName("a blank name is rejected")
    void blankNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> Branch.unborn("  "));
    }

    @Test
    @DisplayName("a blank tip commit id is rejected")
    void blankTipRejected() {
        assertThrows(IllegalArgumentException.class, () -> Branch.at(NAME, ""));
    }
}
