package disaggregate.model;

import disaggregate.rvu.Trip;
import common.Activity; 

import java.util.Map;

public class Person {
    public final String[] VARIABLES = {"idx", "name", "age", "income", "homeZone", "gender"};

    private String segment;
    private Integer age; 
    private Long income;
    private String homeZone;
    private Integer gender;
    private int idx;
    private String name; 
    private Trip trip;

    public Person(int idx) {
        this(idx, null, null, null, null);
    }

    public Person(int idx, String homeZone) {
        this(idx, homeZone, null, null, null);
    }

    public Person(int idx, Integer age) {
        this(idx, null, age, null, null);
    }

    public Person(int idx, String homeZone, Integer age) {
        this(idx, homeZone, age, null, null);
    }

    public Person(int idx, String homeZone, Integer age, Long inc) {
        this(idx, homeZone, age, inc, null);
    }

    public Person(Integer idx, Integer age, Long inc, Integer gender) {
        this(idx, null, age, inc, gender);
    }

    public Person(int idx, String homeZone, Integer age, Long inc, Integer gender)  {
        this.idx = idx;
        this.age = age; 
        this.income = inc; 
        this.homeZone = homeZone;
        this.gender = gender;
    }

    public Person setName(String name) { this.name= name; return this; }
    public String getName() { return this.name; }

    @SuppressWarnings("unused")
    private void setAge(int age) {
        if (age < 0) throw new IllegalArgumentException("Age cannot be negative");
        this.age = age;
    }

    @SuppressWarnings("unused")
    private void setIncome(long inc) {
        if (inc < 0) throw new IllegalArgumentException("Income cannot be negative");
        this.income = inc;
    }
    public Long getIncome() {
        return this.income; 
    }
    
    public String getHomeZone() {return this.homeZone;}
    public void setHomeZone(String homeZone) {this.homeZone = homeZone;}
    
    public Integer getAge() {return this.age;}
    public void setAge(Integer age) { this.age = age; }
    
    public Integer getGender() { return this.gender; }
    @SuppressWarnings("unused")
    private void setGender(int gender) {
        if (gender < 0) throw new IllegalArgumentException("Gender cannot be negative");
        this.gender = gender;
    }

    public int getIdx() {
        return this.idx;
    }

    public String getAttributes() {return "Income: " + this.income + " Age: " + this.age;    }

    public Person assignPersonTrip(Trip trip) {
        this.trip = trip;
        return this;
    }

    public Trip getTrip() {
        return this.trip;
    }
    public String getSegment() { return this.segment; }
    public void setSegment(String segment) { this.segment = segment; }
    public boolean hasTrip() {
        return (this.trip != null);
    }

    public void validate() throws IllegalArgumentException {
        if (homeZone == null) throw new IllegalArgumentException("homeZone cannot be null");
        if (age == null) throw new IllegalArgumentException("age cannot be null");
        if (income == null) throw new IllegalArgumentException("income cannot be null");
        if (gender == null) throw new IllegalArgumentException("gender cannot be null");

        if (age < 0) throw new IllegalArgumentException("age cannot be negative");
        if (income < 0) throw new IllegalArgumentException("income cannot be negative");
        if (!(gender == 1 || gender == 2)) throw new IllegalArgumentException("gender must be either 1 or 2");

        if (hasTrip()) {
//            if (trip.getTotalTravelTime_h() > 4.0) throw new IllegalArgumentException("ttt must be less than 4 hours");
//            if (trip.getStopCount() > 10) throw new IllegalArgumentException("Total stop count must be less than 10");
            for (Map.Entry<Activity, Double> entry : trip.getActivityTimes_h().entrySet()) {
                if (entry.getValue() < 0) {
                    throw new IllegalArgumentException(String.format("Activity time for activity %s cannot be negative", entry.getKey().toString()));
                }
            }
        }
    }
}