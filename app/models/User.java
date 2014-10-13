package models;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;

import play.Logger;
import play.Play;
import utils.DataStore;
import utils.HashUtils;

import com.vividsolutions.jts.geom.Geometry;


public class User implements Serializable {

	private static final long serialVersionUID = 1L;

	static private DataStore<User> userData = new DataStore<User>("users");
	
	public String id;
	public String username;
	public String passwordHash;
	public String email;
	
	public Boolean active;
	public Boolean admin;
	
	public ArrayList<ProjectPermissions> projectPermissions;

	public User(String username, String password, String email) throws Exception {
		
		this.username = username.toLowerCase();
		
		// check for duplicate usernames
		if(userData.getById(getUserId(this.username)) != null)
				throw new Exception("Username " + this.username + " already exists"); 
		
		this.email = email;
		this.active = true;
		this.admin = false;
		
		try {
			
			this.passwordHash = getPasswordHash(password);
			
		}
		catch(Exception e) {
			
			this.active = false;
			this.passwordHash = "";
		}
	
		
	}
	
	public void save() {
		
		// assign id at save
		if(id == null || id.isEmpty()) {
			
			Date d = new Date();
			id = getUserId(this.username);
			
			Logger.info("created user u " + id);
		}
		
		userData.save(id, this);
		
		Logger.info("saved user u " +id);
	}
	
	public void delete() {
		userData.delete(id);
		
		Logger.info("delete user u " +id);
	}

	public Boolean checkPassword(String password) {	
		try {
			
			return this.passwordHash.equals(getPasswordHash(password));
			
		}
		catch(Exception e) {
			
			return false;
		}
	}
	
	static public String getUserId(String username) {
		return HashUtils.hashString("u_" + username);
	}
	
	static public String getPasswordHash(String password) throws UnsupportedEncodingException {
		byte[] bytesOfMessage = (password + Play.application().configuration().getString("application.secret")).getBytes("UTF-8");	
		
		return DigestUtils.shaHex(bytesOfMessage);
	}

	static public User getUser(String id) {
		
		return userData.getById(id);	
	}
	
	static public User getUserByUsername(String username) {
		
		return userData.getById(getUserId(username));	
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
