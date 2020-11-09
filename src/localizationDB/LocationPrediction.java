package localizationDB;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;

import service.APtoRoom;
//import java_cup.runtime.ComplexSymbolFactory.Location;
import service.LocationService;
import service.LocationState;

public class LocationPrediction {
	static double alpha = 0.6;
	public static List<String> candidateRoom = new ArrayList<>();
	//when get online users, store the results of coarse localization, only store for online devices
	public static List<LocationState> neighborInfo = new ArrayList<>();

	public static void catchData(String email, String time) {

	}

	public static LocationSet getLocation(String mac, String time, int LearnTimeLength) {
		LocationSet locations = new LocationSet();

		//catchData(email, time);
		LocationState result = new LocationState();
		//coarse localization
		result = LocationService.queryByHashedMacAddress(mac, time);

		if (!result.inside) {
			locations.buildingLocation = "out";
			locations.regionLocation = "out";
			locations.roomLocation = "out";
		} else {
			locations.buildingLocation = "in";
			locations.regionLocation = result.accessPoint;
			locations.regionProbability = result.insideProb;
			double prob = -1;
			String room = "";
			LocationModel Devices = new LocationModel();
			//Devices(isOnline=true) stores all the online neighbors
			if(!LocalDataMaintenance.searchOffice(mac).equals("null")){//this mac has an office, means it is registered
				Devices = getCandidateMacsList(time, mac);
			}
			else{//this device has not been registered
				Devices = getCandidateMacsOnline(time,mac,result.accessPoint);
			}
			LocationModel roomAffinty = new LocationModel();
			LocationModel deviceAffinity = new LocationModel();
			roomAffinty = AffinityLearning.roomAffinity(mac, result.possibleRooms);


			deviceAffinity = AffinityLearning.deviceAffinityOnline(mac, Devices, time, LearnTimeLength);


			double sum = 0.0;

			for (int i = 0; i < result.possibleRooms.size(); i++) {
				room = result.possibleRooms.get(i);
				locations.rooms.add(room);
				double rAffinity = roomAffinty.roomaffinity.get(i);
				double probability = alpha * rAffinity + (1 - alpha)
						* getRoomProbability(room, time, mac, Devices, deviceAffinity);
				sum += probability;
				locations.probabilities.add(probability);
				//System.out.println(room + " " + probability);
				if (probability > prob) {
					prob = probability;
					locations.roomLocation = room;
				}
			}
			if(sum>0){
				locations.roomProbability = (double) prob * (1 / sum);
				//update location probability distribution
				for(int i=0;i<locations.probabilities.size();i++){
					locations.probabilities.set(i,locations.probabilities.get(i)/sum);
				}
			}else{
				locations.roomProbability = prob;
			}

		}
		return locations;
	}


	public static LocationModel getCandidateMacsList(String time, String mac) {
		//this implementation searches for all the users in social network, and call coarse function to decide its status
		LocationModel Macs = LocalDataMaintenance.getMacs();//read macs from social network

		LocationState result = new LocationState();
		LocationState result_target = new LocationState();
		result_target = LocationService.queryByHashedMacAddress(mac, time);
		String device;
		for (int i = 0; i < Macs.Macs.size(); i++) {
			device = Macs.Macs.get(i);
			Macs.isOnline.add(false);
			if (mac.equals(device))
				continue;
			result = LocationService.queryByHashedMacAddress(device, time);
			if (result == null)
				continue;
			if (!result.inside)
				continue;// offline
			if (!result.possibleRooms.retainAll(result_target.possibleRooms))
				continue;// no overlapping candidate rooms
			Macs.isOnline.set(i, true);
			neighborInfo.add(result);
		}
		return Macs;
	}

	public static LocationModel getCandidateMacsOnline(String time, String mac, String target_sensor_id) {
		//find all the online devices at time t
		LocationModel Devices = new LocationModel();
		String beginTime = AffinityLearning.getClock(time,-2);
		String endTime = AffinityLearning.getClock(time,2);
		Devices = LocalDataMaintenance.ReadObservationInterval(mac,target_sensor_id,beginTime, endTime);
		return Devices;
	}

	public static double getRoomProbability(String room, String time, String email, LocationModel Devices,
											LocationModel deviceAffinity) {
		// for one room, compute the weight from all neighbors
		double prob = 0;
		LocationModel roomAffinty = new LocationModel();
		LocationState result = new LocationState();
		double rAffinity, dAffinity;
		String device;
		double onlineUser = 0;
		for (int i = 0; i < Devices.Macs.size(); i++) {
			if (!Devices.isOnline.get(i))
				continue;// filter out offline users
			device = Devices.Macs.get(i);
			result = LocationService.queryByHashedMacAddress(device, time);
			if (!result.possibleRooms.contains(room))
				continue;// candidate rooms do not contain "room"
			onlineUser++;

			roomAffinty = AffinityLearning.roomAffinity(device, result.possibleRooms);
			rAffinity = roomAffinty.roomaffinity.get(result.possibleRooms.indexOf(room));
			dAffinity = deviceAffinity.deviceaffinity.get(i);

			prob += (rAffinity * dAffinity);
		}
		if (onlineUser == 0)
			return 0;
		return prob / onlineUser;
	}
}

