package utils;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.mapdb.BTreeKeySerializer;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import com.google.common.collect.Maps;

import controllers.Application;
import models.Query;

/**
 * A datastore optimized for storing query results: high performance reading of a single variable, and high performance writing overall.
 */
public class QueryResultStore {
	public final boolean readOnly;
	private DB db;
	
	/** cache mapdb maps so we don't continually create them */
	private HashMap<String, BTreeMap<String, ResultEnvelope>> mapCache = Maps.newHashMap();
	
	public QueryResultStore (Query q) {
		this.readOnly = q.completePoints == q.totalPoints;
		File resultDir = new File(Application.dataPath, "query_results");
		resultDir.mkdirs();
		
		initialize(new File(resultDir, q.id + ".db"));
	}
	
	public QueryResultStore (File dbFile, boolean readOnly) {
		this.readOnly = readOnly;
		initialize(dbFile);
	}
	
	private void initialize (File dbFile) {		
		DBMaker dbm = DBMaker.newFileDB(dbFile)
				// promise me you'll never run this on a 32bit machine
				.mmapFileEnable()
				.transactionDisable()
				// don't cache too much stuff
				.cacheSize(2000);
		
		if (readOnly)
			dbm.readOnly();
		
		db = dbm.make();
	}
	
	/** Save a result envelope in this store */
	public void store(ResultEnvelope res) {
		// We store each variable individually, to increase read performance.
		// The client only ever needs to see one variable at a time; we don't want to loop over the entire mapdb to render one tileset
		Map<String, ResultEnvelope> exploded = res.explode();
		
		for (Entry<String, ResultEnvelope> attr : exploded.entrySet()) {
			getMap(attr.getKey()).put(attr.getValue().id, attr.getValue());
		}
	}
	
	/** close the underlying mapdb, writing all changes to disk */
	public void close () {
		db.close();
	}
	
	/** get all the result envelopes for a particular variable */
	public Collection<ResultEnvelope> getAll(String attr) {
		if (!db.exists(attr))
			return null;
		
		return getMap(attr).values();
	}
	
	/** Get a map for a particular variable */
	private BTreeMap<String, ResultEnvelope> getMap (String attr) {
		if (!mapCache.containsKey(attr)) {
			synchronized (mapCache) {
				if (!mapCache.containsKey(attr)) {
					BTreeMap<String, ResultEnvelope> map = db.createTreeMap(attr)
							.keySerializer(BTreeKeySerializer.STRING)
							// we've seen issues with disk performance, and we're disk bound, so spend a little CPU to save a lot of disk
							.valueSerializer(new Serializer.CompressionWrapper(db.getDefaultSerializer()))
							.makeOrGet();
					mapCache.put(attr, map);
					return map;
				}
			}
		}
		
		return mapCache.get(attr);
	}
}
