package otp;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Caches travel time surfaces, which are derived from shortest path trees.
 * TODO add LRU behavior upon get
 * TODO extend to store any type by moving the IDs into the cache
 * TODO use a disk-backed MapDB to avoid eating memory
 */
public class ProfileResultCache {

    public static final int NONE = -1;
    public final Cache<Integer, ProfileResult> cache;

    public ProfileResultCache (int capacity) {
        this.cache = CacheBuilder.newBuilder()
        	       		.maximumSize(100)
        	       		.build();
    }

    public int add(ProfileResult result) {
    	this.cache.put(result.id, result);
    	return result.id;
    }

    public ProfileResult get(int id) {
        return this.cache.getIfPresent(id);
    }

}
