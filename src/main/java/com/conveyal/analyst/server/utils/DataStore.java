package com.conveyal.analyst.server.utils;

import com.conveyal.analyst.server.AnalystMain;
import com.google.common.collect.Lists;
import org.mapdb.*;
import org.mapdb.DB.BTreeMapMaker;
import org.mapdb.Fun.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

public class DataStore<T> {

	public static final Logger LOG = LoggerFactory.getLogger(DataStore.class);

	DB db;
	Map<String,T> map;
	
	public static String dataPath = null;
	
	/** Create a new data store in the default location with transactional support enabled and the default cache and serializer */
	public DataStore(String dataFile) {
		
		// allow models to be used outside of the application by specifying a data path directly
		this(new File(dataPath != null ? dataPath : AnalystMain.config.getProperty(
				"application.data")), dataFile, true, false, false);
	}

	/** Create a new data store in the default location with transactional support enabled and the default cache */
	public DataStore(String dataFile, boolean useJavaSerialization) {
	
		this(new File(dataPath != null ? dataPath : AnalystMain.config.getProperty("application.data")), dataFile, true, false, useJavaSerialization);
	}
	
	/**
	 * Create a new datastore with transactional support enabled and a default cache.
	 */
	public DataStore(File directory, String dataFile) {
		this(directory, dataFile, true, false, false);
	}
	
	/**
	 * Create a new DataStore.
	 * @param directory Where should it be created?
	 * @param dataFile What should it be called?
	 * @param transactional Should MapDB's transactional support be enabled?
	 * @param weakRefCache Should we use a weak reference cache instead of the default fixed-size cache?
	 * @param useJavaSerialization Should java serialization be used instead of mapdb serialization (more tolerant to class version changes)?
	 */
	public DataStore(File directory, String dataFile, boolean transactional, boolean weakRefCache, boolean useJavaSerialization) {
	
		if(!directory.exists())
			directory.mkdirs();
		
		try {
			LOG.info(directory.getCanonicalPath());
		} catch (IOException e) {
			LOG.error("IO exception reading data directory", e);
			throw new RuntimeException(e);
		}
		
		DBMaker dbm = DBMaker.newFileDB(new File(directory, dataFile + ".db"))
			.closeOnJvmShutdown();
		
		if (!transactional)
			dbm = dbm.transactionDisable();
		
		if (weakRefCache)
			dbm = dbm.cacheWeakRefEnable();
		
	    db = dbm.make();
		
	    BTreeMapMaker maker = db.createTreeMap(dataFile);
	    
	    // this probably ought to cache the serializer.
	    if (useJavaSerialization)
	    	maker = maker.valueSerializer(Serializer.JAVA);
	    
		map = maker.makeOrGet();
	}
	
	/** create a data store from a _reverse-sorted_ list of features */
	public DataStore(File directory, String dataFile, List<Fun.Tuple2<String,T>>inputData) {
		
		if(!directory.exists())
			directory.mkdirs();
		
		try {
			LOG.info(directory.getCanonicalPath());
		} catch (IOException e) {
			LOG.error("IO exception reading data directory", e);
			throw new RuntimeException(e);
		}
		
		db = DBMaker.newFileDB(new File(directory, dataFile + ".db"))
			.transactionDisable()
			.closeOnJvmShutdown()
	        .make();

		// temporarily disabling string serializer as it throws NPE on some datasets, see mapdb issue 582.
		BTreeKeySerializer<String> keySerializer = BTreeKeySerializer.BASIC;//STRING;

		// shape features already sorted in forward order, but MapDB needs them in reverse order
		Iterator<Tuple2<String, T>> iter = inputData.iterator();
		
		map = db.createTreeMap(dataFile)
			.valuesOutsideNodesEnable()
        	.pumpSource(iter)
        	.keySerializer(keySerializer)
			.valueSerializer(Serializer.JAVA)
        	.make();
		
		// close/flush db 
		db.close();
		
		// re-connect with transactions enabled
		db = DBMaker.newFileDB(new File(directory, dataFile + ".db"))
				.closeOnJvmShutdown()
				.cacheWeakRefEnable()
		        .make();
		
		map = db.getTreeMap(dataFile);
	}
	
	public void save(String id, T obj) {
		map.put(id, obj);
		db.commit();
	}
	
	public void saveWithoutCommit(String id, T obj) {
		map.put(id, obj);
	}
	
	public void commit() {
		db.commit();
	}
	
	public void delete(String id) {
		map.remove(id);
		db.commit();
	}
	
	public T getById(String id) {
		return map.get(id); 
	}
	
	public Collection<T> getAll() {
		return map.values();
	}
	
	public Collection<Entry<String, T>> getEntries () {
		return map.entrySet();
	}

	public void addAll(DataStore<T> source) {
		map.putAll(source.map);
		db.commit();
	}
	
	public Integer size() {
		return map.keySet().size();
	}
	
	public boolean contains (String id) {
		return map.containsKey(id);
	}

	public boolean isEmpty() {
		return map.isEmpty();
	}

	public void close() {
		db.close();
	}

	// methods to get and manipulate atomic ints
	/** Create a new atomic int */
	public int createInt (String name, int value) {
		return db.createAtomicInteger(name, value).get();
	}

	/** Increment an atomic int and return its value */
	public int incrementInt(String name) {
		return db.getAtomicInteger(name).incrementAndGet();
	}

	/** Get the value of an atomic int */
	public int getInt(String name) {
		return db.getAtomicInteger(name).get();
	}
	
}
