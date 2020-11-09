package localizationDB;

import service.LocationService;
import service.LocationState;

//this code is to provide API for enrichmentDB

public class enrichment {

	public static LocationSet getBuildingLocation(String email, String time) {
		LocationSet location = new LocationSet();
		AffinityLearning.loadOfficeMetadata();
		LocationPrediction.catchData(email, time);
		LocationState result = new LocationState();
		result = LocationService.newQueryTimestampByEmail(email, time);
		if (!result.inside) {
			location.buildingLocation = "out";
		} else {
			location.buildingLocation = "in";
		}
		return location;
	}

	public static LocationSet getRegionLocation(String email, String time) {
		LocationSet location = new LocationSet();
		AffinityLearning.loadOfficeMetadata();
		LocationPrediction.catchData(email, time);
		LocationState result = new LocationState();
		result = LocationService.newQueryTimestampByEmail(email, time);
		if (!result.inside) {
			location.regionLocation = "out";
		} else {
			location.regionLocation = result.accessPoint;
		}
		location.regionProbability = result.insideProb;
		return location;
	}

	public static LocationSet getRoomLocation(String email, String time, int LearnTimeLength) {
		return LocationPrediction.getLocation(email, time, LearnTimeLength);
	}

}
