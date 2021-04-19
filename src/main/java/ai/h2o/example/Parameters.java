package ai.h2o.example;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Parameters {
    private final String user;
    private final String appUser;
    private final String authType;
    private final String keyTabPath;
    private final String confDir;
    private final String query;
    private final String destinationDir;

    private Parameters(String user, String appUser, String authType, String keyTabPath, String confDir, String query, String destinationDir) {
        this.user = user;
        this.appUser = appUser;
        this.authType = authType;
        this.keyTabPath = keyTabPath;
        this.confDir = confDir;
        this.query = query;
        this.destinationDir = destinationDir;
    }

    public String getUser() {
        return user;
    }

    public String getAppUser() {
        return appUser;
    }

    public String getAuthType() {
        return authType;
    }

    public String getKeyTabPath() {
        return keyTabPath;
    }

    public String getConfDir() {
        return confDir;
    }

    public String getQuery() {
        return query;
    }

    public String getDestinationDir() {
        return destinationDir;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String user;
        private String authType;
        private String userPrincipal;
        private String userKeytab;
        private String confDir;
        private String query;
        private final String destinationDir;
        private final Path workingDir = Paths.get("").toAbsolutePath();

        public Builder() {
            this.destinationDir = workingDir.resolve("build/results_" + System.currentTimeMillis()).toString();
            setUser(System.getProperty("user.name"));
            setAuthType("NOAUTH");
            setUserPrincipal("");
            setUserKeytab("");
        }

        public Builder setAuthType(String authType) {
            this.authType = authType.toUpperCase();
            this.setConfDir();
            return this;
        }

        public Builder setUser(String user) {
            this.user = user;
            return this;
        }

        public Builder setUserPrincipal(String userPrincipal) {
            this.userPrincipal = userPrincipal;
            return this;
        }

        public Builder setUserKeytab(String userKeytab) {
            this.userKeytab = userKeytab;
            return this;
        }

        public Builder setQuery(String query) {
            this.query = query;
            return this;
        }

        public Parameters build() {
            return new Parameters(user, userPrincipal, authType, userKeytab, confDir, query, destinationDir);
        }

        private void setConfDir() {
            this.confDir = workingDir.resolve("src/test/resources/conf").resolve(authType.toLowerCase()).toString();
        }
    }
}
