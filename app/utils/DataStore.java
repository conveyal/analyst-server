package utils;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import models.Shapefile.ShapeFeature;

import org.mapdb.BTreeKeySerializer;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Fun;
import org.mapdb.Pump;
import org.mapdb.Fun.Tuple2;

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
		
		map = db.getTreeMap(dataFile);
	}
	
	public DataStore(File directory, String dataFile, List<Fun.Tuple2<String,T>>inputData) {
		
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
