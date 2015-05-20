package tiles;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.concurrent.ExecutionException;

public class TileCache extends CacheLoader<AnalystTileRequest, byte[]> {
	
	 private LoadingCache<AnalystTileRequest, byte[]> tileCache;

	 private int size = 200;
	 private int concurrency = 16;
	 
	 public TileCache() {
		 
		 this.tileCache = CacheBuilder.newBuilder()
				 .concurrencyLevel(concurrency)
				 .maximumSize(size)
				 .build(this);
	 }
	 
	 public byte[] get(AnalystTileRequest req) {
		try {
			return tileCache.get(req);
		} catch (ExecutionException e) {
			return null;
		}
	 }

	 public void clear() {
		 tileCache.invalidateAll();
	 }
	 
	 @Override
	 public byte[] load(final AnalystTileRequest request) throws Exception {
		 
		 return request.render();
	
	 }	 
}
