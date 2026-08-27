package synthesis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public abstract class SyntheticPopulation {
	
    // ----- Fields -------
	
    private final Random r;
    private final List<SyntheticPerson> population;

   
    
    // ----- Construction -------
    
    public SyntheticPopulation(int n) {
        this.r = new Random();
        this.population = new ArrayList<>(n);
        initializePopulation(n);
    }

    SyntheticPopulation(int n, long seed) {
        this.r = new Random(seed);
        this.population = new ArrayList<>(n);
        initializePopulation(n);
    }
    
    // ----- Methods -------
    
    protected abstract void initializePopulation(int n);

    public List<SyntheticPerson> getPopulation() {
        return population;
    }

    protected void addPerson(SyntheticPerson person) {
        this.population.add(person);
    }

    protected Random getRandom() {
        return this.r;
    }

    public int size() {
        return this.population.size();
    }

    public void writePopulation(java.nio.file.Path path) {
        writePopulation(path.toString());
    }
    
//    public void writePopulation(String path) {
//        StringBuilder sb = new StringBuilder();
//        sb.append("idx,gender,age,income\n");
//        for (SyntheticPerson row : population) {
//            sb.append(String.format(Locale.US, "%d,%s,%d,%f\n", row.getIdx(), row.getGender(), row.getAge(), row.getIncome()));
//        }
//        try (var writer = Files.newBufferedWriter(Paths.get(path))) {
//            writer.write(sb.toString());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
    
    
    public void writePopulation(String path) {
    	StringBuilder sb = new StringBuilder();
    	sb.append("idx,age,income,home,work,education,other\n");
    	for (SyntheticPerson person : population) {
    		sb.append(String.format(
    				Locale.US,
    				"%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f\n",
    				person.getIdx(),
    				// person.getGender(),
    				person.getAge(),
    				person.getIncome(),
    				person.getHomePreference(),
    				person.getWorkPreference(),
    				person.getEducationPreference(),
    				person.getOtherPreference()
    				));
    	}
    	try (var writer = Files.newBufferedWriter(Paths.get(path))) {
    		writer.write(sb.toString());
    	} catch (IOException e) {
    		e.printStackTrace();
    	}
    }

    public void printPopulation() {
        for (var person : this.population) {
            System.out.println(person);
        }
    }    
}
