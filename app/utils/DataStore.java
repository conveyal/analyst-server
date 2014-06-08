package utils;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import org.mapdb.DB;
import org.mapdb.DBMaker;

import controllers.Application;
import play.Play;

public class DataStore<T> {

	DB db;
	Map<String,T> map;
	
	public DataStore(String dataFile) {
	
		this(new File(Application.dataPath), dataFile);
	}

	public DataStore(File directory, String dataFile) {
		
		db = DBMaker.newFileDB(new File(directory, dataFile + ".db"))
			.closeOnJvmShutdown()
	        .make();
		
		map = db.getHashMap(dataFile);
	}
	
	public void save(String id, T obj) {
		map.put(id, obj);
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
	
	public Integer size() {
		return map.keySet().size();
	}
	
}
