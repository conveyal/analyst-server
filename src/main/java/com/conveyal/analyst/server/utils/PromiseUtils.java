package com.conveyal.analyst.server.utils;

import play.libs.F;

/**
 * Promise utilities
 */
public class PromiseUtils {
    /**
     * Return a promise that is immediately resolved with the passed-in result. Useful when a promise is required bu
     * the result has already been computed.
     */
    public static <T> F.Promise<T> resolveNow (final T result) {
        return F.Promise.promise(new F.Function0<T> () {
            public T apply() throws Throwable {
                return result;
            }
        });
    }
}
