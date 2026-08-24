package renate.model.amalTemp;

import se.vti.utils.misc.Tuple;

public class PopulationGroup {
        Tuple<Integer, Integer> incomeGroup;
        Tuple<Integer, Integer> ageGroup;
        String gender; 
        String key; 

        public PopulationGroup(Tuple<Integer, Integer> incomeGroup, Tuple<Integer, Integer> ageGroup, String gender) {
            this.incomeGroup = incomeGroup;
            this.ageGroup = ageGroup; 
            this.gender = gender; 
            this.key = incomeGroup + "-" + ageGroup + "-" + gender;
        }

        public String getKey() {
            return this.key;
        }

        public Tuple<Integer, Integer> getIncomeGroup() {
            return this.incomeGroup;
        }

        public Tuple<Integer, Integer> getAgeGroup() {
            return this.ageGroup;
        }

        public boolean inGroup(SyntheticPopulation.Person person) {
            boolean inIncomeGroup = (person.income() >= this.incomeGroup.getA() && person.income() < this.incomeGroup.getB());
            boolean inAgeGroup = (person.age() >= this.ageGroup.getA() && person.age() <= this.ageGroup.getB());
            boolean inGender = person.gender().equals(this.gender);
            return inIncomeGroup && inAgeGroup && inGender;
        }   

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PopulationGroup that = (PopulationGroup) o;
            return incomeGroup.equals(that.incomeGroup) &&
                   ageGroup.equals(that.ageGroup) &&
                   gender.equals(that.gender);
        }

        @Override
        public int hashCode() {
            int result = incomeGroup.hashCode();
            result = 31 * result + ageGroup.hashCode();
            result = 31 * result + gender.hashCode();
            return result;
        }

        @Override 
        public String toString() {
            return "PopulationGroup{" +
                    "incomeGroup=" + incomeGroup +
                    ", ageGroup=" + ageGroup +
                    ", gender='" + gender + '\'' +
                    '}';
        }
    }
