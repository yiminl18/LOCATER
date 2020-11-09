// This code is used for testing 
/*
 * 1. for the format of email, deal with it as a black box: differential them inside functions
 */

package localizationDB;

import service.APtoRoom;
import service.LocationService;
import service.LocationState;
import service.RoomToAP;

import java.sql.Connection;

import dao.LocalDataGeneration;

public class LocalizationDB {
	public static void main(String args[]) {
		Initialization.Initialize();

		//long startTime = System.currentTimeMillis();
		LocationSet Location = LocationPrediction.getLocation("d5d1972a21856639df45695de00a69eb6644eb26","2018-05-01 12:34:11",14);
		//long endTime = System.currentTimeMillis();
		//long duration = (endTime - startTime);

		//System.out.println(duration);
		System.out.println(Location.buildingLocation + " " + Location.regionLocation + " " + Location.roomLocation);

		double probs[] = new double[DBHLocationMap.numLocations];
		int label = 0;
		double max_ = 0.0;
		if (!Location.buildingLocation.equals("null")) {
			System.out.println(Location.probabilities.size() + " " + Location.rooms.size());
			for (int i = 0; i < Location.probabilities.size(); i++) {
				try {
					probs[DBHLocationMap.locationMap.get(
							Integer.parseInt(Location.rooms.get(i)))] = Location.probabilities.get(i);
					System.out.println(Location.rooms.get(i) + " " + Location.probabilities.get(i));
					if ( Location.probabilities.get(i) > max_) {
						label = DBHLocationMap.locationMap.get(
								Integer.parseInt(Location.rooms.get(i)));
						max_ = Location.probabilities.get(i);
					}
				} catch (Exception ignored) {

				}
			}
		}
	}

}
