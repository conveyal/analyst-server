package models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class User implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LoggerFactory.getLogger(User.class);

	public final String username;
	public final String email;

	public final String name;
	public final boolean active;
	public final boolean admin;

	private final Account account;

	public List<ProjectPermissions> projectPermissions = new ArrayList<ProjectPermissions>();

	public User (Account account) {
		this.username = account.getUsername();
		this.email = account.getEmail();
		this.name = account.getFullName();

		this.active = account.getStatus() == AccountStatus.ENABLED;
		this.admin = Boolean.parseBoolean((String) account.getCustomData().get("analyst_admin"));

		List<Object> projectPermissions = (List<Object>) account.getCustomData().get("analyst_projectPermissions");

		this.projectPermissions = projectPermissions.stream().map(o -> {
			if (o instanceof ProjectPermissions) return (ProjectPermissions) o;
			else return new ProjectPermissions((Map<String, Object>) o);
		}).collect(Collectors.toList());

		this.account = account;
	}

	public void save () {
		account.getCustomData().put("analyst_projectPermissions", projectPermissions);
		account.save();
	}

	public void addProjectPermission(String projectId) {
		
		if(projectPermissions == null) {
			projectPermissions = new ArrayList<ProjectPermissions>();
		}
		
		ProjectPermissions pp = null;
		
		for(ProjectPermissions pp1 : projectPermissions) {
			
			if(pp1.projectId.equals(projectId))
				pp = new ProjectPermissions();
		}
		
		if(pp == null) {
			pp = new ProjectPermissions();
			pp.projectId = projectId;
			projectPermissions.add(pp);
		}
			
		pp.read = true;
		pp.write = true;
		pp.admin = true;
		
	}

	public void addReadOnlyProjectPermission(String projectId) {

		if(projectPermissions == null) {
			projectPermissions = new ArrayList<ProjectPermissions>();
		}

		ProjectPermissions pp = null;

		for(ProjectPermissions pp1 : projectPermissions) {

			if(pp1.projectId.equals(projectId))
				pp = new ProjectPermissions();
		}

		if(pp == null) {
			pp = new ProjectPermissions();
			pp.projectId = projectId;
			projectPermissions.add(pp);
		}

		pp.read = true;
		pp.write = false;
		pp.admin = false;

	}

	public boolean hasReadPermission(Project project) {
		return hasReadPermission(project.id);
	}

	public boolean hasReadPermission(String projectId) {
		if (projectPermissions == null)
			return false;

		for(ProjectPermissions pp : projectPermissions) {
			if(pp.projectId.equals(projectId) && pp.read)
				return true;
		}
		
		return false;
	}

	public boolean hasWritePermission(Project project) {
		return hasWritePermission(project.id);
	}

	public boolean hasWritePermission(String projectId) {
		if (projectPermissions == null)
			return false;

		for(ProjectPermissions pp : projectPermissions) {
			if(pp.projectId.equals(projectId) && pp.write)
				return true;
		}

		return false;
	}
	
	public static class ProjectPermissions implements Serializable {

		/** create project permissions from String, String map as deserialized by Stormpath */
		public ProjectPermissions (Map<String, Object> serialized) {
			this.projectId = (String) serialized.get("projectId");
			this.read = (Boolean) serialized.get("read");
			this.write = (Boolean) serialized.get("write");
			this.admin = (Boolean) serialized.get("admin");
		}

		private static final long serialVersionUID = 1L;
		public String projectId;
		public boolean read;
		public boolean write;
		public boolean admin;
	
		public ProjectPermissions() {
			
		}
		
	}

}
