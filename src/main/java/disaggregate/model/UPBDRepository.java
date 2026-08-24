package renate.rvu;

import java.nio.file.Path;
import java.util.Random;

public class UPBDRepository extends Repository{
    public UPBDRepository fromCSV(Path filePath) throws Exception {
        return fromCSV(filePath.toString());
    }
    public UPBDRepository fromCSV(String filePath) throws Exception {
        super.fromCSV(filePath);
        return this;
    }

    public UPBD load() throws Exception {
        return load(rows.size());
    }

    @Override 
    public UPBDRepository withRandom(Random random) {
        super.withRandom(random);
        return this;
    }

    public UPBD load(int n) throws Exception {
        logger.info("Loading UPBD from {} with {} rows.", this.getFilePath(), rows.size());
        UPBD upbd = new UPBD(rows);
        if (0 < n && n < upbd.size()) {
            java.util.List<String> ids = upbd.samplePersonIdsWithoutReplacement(n, this.getRandom());
            for (String id : upbd.getIds()) {
                if (!ids.contains(id)) {
                    upbd.removePerson((id));
                }
            }
        }
        logger.info("Loaded UPBD with {} persons.", upbd.size());
        return upbd;
    }
}
