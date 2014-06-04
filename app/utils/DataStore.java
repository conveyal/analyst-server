package utils;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import org.mapdb.DB;
import org.mapdb.DBMaker;

import play.Play;

public class DataStore<T> {

	DB db;
	Map<String,T> map;
	
	public DataStore(String dataFile) {
		
		db = DBMaker.newFileDB(new File(Play.application().configuration().getString("application.data"), dataFile))
					.closeOnJvmShutdown()
	               	.make();
		
		map = db.getHashMap("projects");
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
	
}
