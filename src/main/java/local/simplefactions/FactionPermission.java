package local.simplefactions;

/**
 * Enumeration of all possible faction permissions.
 * Used by {@link FactionManager#hasPerm(UUID, FactionPermission)} and
 * checked throughout the command handlers to determine whether a player
 * is allowed to perform an action.
 */
public enum FactionPermission {
    OPEN_CHEST,
    HOME,
    INVITE,
    KICK,
    CLAIM,
    UNCLAIM,
    UNCLAIM_ALL,
    SET_HOME,
    SET_DESC,
    PROMOTE,
    DISBAND,

    // extended permissions
    POWER,          // view your faction's power
    SET_POWER,      // adjust power directly
    ADD_POWER,      // increase power (e.g. by death loss)

    ALLY,           // create an alliance
    ENEMY,          // declare war/enemy
    UNALLY,         // remove alliance
    UNENEMY,        // remove enemy

    FLAG,           // toggle claim flags (pvp/explosion/etc)
    BALANCE         // view faction balance / economy
}