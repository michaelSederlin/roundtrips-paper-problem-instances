package renate.model.amalTemp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import java.util.HashSet;

import se.vti.utils.misc.Tuple;

public class StandardSyntheticPopulation extends SyntheticPopulation {
    private static final List<Integer> IncomeGroupCutoffs = Arrays.asList(0, 50, 100, 200, 300);
    private static final NavigableMap<Double, Tuple<Integer, Integer>> ageDist = new TreeMap<>(Map.of(
        // (age min, age max): cumulative p
        0.2289922379383734, new Tuple<>(0, 17),        // 0.2289922379383734
        0.28508439440177785, new Tuple<>(18, 24),      // 0.05609215646340445
        0.4199296853106327, new Tuple<>(25, 34),       // 0.13484529090885483
        0.5498832558561378, new Tuple<>(35, 44),       // 0.1299535705455051
        0.6735645711163132, new Tuple<>(45, 54),       // 0.12368131526017544
        0.7938957175365085, new Tuple<>(55, 64),       // 0.12033114642019532
        1.0, new Tuple<>(65, 100)                      // 0.20610428246349144
    ));

    private static final List<String> ageGroups = Arrays.asList(
        "0-17", "18-24", "25-34", "35-44", "45-54", "55-64", "65+"
    );

    private static final Map<String, Double> genderDist = Map.of(
        "male", 0.5,
        "female", 0.5
    );

    // Annual median income in kSEK
    private static final Map<String, NavigableMap<Integer, Tuple<Double, Double>>> ageGenderIncomeMedian = Map.of(  
        "male", new TreeMap<>(Map.of(
            17, new Tuple<>(3.25, 0.0),
            24, new Tuple<>(167.90, 0.0),
            34, new Tuple<>(396.00, 0.0),
            44, new Tuple<>(456.65, 0.0),
            54, new Tuple<>(490.55, 0.0),
            64, new Tuple<>(451.25, 0.0),
            100, new Tuple<>(265.65, 0.0)
        )),
        "female", new TreeMap<>(Map.of(
            17, new Tuple<>(5.70, 0.0),
            24, new Tuple<>(132.80, 0.0),
            34, new Tuple<>(322.90, 0.0),
            44, new Tuple<>(374.40, 0.0),
            54, new Tuple<>(414.50, 0.0),
            64, new Tuple<>(384.35, 0.0),
            100, new Tuple<>(209.80, 0.0)
        )));

    Set<PopulationGroup> uniqueGroups;

    public StandardSyntheticPopulation(int n) {
        super(n);
    }
    
    public StandardSyntheticPopulation(int n, long seed) {
        super(n, seed);
        this.uniqueGroups = new HashSet<>();
    }

    public Set<PopulationGroup> getUniqueGroups() {
        return this.uniqueGroups;
    }

    @Override
    protected void initializePopulation(int n) {
        for (int ii = 0; ii < n; ii++) {
            String gender = getRandom().nextDouble() < genderDist.get("male") ? "male" : "female";
            Tuple<Integer, Integer> ageGroup = ageDist.ceilingEntry(getRandom().nextDouble()).getValue();
            int age = getRandom().nextInt(ageGroup.getA(), ageGroup.getB());
            Tuple<Double, Double> incomeDist = ageGenderIncomeMedian.get(gender).ceilingEntry(age).getValue();
            
            Double income = (incomeDist.getB() == 0.0) ? 
                incomeDist.getA() : 
                Math.max(getRandom().nextGaussian(incomeDist.getA(), incomeDist.getB()), 0.0);

            addRow(ii, gender, age, income);
        }
    }

    public StandardSyntheticPopulation assignGroups() {
        for (Person person : this.getPopulation()) {
            
            Tuple<Integer, Integer> incomeGroup = null;

            for (int i = 1; i < IncomeGroupCutoffs.size(); i++) {
                int lower = IncomeGroupCutoffs.get(i - 1);
                int upper = IncomeGroupCutoffs.get(i);
                if (person.income() >= lower && person.income() < upper) {
                    incomeGroup = new Tuple<>(lower, upper);
                    break;
                }
            }
            if (incomeGroup == null) {
                incomeGroup = new Tuple<>(IncomeGroupCutoffs.get(IncomeGroupCutoffs.size() - 1), Integer.MAX_VALUE);
            }

            Tuple<Integer, Integer> ageGroup = ageDist.values().stream()
                .filter(tuple -> person.age() >= tuple.getA() && person.age() <= tuple.getB())
                .findFirst()
                .orElse(new Tuple<>(0, 0));

            uniqueGroups.add(new PopulationGroup(incomeGroup, ageGroup, person.gender()));
        }

        return this;
    }


}