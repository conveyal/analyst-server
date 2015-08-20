package models;

import com.conveyal.analyst.server.AnalystMain;
import com.conveyal.analyst.server.utils.QuotaLedger;
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
import java.util.Optional;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class User implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LoggerFactory.getLogger(User.class);

	private static final QuotaLedger ledger = new QuotaLedger("ledger.db");

	public final String username;
	public final String email;

	public final String name;
	public final boolean active;
	public final boolean admin;

	public final String groupName;

	/** A custom logo for this group */
	public final String logo;

	/** the maximum number of origin points this user's group is allowed to calculate */
	public final long quota;

	private final Account account;

	private String lang;

	public List<ProjectPermissions> projectPermissions = new ArrayList<ProjectPermissions>();

	public User (Account account) {
		this.username = account.getUsername();
		this.email = account.getEmail();
		this.name = account.getFullName();

		this.active = account.getStatus() == AccountStatus.ENABLED;
		Boolean admin = (Boolean) account.getCustomData().get(AnalystMain.config.getProperty("auth.stormpath-name") + "_admin");
		this.admin = admin != null ? admin : false;
		this.lang = (String) account.getCustomData().get(AnalystMain.config.getProperty("auth.stormpath-name") + "_lang");

		List<Object> projectPermissions = (List<Object>) account.getCustomData().get(AnalystMain.config.getProperty("auth.stormpath-name") + "_projectPermissions");

		if (projectPermissions != null)
			this.projectPermissions = projectPermissions.stream().map(o -> {
				if (o instanceof ProjectPermissions) return (ProjectPermissions) o;
				else return new ProjectPermissions((Map<String, Object>) o);
			}).collect(Collectors.toList());
		else
			this.projectPermissions = new ArrayList<>();

		// get the quota from the group
		GroupList groups = account.getGroups();
		if (groups.getSize() == 0) {
			this.quota = 0;
			this.groupName = null;
			this.logo = null;
		}
		else {
			Group group = groups.single();
			Number quota = (Number) group.getCustomData().get(AnalystMain.config.getProperty("auth.stormpath-name") + "_quota");
			if (quota == null) {
				this.quota = 0;
			}
			else {
				this.quota = quota.longValue();
			}

			this.groupName = group.getName();

			this.logo = (String) group.getCustomData().get(AnalystMain.config.getProperty("auth.stormpath-name") + "_logo");
		}

		this.account = account;
	}

	public void save () {
		account.getCustomData().put(AnalystMain.config.getProperty("auth.stormpath-name") + "_projectPermissions", projectPermissions);
		account.getCustomData().put(AnalystMain.config.getProperty("auth.stormpath-name") + "_lang", this.lang);
		account.save();
	}

	public String getLang() {
		if(this.lang != null && !this.lang.isEmpty())
			return this.lang;
		else
			return AnalystMain.config.getProperty("application.lang");

	}

	public void setLang(String lang) {
		this.lang = lang;
		this.save();
	}

	/** the number of origins that have been computed so far */
	public long getQuotaUsage () {
		// we currently don't store purchases of quota in the ledger; we will change this eventually.
		// for the time being then ledger value is always -1 * used points, so flip the sign for display
		return -1 * ledger.getValue(groupName);
	}

	public void addProjectPermission(String projectId) {
		
		if(projectPermissions == null) {
			projectPermissions = new ArrayList<>();
		}

		// update existing if present
		Optional<ProjectPermissions> opt = projectPermissions.stream().filter(pp -> projectId.equals(pp.projectId)).findFirst();
		ProjectPermissions pp;

		if (opt.isPresent())
			pp = opt.get();
		else {
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

	public static List<QuotaLedger.LedgerEntry> getLedgerEntries(String groupId, long fromTime, long toTime) {
		return ledger.getLedgerEntries(groupId, fromTime, toTime);
	}

	public static void addLedgerEntry (QuotaLedger.LedgerEntry entry) {
		ledger.add(entry);
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
