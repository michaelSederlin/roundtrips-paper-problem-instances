package renate.model.amalTemp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public abstract class SyntheticPopulation {
    private final Random r;
    private final List<Person> population;
    // public record Person(String gender,  Integer ageMin,  Integer ageMax, double income) {}
    public class Person {
        private final Integer idx;
        private final String gender; 
        private final Integer age; 
        private final double income; 

        public Person(Integer idx, String gender, Integer age, double income) {
            this.idx = idx; 
            this.gender = gender; 
            this.age = age; 
            this.income = income; 
        }
        public Integer idx() {return this.idx;}
        public String gender() {return this.gender;}
        public Integer age() {return this.age;}
        public double income() {return this.income;}
    }
    
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
    
    protected abstract void initializePopulation(int n);

    public List<Person> getPopulation() {
        return population;
    }

    protected void addRow(Integer idx, String gender, Integer age, double income) {
        this.population.add(new Person(idx, gender, age, income));
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
    
    public void writePopulation(String path) {
        StringBuilder sb = new StringBuilder();
        sb.append("idx,gender,age,income\n");
        for (Person row : population) {
            sb.append(String.format(Locale.US, "%d,%s,%d,%f\n", row.idx(), row.gender(), row.age(), row.income()));
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
