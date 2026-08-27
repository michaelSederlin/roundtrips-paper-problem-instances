package synthesis;
// Swedish (income | age) distribution for 2024
// Total	 
// Total 16+ years	342.0
// 16–19 years	9.0
// Total 20+ years	359.2
// 20–64 years	401.4
// 20–65 years	401.4
// 65+ years	264.4
// 66+ years	261.0
// 20–24 years	159.7
// 25–29 years	330.4
// 30–34 years	391.1
// 35–39 years	418.8
// 40–44 years	445.7
// 45–49 years	465.2
// 50–54 years	467.9
// 55–59 years	448.5
// 60–64 years	414.8
// 65–69 years	362.1
// 70–74 years	272.7
// 75–79 years	245.8
// 80–84 years	244.1
// 85+ years	236.1


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
	
	
    //  ------------- Fields ------------

	Set<PopulationGroup> uniqueGroups;

	private static final int incomeScale = 30; // kSEK annual income std

	private static final List<Integer> IncomeGroupCutoffs = Arrays.asList(0, 50, 100, 200, 300);

	private static final List<String> ageGroups = Arrays.asList(
			"16-17", "18-24", "25-34", "35-44", "45-54", "55-64", "65-85"
			);

	private static final NavigableMap<Double, Tuple<Integer, Integer>> ageDist = new TreeMap<>(Map.of(
			0.035807055, new Tuple<>(16, 17),        
			0.114746211, new Tuple<>(18, 24),     
			0.304515587, new Tuple<>(25, 34),      
			0.487400787, new Tuple<>(35, 44),       
			0.661458968, new Tuple<>(45, 54),       
			0.830802417, new Tuple<>(55, 64),
			1.0, new Tuple<>(65, 85)              
			));

        
    // Annual median income in kSEK, 2024
    NavigableMap<Integer, Double> ageIncomeMedian = new TreeMap<>(Map.of(
        17, 9.0,    // 17–23
        24, 159.7,  // 24–33 (approx.)
        34, 405.0,  // 34–43 (approx.)
        44, 466.0,  // 44–53 (approx.)
        54, 431.7,  // 54–63 (approx.)
        64, 284.4,  // 64–84 (approx.)
        85, 236.1   // 85+
    ));

    //  ------------- Construction ------------
    public StandardSyntheticPopulation(int n) {
        super(n);
    }
    
    public StandardSyntheticPopulation(int n, long seed) {
        super(n, seed);
        this.uniqueGroups = new HashSet<>();
    }

    //  ------------- Methods ------------
    public Set<PopulationGroup> getUniqueGroups() {
        return this.uniqueGroups;
    }
    
    @Override
    protected void initializePopulation(int n) {
        for (int ii = 0; ii < n; ii++) {
            Tuple<Integer, Integer> ageGroup = ageDist.ceilingEntry(getRandom().nextDouble()).getValue();
            int age = getRandom().nextInt(ageGroup.getA(), ageGroup.getB() + 1);
           
            double income = Math.max(getRandom().nextGaussian(ageIncomeMedian.get(age), incomeScale), 0.0);

            SyntheticPerson newPerson = new SyntheticPerson(ii, age, income);
            addPerson(newPerson);
        }
    }
    
    public StandardSyntheticPopulation identifyUniqueGroups() {
        for (SyntheticPerson person : this.getPopulation()) {
            
            Tuple<Integer, Integer> incomeGroup = null;

            for (int i = 1; i < IncomeGroupCutoffs.size(); i++) {
                int lower = IncomeGroupCutoffs.get(i - 1);
                int upper = IncomeGroupCutoffs.get(i);
                if (person.getIncome() >= lower && person.getIncome() < upper) {
                    incomeGroup = new Tuple<>(lower, upper);
                    break;
                }
            }
            if (incomeGroup == null) {
                incomeGroup = new Tuple<>(IncomeGroupCutoffs.get(IncomeGroupCutoffs.size() - 1), Integer.MAX_VALUE);
            }

            Tuple<Integer, Integer> ageGroup = ageDist.values().stream()
                .filter(tuple -> person.getAge() >= tuple.getA() && person.getAge() <= tuple.getB())
                .findFirst()
                .orElse(new Tuple<>(0, 0));

            uniqueGroups.add(new PopulationGroup(incomeGroup, ageGroup));
        }

        return this;
    }


}