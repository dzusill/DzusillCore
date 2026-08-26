package me.dzusill.core.license;

/**
 * Server verdicts for a license check. {@code UNKNOWN} means the response could not be reached or its signature could
 * not be verified — it drives the offline-grace path, never an instant disable, so a flaky network or a proxy that
 * mangles traffic can't brick a paying customer.
 */
public enum LicenseVerdict {
    VALID, INVALID, REVOKED, EXPIRED, INSTANCE_LIMIT, UNKNOWN
}
