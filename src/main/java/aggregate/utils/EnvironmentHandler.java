package aggregate.utils;

import java.nio.file.Path;

public class EnvironmentHandler {
	
	
	
	// -------- Fields ---------
	
    private final String user; 
    private final String host;
    private String project = null;

    
    
	// -------- Construction ---------
    
    public EnvironmentHandler(String project) {
        user = System.getProperty("user.name");
        host = System.getenv("HOSTNAME");
        this.project = project;
    }

    
    
	// -------- Setter & Getter ---------
    
    public void setProject(String project) {
        this.project = project;
    }

    public String getProjectRoot() {
        if ("java-maven-dev".equals(host)) {
            return "./" + project + "/";
        } else if ("MichaelS".equals(user)) {
            return "./";
        } else if ("frupprecht".equals(user)) {
            return "./";
        } else {
            return "./";
        }
    }

    public String getCondaEnv() {
        if ("MichaelS".equals(user) || "java-maven-dev".equals(host) || "micse02".equals(user)) {
            return "renate";
        } else if ("frupprecht".equals(user)) {
            return "amal";
        } else {
            return null;
        }
    }

    public String getPythonBin() {
        return System.getenv("PYTHON_BIN");
    }

    public String getCondaBin() {
        return System.getenv().getOrDefault("CONDA_EXE", "conda");
    }
    
    
    
	// -------- Other Methods ---------

    public Path resolvePath(String other) { 
    	return resolvePath(Path.of(other)); 
    }
    
    public Path resolvePath(Path other) { 
    	return Path.of(this.getProjectRoot()).resolve(other); 
    }
    
}
