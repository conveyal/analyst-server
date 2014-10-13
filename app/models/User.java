package models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import play.Logger;
import utils.DataStore;
import utils.HashUtils;

import com.vividsolutions.jts.geom.Geometry;


public class User implements Serializable {

	private static final long serialVersionUID = 1L;

	static private DataStore<User> userData = new DataStore<User>("users");
	
	public String id;
	public String username;
	public String password_hash;
	public String email;
	
	public Boolean admin;
	
	public ArrayList<ProjectPermissions> projectPermissions;

	public User() {
		
	}

	public void save() {
		
		// assign id at save
		if(id == null || id.isEmpty()) {
			
			Date d = new Date();
			id = HashUtils.hashString("u_" + d.toString());
			
			Logger.info("created user u " + id);
		}
		
		userData.save(id, this);
		
		Logger.info("saved user u " +id);
	}
	
	public void delete() {
		userData.delete(id);
		
		Logger.info("delete user u " +id);
	}

	static public User getUser(String id) {
		
		return userData.getById(id);	
	}
	
	static public Collection<User> getProjects() {
		
		return userData.getAll();
		
	}
	
	static class ProjectPermissions {
		
		String project_id;
		Boolean read;
		Boolean write;
		Boolean admin;
		
	}

}
