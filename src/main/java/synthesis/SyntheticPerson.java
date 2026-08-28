package synthesis;

public class SyntheticPerson {


	// ------ Fields ------
	// private final String gender; 
	private final Integer age; 
	private final double income; 

	private double homePreference;
	private double workPreference;
	private double educationPreference;
	private double otherPreference;



	// ------ Construction ------

	public SyntheticPerson(Integer idx, Integer age, double income) {
		this.idx = idx; 
		// this.gender = gender; 
		this.age = age; 
		this.income = income; 
	}



	// ------ Setter & Getter ------

	private final Integer idx;
	public Integer getIdx() {
		return idx;
	}

	// public String getGender() {
	// 	return gender;
	// }

	public Integer getAge() {
		return age;
	}

	public double getIncome() {
		return income;
	}

	public double getHomePreference() {
		return homePreference;
	}
	public void setHomePreference(double homePreference) {
		this.homePreference = homePreference;
	}
	public double getWorkPreference() {
		return workPreference;
	}
	public void setWorkPreference(double workPreference) {
		this.workPreference = workPreference;
	}
	public double getEducationPreference() {
		return educationPreference;
	}
	public void setEducationPreference(double educationPreference) {
		this.educationPreference = educationPreference;
	}
	public double getOtherPreference() {
		return otherPreference;
	}
	public void setOtherPreference(double otherPreference) {
		this.otherPreference = otherPreference;
	}
}
