package com.conveyal.analyst.server.migrations;

import java.io.File;
import java.util.Map.Entry;

import com.google.common.io.Files;

import models.Attribute;
import models.Project;
import models.Shapefile;
import models.User;
import utils.DataStore;

/**
 * Move the storage of most of the data to Java Serialization format; see discussion in issue 68.
 * 
 * This is a java main class and runs outside the Play framework; I just run it inside Eclipse.
 * 
 * @author mattwigway
 */
public class MoveToJavaSerialization {
	public static void main(String... args) {
		File inDir = new File(args[0]);
		File outDir = new File(args[1]);
		
		if (args.length != 2 || !inDir.isDirectory() || !outDir.isDirectory() || outDir.list().length != 0) {
			System.out.println("usage: ... old_data_directory new_data_directory");
			System.out.println("both directories must exist. new directory must be empty.");
			return;
		}
		
		// we don't want to use the built-in datastores of models, so point them at nowhere
		DataStore.dataPath = Files.createTempDir().getAbsolutePath();
		
		// for each of the models, get a datastore, read the data, and then write it out
		System.out.println("Processing shapefiles");
		int migrated = new Migrator<Shapefile>(inDir, outDir, "shapes").migrate();
		System.out.println("Done processing " + migrated + " shapefiles");
		
		System.out.println("Processing users");
		migrated = new Migrator<User>(inDir, outDir, "users").migrate();
		System.out.println("Done processing " + migrated + " users");
		
		System.out.println("Processing projects");
		migrated = new Migrator<Project>(inDir, outDir, "projects").migrate();
		System.out.println("Done processing " + migrated + " projects");
		
		System.out.println("Processing scenarios");
		migrated = new Migrator<User>(inDir, outDir, "scenario").migrate();
		System.out.println("Done processing " + migrated + " scenarios");
		
		System.out.println("Processing queries");
		migrated = new Migrator<User>(inDir, outDir, "queries").migrate();
		System.out.println("Done processing " + migrated + " queries");
	}
	
	/** Migrate a datastore to Java serialization */
	private static class Migrator<T> {
		private File inDir;
		private File outDir;
		private String name;
		
		public Migrator(File inDir, File outDir, String name) {
			this.inDir = inDir;
			this.outDir = outDir;
			this.name = name;
		}
		
		public int migrate() {
			// note: hard wired to transactional and hard-ref default cache. these things are tiny anyhow.
			DataStore<T> in = new DataStore<T>(inDir, name, true, false, false);
			DataStore<T> out = new DataStore<T>(outDir, name, true, false, true);
			
			int migrated = 0;
			
			for (Entry<String, T> kv : in.getEntries()) {
				
				// set category IDs on old shapefiles while we're at it.
				if (kv instanceof Shapefile) {
					Shapefile shp = (Shapefile) kv;
					if (shp.categoryId == null) {
						shp.categoryId = Attribute.convertNameToId(shp.name);
					}				
				}
				
				out.saveWithoutCommit(kv.getKey(), kv.getValue());
				migrated++;
			}
			
			out.commit();
			
			return migrated;
		}
	}
}
