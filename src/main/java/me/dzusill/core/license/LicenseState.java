package me.dzusill.core.license;

/**
 * Immutable snapshot of the last verification. {@code validUntilMillis} is the server-signed grace deadline: while it
 * is in the future the plugin runs even if the license server is unreachable.
 */
public record LicenseState(LicenseVerdict verdict, long validUntilMillis, String message) {

    public boolean allowsRunning(long now) {
        return verdict == LicenseVerdict.VALID && now < validUntilMillis;
    }
}
