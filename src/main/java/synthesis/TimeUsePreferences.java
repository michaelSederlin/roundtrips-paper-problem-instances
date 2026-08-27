package synthesis;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.w3c.dom.NodeList;

import jj2000.j2k.NotImplementedError;
import se.vti.roundtrips.examples.activityExpandedGridNetwork.Activity;
import common.XMLReader;
import se.vti.utils.misc.Tuple;

public class TimeUsePreferences {

	// ---------- Fields ----------------

	private final Activity[] activities = Activity.values();
	// private final Map<String, Map<Activity, Double>> genderPrefs;
	private final Map<Tuple<Integer, Integer>, Map<Activity, Double>> agePrefs;
	private final Map<Tuple<Integer, Integer>, Map<Activity, Double>> incomePrefs;
	private final Random rng;
	private Double scale;
	private boolean randomMixing = false;



	// ---------- Construction ----------------

	public TimeUsePreferences(String fp, long seed) {
		this(Path.of(fp), new Random(seed));
	}

	public TimeUsePreferences(String fp, Random rng) {
		this(Path.of(fp), rng);
	}

	public TimeUsePreferences(Path fp, long seed) { 
		this(fp, new Random(seed)); 
	}

	public TimeUsePreferences(Path fp, Random rng) {
		XMLReader xml = new XMLReader(fp.toString());
		// NodeList genderNodes = xml.nodes("Preferences/GenderPreferences/*");

		this.scale = null;

		this.rng = rng;

		// this.genderPrefs = new HashMap<>();
		this.agePrefs = new HashMap<>();
		this.incomePrefs = new HashMap<>();

		// for (int i = 0; i < genderNodes.getLength(); i++) {
		// 	var node = genderNodes.item(i);
		// 	String gender = node.getAttributes().getNamedItem("type").getNodeValue();
		// 	Map<Activity, Double> prefs = new HashMap<>();
		// 	NodeList activityNodes = node.getChildNodes();
		// 	for (int j = 0; j < activityNodes.getLength(); j++) {
		// 		var actNode = activityNodes.item(j);
		// 		if (actNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
		// 			String actName = actNode.getAttributes().getNamedItem("name").getNodeValue();
		// 			double value = Double.parseDouble(actNode.getTextContent());
		// 			prefs.put(Activity.valueOf(actName.toUpperCase()), value);
		// 		}
		// 	}
		// 	this.genderPrefs.put(gender, prefs);
		// }

		NodeList ageNodes = xml.nodes("Preferences/AgePreferences/AgeGroup");
		for (int i = 0; i < ageNodes.getLength(); i++) {
			var node = ageNodes.item(i);
			int minAge = Integer.parseInt(node.getAttributes().getNamedItem("minAge").getNodeValue());
			int maxAge = Integer.parseInt(node.getAttributes().getNamedItem("maxAge").getNodeValue());
			Map<Activity, Double> prefs = new HashMap<>();
			NodeList activityNodes = node.getChildNodes();
			for (int j = 0; j < activityNodes.getLength(); j++) {
				var actNode = activityNodes.item(j);
				if (actNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
					String actName = actNode.getAttributes().getNamedItem("name").getNodeValue();
					double value = Double.parseDouble(actNode.getTextContent());
					prefs.put(Activity.valueOf(actName.toUpperCase()), value);
				}
			}
			this.agePrefs.put(new Tuple<>(minAge, maxAge), prefs);
		}

		NodeList incomeNodes = xml.nodes("Preferences/IncomePreferences/IncomeGroup");
		for (int i = 0; i < incomeNodes.getLength(); i++) {
			var node = incomeNodes.item(i);
			int minInc = Integer.parseInt(node.getAttributes().getNamedItem("minIncome").getNodeValue());
			int maxInc = Integer.parseInt(node.getAttributes().getNamedItem("maxIncome").getNodeValue());
			Map<Activity, Double> prefs = new HashMap<>();
			NodeList activityNodes = node.getChildNodes();
			for (int j = 0; j < activityNodes.getLength(); j++) {
				var actNode = activityNodes.item(j);
				if (actNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
					String actName = actNode.getAttributes().getNamedItem("name").getNodeValue();
					double value = Double.parseDouble(actNode.getTextContent());
					prefs.put(Activity.valueOf(actName.toUpperCase()), value);
				}
			}
			this.incomePrefs.put(new Tuple<>(minInc, maxInc), prefs);
		}
	}



	// ---------- Setter & Getter ----------------

	// public Map<String, Map<Activity, Double>> getGenderPrefs() {
	// 	return this.genderPrefs;
	// }
	public Map<Tuple<Integer, Integer>, Map<Activity, Double>> getAgePrefs() {
		return this.agePrefs;
	}
	public Map<Tuple<Integer, Integer>, Map<Activity, Double>> getIncomePrefs() {
		return this.incomePrefs;
	}

	public TimeUsePreferences setUniformRandomMixing(boolean val) {
		this.randomMixing = val;
		return this;
	}



	// ---------- Methods ----------------

	public TimeUsePreferences withScale(Double scale) {
		this.scale = scale;
		return this;
	}

	public void computeAndSetPreferences(SyntheticPopulation population) {
		for (SyntheticPerson person : population.getPopulation()) {
			double[] mixingWeights = new double[3];
			if (randomMixing) {
				double sum = 0.0;
				for (int ii = 0; ii < mixingWeights.length; ii++) {
					mixingWeights[ii] = rng.nextDouble();
					sum += mixingWeights[ii];
				}
				for (int ii = 0; ii < mixingWeights.length; ii++) mixingWeights[ii] = mixingWeights[ii] / sum;
			} else {
				for (int ii = 0; ii < mixingWeights.length; ii++) mixingWeights[ii] = (double) 1/mixingWeights.length;
			}

			// Map<Activity, Double> genderPref = this.genderPrefs.get(person.getGender());

			Map<Activity,Double> agePref = new HashMap<>();
			for (Tuple<Integer, Integer> ageRange : this.agePrefs.keySet()) {
				if (person.getAge() >= ageRange.getA() && person.getAge() <= ageRange.getB()) {
					agePref = this.agePrefs.get(ageRange);
				}
			}
			Map<Activity,Double> incPref = new HashMap<>();
			for (Tuple<Integer, Integer> incRange : this.incomePrefs.keySet()) {
				if (person.getIncome() >= incRange.getA() && person.getIncome() <= incRange.getB()) {
					incPref = this.incomePrefs.get(incRange);
				}
			}

			Map<Activity, Double> personPrefs = new HashMap<>();
			Double res;
			for (Activity act : this.activities) {
				res =  mixingWeights[0] * agePref.get(act) 
						+ mixingWeights[1] * incPref.get(act);

				if (this.scale != null) {
					throw new NotImplementedError();
					// res += rng.nextGaussian(0, this.scale);
				}

				//personPrefs.put(act, res / 3.0); // Franz 05_2026: I think this was a bug? Normalization already happens in mixingWeights.
				personPrefs.put(act, res);
			}

			double total = personPrefs.values().stream().mapToDouble(Double::doubleValue).sum();

			// NOTE!!! Changed this to do scaling. Didn't do anything before. /M
			if (total > 24) {
				for (Activity act : personPrefs.keySet()) {
					personPrefs.put(act, personPrefs.get(act) * 24.0 / total);
				}
			}

			person.setHomePreference(personPrefs.get(Activity.HOME));
			person.setWorkPreference(personPrefs.get(Activity.WORK));
			person.setEducationPreference(personPrefs.get(Activity.EDUCATION));
			person.setOtherPreference(personPrefs.get(Activity.OTHER));

		}

	}


	public void moveWorkHoursOfElderlyToHome(SyntheticPopulation population) {
		for (SyntheticPerson person : population.getPopulation()) {
			if (person.getAge() >= 65) {
				double workPref = person.getWorkPreference();

				if (workPref > 0.0) {
					double currentHomePref = person.getHomePreference();

					// Transfer all work hours to home
					person.setHomePreference(currentHomePref + workPref);
					person.setWorkPreference(0.0);
				}
			}
		}
	}



}

