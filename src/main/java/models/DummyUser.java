package models;

/**
 * Dummy user model used when authentication is disabled.
 */
public class DummyUser extends User {
    public DummyUser() {
        super("dev", "dev");
        this.email = "nobody@example.com";
        this.active = true;
        this.admin = true;
        this.name = "Development User";
    }

    @Override public void save() {
        return;
    }

    @Override public String getLang() {
        return "en";
    }

    @Override public void setLang(String lang) {
        return;
    }

    @Override public void addProjectPermission(String projectId) {
        return;
    }

    @Override public void addReadOnlyProjectPermission(String projectId) {
        return;
    }

    @Override public boolean hasReadPermission(Project project) {
        return true;
    }

    @Override public boolean hasReadPermission(String projectId) {
        return true;
    }

    @Override public boolean hasWritePermission(Project project) {
        return true;
    }

    @Override public boolean hasWritePermission(String projectId) {
        return true;
    }
}
