package utils;

import java.util.UUID;

/**
 * Utilities for getting IDs.
 */
public class IdUtils {
    /** Get a random ID. This is a UUID with hyphens removed to save space */
    public static String getId () {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }
}
