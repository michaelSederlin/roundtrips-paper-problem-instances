package renate.rvu;

import java.nio.file.Path;
import java.util.Random;

public class MDDRRepository extends Repository{
	public MDDRRepository() throws Exception {
        super();
	}

	public MDDRRepository fromPSQL(int n, String type) throws Exception {
		String sqlBase;
		if (type.equals("complete")) {
			sqlBase = "rvu_stockholm.complete_mddr";
		} else if (type.equals("filtered")) {
			sqlBase = "rvu_stockholm.mddr1116_filtered2";
		} else {
			sqlBase = "rvu_stockholm.mddr1116";
		}

		String sql;
		if (n > 0) {
			sql = "WITH sampled_travelers as (" +
				"SELECT \"UENR\" FROM " + sqlBase +
				" WHERE \"D_A_KL\" IS NOT NULL AND \"D_B_KL\" IS NOT NULL GROUP BY \"UENR\" ORDER BY RANDOM() LIMIT " + n + ")" +
				"SELECT m.* FROM " + sqlBase + " m " +
				"JOIN sampled_travelers s on m.\"UENR\" = s.\"UENR\" WHERE m.\"D_A_KL\" IS NOT NULL AND m.\"D_B_KL\" IS NOT NULL";
		} else {
			sql = "SELECT * FROM " + sqlBase;
		}

		rows = RvuDataset.loadRows(conn, sql);
		return this;
	}

	@Override 
	public MDDRRepository withRandom(Random random) {
		setRandom(random);
		return this;
	}

    public MDDRRepository fromCSV(Path filePath) throws Exception{
        return fromCSV(filePath.toString());
    }

	@Override 
	public MDDRRepository fromCSV(String filePath) throws Exception{
		super.fromCSV(filePath);
		return this;
	}

	// Convenience method - just calls fromCSV since load() auto-detects format
	public MDDRRepository fromAmalCSV(String filePath) throws Exception {
		return fromCSV(filePath);
	}
	public MDDR load () throws Exception {
		return load("default");
	}
	public MDDR load(String scenario) throws Exception {
		logger.info("Loading MDDR from {} with {} rows.", this.getFilePath(), rows.size());
		
		// Check if this is AMAL format (has D_A_S column)
		if (scenario.equals("amal")) {
			if (!rows.isEmpty() && rows.get(0).containsKey("D_A_S")) {
				return MDDR.fromAmalCSV(rows);
			} else {
				logger.warn("AMAL format expected but D_A_S column not found. Falling back to regular MDDR format.");
			}
		} else if (scenario.equals("default")) {
			return new MDDR(rows);
		} 
		throw new IllegalArgumentException("Unknown scenario type: " + scenario);
	}
}
