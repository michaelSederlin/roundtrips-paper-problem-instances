package disaggregate.rvu;

// import java.io.FileNotFoundException;
// import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class Repository {
    static Logger logger = LogManager.getLogger(Repository.class);
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


    public String getFilePath() {
        return this.filePath;
    }
}
