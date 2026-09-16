package aggregate.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;



public class PythonRunner {
	
	
	
	// ---- Fields ----
	
    String scriptPath;
    String[] args = {};
    EnvironmentHandler env = new EnvironmentHandler("amal");
    
    
    
	// ---- Construction ----
    
	public PythonRunner(String scriptPath) {
        this.scriptPath = scriptPath;
    }
    
	public PythonRunner(String scriptPath, String... args) {
        this.scriptPath = scriptPath;
        this.args = args;
    }
	
	
	
	// ---- Functionality ----

    public PythonRunner setEnvironmentHandler(String project) {
        this.env = new EnvironmentHandler(project);
        return this;
    }
    
    public int run() throws IOException, InterruptedException {  	
    	List<String> cmd = new ArrayList<>();
        
        // Add Python executable
        if (env.getPythonBin() != null && !env.getPythonBin().isBlank()) {
            cmd.add(env.getPythonBin());
        } else {
            cmd.add(env.getCondaBin());
            cmd.add("run");
            cmd.add("-n");
            cmd.add(env.getCondaEnv());
            cmd.add("python");
        }
        
        // Add script
        cmd.add(env.getProjectRoot() + scriptPath);
        
        // Add arguments
        for (String arg : args) {
            cmd.add(arg);
        }
        
        Process p = new ProcessBuilder(cmd).inheritIO().start();
        return p.waitFor();
    }
}
