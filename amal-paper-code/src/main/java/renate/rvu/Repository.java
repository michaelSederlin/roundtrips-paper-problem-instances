package renate.rvu;

// import java.io.FileNotFoundException;
// import java.io.IOException;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import se.vti.utils.connections.EngineCreator;
import se.vti.utils.connections.UserLoader;

public class Repository {
    static Logger logger = LogManager.getLogger(Repository.class);
    public Connection conn; 
    public String filePath;
    private Random random = new Random();
    List<Map<String, String>> rows;
    
    public Repository() {}

    public void setRandom(Random random) {
        this.random = random;
    }

    public Repository withRandom(Random random) {
        setRandom(random);
        return this;
    }

    public Random getRandom() {
        return this.random;
    }

    @SuppressWarnings("unchecked")
    public <T extends Repository> T fromCSV(String fp) throws Exception {
        rows = RvuDataset.loadRowsFromCsv(fp);
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public <T extends Repository> T fromPSQL() throws Exception {
        this.conn = new EngineCreator(UserLoader.getUser(
                "postgres-superuser",
                Map.of("database", "renate")
            ), "postgresql"
        ).connect();
        return (T) this;
    }

    public String getFilePath() {
        return this.filePath;
    }
}
