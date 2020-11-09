/*
 * This code is used to define data structures for localization
 */
package localizationDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocationModel {// store the information of all the neighbors including id and isonline
	// the index of all values are same as Users
	//public List<String> Users = new ArrayList<>();//store the emails of all neighbors
	public List<String> Macs = new ArrayList<>();//store all the online mac
	public List<Double> roomaffinity = new ArrayList<>();
	public List<Double> deviceaffinity = new ArrayList<>();//store device affinity between target device and all neighbors
	public List<Boolean> isOnline = new ArrayList<>();
	public List<String> sensorID = new ArrayList<>();
}
