package utils;

import controllers.Application;
import org.mapdb.*;
import org.mapdb.DB.BTreeMapMaker;
import org.mapdb.Fun.Tuple2;
import play.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

public class DataStore<T> {

	DB db;
	Map<String,T> map;
	
	public static String dataPath = null;
	
	/** Create a new data store in the default location with transactional support enabled and the default cache and serializer */
	public DataStore(String dataFile) {
		
		// allow models to be used outside of the application by specifying a data path directly
		this(new File(dataPath != null ? dataPath : Application.dataPath), dataFile, true, false, false);
	}

	/** Create a new data store in the default location with transactional support enabled and the default cache */
	public DataStore(String dataFile, boolean useJavaSerialization) {
	
		this(new File(dataPath != null ? dataPath : Application.dataPath), dataFile, true, false, useJavaSerialization);
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
			Logger.info(directory.getCanonicalPath());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
	    	maker = maker.valueSerializer(new ClassLoaderSerializer());
	    
		map = maker.makeOrGet();
	}
	
	// TODO: add all the other arguments about what kind of serialization, transactions, etc.
	public DataStore(File directory, String dataFile, List<Fun.Tuple2<String,T>>inputData) {
		
		if(!directory.exists())
			directory.mkdirs();
		
		try {
			Logger.info(directory.getCanonicalPath());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		db = DBMaker.newFileDB(new File(directory, dataFile + ".db"))
			.transactionDisable()
			.closeOnJvmShutdown()
	        .make();
        
		Comparator<Tuple2<String, T>> comparator = new Comparator<Fun.Tuple2<String,T>>(){

			@Override
			public int compare(Tuple2<String, T> o1,
					Tuple2<String, T> o2) {
				return o1.a.compareTo(o2.a);
			}
		};

		// need to reverse sort list
		Iterator<Fun.Tuple2<String,T>> iter = Pump.sort(inputData.iterator(),
                true, 100000,
                Collections.reverseOrder(comparator), //reverse  order comparator
                db.getDefaultSerializer()
                );
		
		
		BTreeKeySerializer<String> keySerializer = BTreeKeySerializer.STRING;
		
		map = db.createTreeMap(dataFile)
        	.pumpSource(iter)
        	.pumpPresort(100000) 
        	.keySerializer(keySerializer)
        	.make();
		
		// close/flush db 
		db.close();
		
		// re-connect with transactions enabled
		db = DBMaker.newFileDB(new File(directory, dataFile + ".db"))
				.closeOnJvmShutdown()
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
