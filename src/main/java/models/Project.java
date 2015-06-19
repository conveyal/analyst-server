package models;

import com.conveyal.analyst.server.utils.DataStore;
import com.conveyal.analyst.server.utils.HashUtils;
import com.vividsolutions.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;


public class Project implements Serializable {
	private static final Logger LOG = LoggerFactory.getLogger(Project.class);

	private static final long serialVersionUID = 1L;

	static private DataStore<Project> projectData = new DataStore<Project>("projects", true);
	
	public String id;
	public String name;
	public String description;
	public Geometry boundary;

	public Double defaultLat;
	public Double defaultLon;
	public Integer defaultZoom;
	
	public String defaultScenario;
	
	
	public Project() {
		
	}

	public void save() {
		
		// assign id at save
		if(id == null || id.isEmpty()) {
			
			Date d = new Date();
			id = HashUtils.hashString("p_" + d.toString());
			
			LOG.info("created project p " + id);
		}
		
		projectData.save(id, this);
		
		LOG.info("saved project p " +id);
	}
	
	public void delete() {
		projectData.delete(id);
		
		LOG.info("delete project p " +id);
	}

	static public Project getProject(String id) {
		
		return projectData.getById(id);	
	}
	
	static public Collection<Project> getProjects() {
		
		return projectData.getAll();
		
	}
	
	static public Collection<Project> getProjectsByUser(User u) {
		
		Collection<Project> projectsByUser = new ArrayList<Project>();
		
		for(Project p : projectData.getAll()) {
			if(u.hasReadPermission(p)) {
				projectsByUser.add(p);
			}
		}
		
		return projectsByUser;
	}

}
