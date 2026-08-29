package synthesis;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.NavigableMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import se.vti.utils.misc.Tuple;

public class StandardSyntheticPopulation extends SyntheticPopulation {

    public class IncomeConfig {
        public List<Integer> incomeGroupCutoffs;
        public List<String> ageGroups;
        public NavigableMap<Double, Tuple<Integer, Integer>> ageDist;
        public NavigableMap<Integer, Double> ageIncomeMedian;
    }

    private static final int incomeScale = 30;

    private final IncomeConfig config;

    Set<PopulationGroup> uniqueGroups;

    public StandardSyntheticPopulation(Path populationConfigs, int n) {
        super(n);
        this.config = loadConfig(populationConfigs);
        this.uniqueGroups = new HashSet<>();
    }

    public StandardSyntheticPopulation(Path populationConfigs, int n, long seed) {
        super(n, seed);
        this.config = loadConfig(populationConfigs);
        this.uniqueGroups = new HashSet<>();
    }

    private IncomeConfig loadConfig(Path populationConfigs) {
        try {
            return new ObjectMapper().readValue(
                getClass().getResourceAsStream(populationConfigs.toString()),
                IncomeConfig.class
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load income configuration", e);
        }
    }

    public Set<PopulationGroup> getUniqueGroups() {
        return this.uniqueGroups;
    }

    @Override
    protected void initializePopulation(int n) {
        for (int ii = 0; ii < n; ii++) {

            Tuple<Integer, Integer> ageGroup =
                config.ageDist.ceilingEntry(getRandom().nextDouble()).getValue();

            int age = getRandom().nextInt(
                ageGroup.getA(),
                ageGroup.getB() + 1
            );

            double medianIncome =
                config.ageIncomeMedian.ceilingEntry(age).getValue();

            double income = Math.max(
                getRandom().nextGaussian(medianIncome, incomeScale),
                0.0
            );

            SyntheticPerson newPerson =
                new SyntheticPerson(ii, age, income);

            addPerson(newPerson);
        }
    }

    public StandardSyntheticPopulation identifyUniqueGroups() {

        for (SyntheticPerson person : this.getPopulation()) {

            Tuple<Integer, Integer> incomeGroup = null;

            for (int i = 1; i < config.incomeGroupCutoffs.size(); i++) {
                int lower = config.incomeGroupCutoffs.get(i - 1);
                int upper = config.incomeGroupCutoffs.get(i);

                if (person.getIncome() >= lower &&
                    person.getIncome() < upper) {

                    incomeGroup = new Tuple<>(lower, upper);
                    break;
                }
            }

            if (incomeGroup == null) {
                incomeGroup = new Tuple<>(
                    config.incomeGroupCutoffs.get(
                        config.incomeGroupCutoffs.size() - 1
                    ),
                    Integer.MAX_VALUE
                );
            }

            Tuple<Integer, Integer> ageGroup =
                config.ageDist.values().stream()
                    .filter(tuple ->
                        person.getAge() >= tuple.getA() &&
                        person.getAge() <= tuple.getB())
                    .findFirst()
                    .orElse(new Tuple<>(0, 0));

            uniqueGroups.add(
                new PopulationGroup(incomeGroup, ageGroup)
            );
        }

        return this;
    }
}
