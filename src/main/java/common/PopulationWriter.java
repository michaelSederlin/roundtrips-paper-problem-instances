package common;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import disaggregate.model.Activity;
import disaggregate.model.Person;
import disaggregate.rvu.Trip;
// import disaggregate.model.SurveyResponse;
import se.vti.utils.misc.metropolishastings.MHToFileLogger;

public class PopulationWriter extends MHToFileLogger<Person>{
    private final List<Person> population;

    public PopulationWriter(List<Person> population, Path logFileName) {
        this(population, logFileName.toString());
    }

    public PopulationWriter(List<Person> population, String logFileName) {
        super(1, logFileName);
        this.population = population;
    }

    @Override
    public String createHeaderLine() {
        // TODO: Should be made more flexible - Or contain all observations and then write null values to missing ones (probably more realistic option)
        StringBuilder sb = new StringBuilder();
        sb.append("idx\tname\tage\tinc\tgender\thomeZone\tttt\tnStops\tlegs\tactivity_durations_h\tsegment");
        return sb.toString();
    }
    
    @Override
    public String createDataLine(Person person, double lw) {
        StringBuilder sb = new StringBuilder();
        sb.append(person.getIdx()).append("\t");
        sb.append(person.getName()).append("\t");
        sb.append(person.getAge()).append("\t");
        sb.append(person.getIncome()).append("\t");
        sb.append(person.getGender()).append("\t");
        sb.append(person.getHomeZone());

        if (person.hasTrip()) {
            Trip trip = person.getTrip();
            String legsStr = "[";
            var legs = trip.getLegs();
            for (int ii = 0; ii < legs.size(); ii++) { 
                legsStr += "{" + legs.get(ii) + "}";
                legsStr += (ii < legs.size() - 1) ? ", " : "";
            }
            legsStr += "]";

            // Create Python dict string for activity durations
            Map<Activity, Double> activityTimes = trip.getActivityTimes_h();
            StringBuilder actDurStr = new StringBuilder("{");
            int count = 0;
            for (Map.Entry<Activity, Double> entry : activityTimes.entrySet()) {
                if (count > 0) actDurStr.append(", ");
                actDurStr.append("'").append(entry.getKey().name()).append("': ");
                actDurStr.append(entry.getValue());
                count++;
            }
            actDurStr.append("}");

            sb.append("\t")
                .append(trip.getTotalTravelTime_h())
                .append("\t")
                .append(trip.getStopCount())
                .append("\t")
                .append(legsStr)
                .append("\t")
                .append(actDurStr.toString());
        } else {
            sb.append("\tnull\tnull\tnull\t{}");
        }

        if (person.getSegment() != null) {
            sb.append("\t").append(person.getSegment());
        } else {
            sb.append("\tnull");
        }

        return sb.toString();
    }

    public void write() {
        this.start();
        for (Person person : population) {
            this.processState(person, 0.0);
        }
        this.end();
    }    
}
