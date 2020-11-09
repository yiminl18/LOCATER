package localizationDB;

import java.util.ArrayList;
import java.util.List;

public class LocationSet {
	public String buildingLocation;
	public String regionLocation;
	public double regionProbability;
	public String roomLocation;
	public double roomProbability;
	public List<String> rooms = new ArrayList<>();
	public List<Double> probabilities = new ArrayList<>();
}
