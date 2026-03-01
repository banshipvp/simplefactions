package local.simplefactions;

/**
 * Represents a player's role within a faction.  Roles are ordered from highest
 * privilege to lowest privilege so that {@link #atLeast(Role)} can be used to
 * perform simple permission checks.
 */
public enum Role {
    /** Faction leader with full control. */
    LEADER,
    /** Officer; can invite, kick, claim, etc. */
    OFFICER,
    /** Moderator; limited permissions. */
    MOD,
    /** Regular member with minimal permissions. */
    MEMBER;

    /**
     * Returns true if this role is equal to or higher than {@code other} in the
     * hierarchy.  Example: {@code LEADER.atLeast(OFFICER)} is {@code true},
     * whereas {@code MEMBER.atLeast(OFFICER)} is {@code false}.
     */
    public boolean atLeast(Role other) {
        // enum declaration order gives natural ranking from highest to lowest.
        return this.ordinal() <= other.ordinal();
    }
}