package models;

import com.conveyal.analyst.server.AnalystMain;
import com.conveyal.analyst.server.utils.QuotaStore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountStatus;
import com.stormpath.sdk.group.Group;
import com.stormpath.sdk.group.GroupList;
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

	private static final QuotaStore quotaStore = new QuotaStore("quota.db");

	public final String username;
	public final String email;

	public final String name;
	public final boolean active;
	public final boolean admin;

	/** the maximum number of origin points this user's group is allowed to calculate */
	public final long quota;

	private final Account account;

	public List<ProjectPermissions> projectPermissions = new ArrayList<ProjectPermissions>();

	public User (Account account) {
		this.username = account.getUsername();
		this.email = account.getEmail();
		this.name = account.getFullName();

		this.active = account.getStatus() == AccountStatus.ENABLED;
		this.admin = Boolean.parseBoolean((String) account.getCustomData().get("analyst_admin"));

		List<Object> projectPermissions = (List<Object>) account.getCustomData().get(AnalystMain.config.getProperty("auth.stormpath-name") + "_projectPermissions");

		this.projectPermissions = projectPermissions.stream().map(o -> {
			if (o instanceof ProjectPermissions) return (ProjectPermissions) o;
			else return new ProjectPermissions((Map<String, Object>) o);
		}).collect(Collectors.toList());

		// get the quota from the group
		GroupList groups = account.getGroups();
		if (groups.getSize() == 0) {
			LOG.warn("User {} has no groups, computation will be forbidden", username);
			this.quota = 0;
		}
		else {
			Group group = groups.single();
			Number quota = (Number) group.getCustomData().get(AnalystMain.config.getProperty("auth.stormpath-name") + "_quota");
			if (quota == null) {
				LOG.warn("Quota not specified for group {}, computation will be unavailable for user {}", group.getName(), username);
				this.quota = 0;
			}
			else {
				this.quota = quota.longValue();
			}
		}

		this.account = account;
	}

	public void save () {
		account.getCustomData().put("analyst_projectPermissions", projectPermissions);
		account.save();
	}


	/** the number of origins that have been computed so far */
	public long getQuotaUsage () {
		return quotaStore.getQuotaUsage(username);
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

	/** increment the quota usage of this user */
	public void incrementQuotaUsage (int increment) {
		quotaStore.incrementQuotaUsage(username, increment);
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
