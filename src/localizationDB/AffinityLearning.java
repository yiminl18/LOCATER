/*
 * This code first read observations from local database and then learn affinity;
 */
package localizationDB;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import service.APtoRoom;
import service.LocationService;
import service.LocationState;
import service.ResultEvaluation;

public class AffinityLearning {

	public static List<Device> devices = new ArrayList<>();
	public static List<RawDevice> rawdevices = new ArrayList<>();
	public static List<Double> Daffinity = new ArrayList<>();//store temp device affinity

	static String serverDatabase = "tippersdb_restored";
	static String localDatabase = "enrichdb";
	static Map<String, String> pair = new HashMap<String, String>();
	static APtoRoom aPtoRoom = new APtoRoom();

	static int length = 14;
	static double coefficient = 10.0;

	static class OBSERVATION {// store raw observation
		String timeStamp;
		String sensorID;
	}

	static class Device { // store observation for each device, and day by day
		//store the timeStamp of query and the mac of this device
		String timeStamp;
		String mac;
		List<OBSERVATION> oneday = new ArrayList<>();//for one day, we only store one tuple which is  closest to time t
	}

	static class RawDevice{
		// store ALL observation for each device, NOT day by day
		String timeStamp;
		String mac;
		List<OBSERVATION> observations = new ArrayList<>();
	}
	/*
	We store historical observation data in three levels:
	1. device: we have many devices and we store data device by device; ->devices, object of Device
	2. for one day, we only store one tuple which is closest to time t
	3. observation: it is the basic unit of data structure. ->object of OBSERVATION
	*** in the devices, the devices.get(0) is the target device, neighbors are from 1;
	*/

	/*
	1. we first read data from tippers and store in RawDevices;
	2. and then we filter data and store day by day in Devices;
	 */

	/*
	Note that: LocationModel deviceAffinity stores all device affinity between target device and all neighbors.
	So for those offline devices, just set the affinity to 0, so that we can keep the index same as LocationModel Users.
	 */



	public static LocationModel deviceAffinityOnline(String mac, LocationModel Devices, String time, int learnDays) {
		// return the affinity of email to all the users in Users
		LocationModel deviceAffinity = new LocationModel();
		String beginTime = getDay(time, -learnDays);
		String endTime = time;
		long sTime = System.currentTimeMillis();
		ReadObservationFromTipperOneUser(mac, beginTime, endTime);//read observation of "mac" into memory
		for (int i = 0; i < Devices.Macs.size(); i++) {
			if (!Devices.isOnline.get(i))
				continue;
			//read observation of all online neighbors into memory
			ReadObservationFromTipperOneUser(Devices.Macs.get(i), beginTime, endTime);
		}

		FilterObservation(time, learnDays);
		learnDeviceAffinity(Devices,learnDays);
		for (int i = 0; i < Devices.Macs.size(); i++) {
			deviceAffinity.deviceaffinity.add(Daffinity.get(i));
		}
		return deviceAffinity;
	}

	public static LocationModel roomAffinity(String mac, List<String> locations) {
		String office = LocalDataMaintenance.searchOffice(mac);
		String room;
		LocationModel affinity = new LocationModel();
		double value = 0;
		int meta_index = 0;//this is the index of room in space metadata;
		//System.out.println(office);
		for (int i = 0; i < locations.size(); i++) {
			room = locations.get(i);
			meta_index = LocalDataMaintenance.possibleRooms.indexOf(room);
			//System.out.println(room + " " + meta_index);
			if (room.equals(office)) {
				affinity.roomaffinity.add(10*coefficient*2);
				value += 10*coefficient*2;
			}else if(meta_index==-1){
				value += 1.0;
				affinity.roomaffinity.add(1.0);
			}
			else {
				Double temp = (double)(10-LocalDataMaintenance.space_metadata.get(meta_index).importance)*coefficient;
				affinity.roomaffinity.add(temp);
				value += temp;
			}
		}
		if (value > 0) {
			for (int i = 0; i < affinity.roomaffinity.size(); i++) {
				affinity.roomaffinity.set(i, affinity.roomaffinity.get(i) / value);
			}
		}
		return affinity;
	}


	public static void loadOfficeMetadata() {
		Connect connectLocal = new Connect("local", localDatabase);// OBSERVATION
		Connection localConnection = connectLocal.getConnection();

		ResultSet rs;
		try {
			Statement stmtlocal = localConnection.createStatement();
			rs = stmtlocal.executeQuery("select * from office");
			while (rs.next()) {
				pair.put(rs.getString(1), rs.getString(2));
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectLocal.close();
	}

	public static void learnDeviceAffinity(LocationModel Devices,int learnDays){
		int c=1;//c is the neighbor id in devices data structure
		//System.out.println(Devices.Macs.size());
		for(int i=0;i<Devices.Macs.size();i++){
			Double affinity = 0.0;
			if (!Devices.isOnline.get(i)) {//for offline user
				Daffinity.add(0.0);
			}
			else{//for online user
				//System.out.println(Devices.Macs.get(i));
				for(int j=0;j<learnDays;j++){//scan observation data for each day
					if(devices.get(0).oneday.get(j).timeStamp.equals("null") || devices.get(c).oneday.get(j).timeStamp.equals("null")){
						continue;
					}
					else{
						//System.out.println(devices.get(c).oneday.get(j).timeStamp);
						affinity += DeviceAffinityOneDay(c,j);
						//System.out.println(affinity);
					}
				}
				c++;
			}
			Daffinity.add(affinity);
		}
		Normalization();
	}

	public static void Normalization(){
		//to normalize the Daffinity
		Double sum=0.0;
		for(int i=0;i<Daffinity.size();i++){
			sum+=Daffinity.get(i);
		}
		if(sum==0.0){
			return;
		}
		for(int i=0;i<Daffinity.size();i++){
			Daffinity.set(i,Daffinity.get(i)/sum);
		}
	}

	public static Double DeviceAffinityOneDay(int neighborID, int dateID){
		String targetTimeStamp = devices.get(0).oneday.get(dateID).timeStamp;
		String neighborTimeStamp = devices.get(neighborID).oneday.get(dateID).timeStamp;
		String targetMac = devices.get(0).mac;
		String neighborMac = devices.get(neighborID).mac;
		String targetSensor = devices.get(0).oneday.get(dateID).sensorID;
		String neighborSensor = devices.get(neighborID).oneday.get(dateID).sensorID;
		LocationModel targetRoomAffinity = new LocationModel();
		LocationModel neighborRoomAffinity = new LocationModel();

		//System.out.println(targetMac + " " + targetSensor + " " + neighborMac + " " + neighborSensor);

		aPtoRoom.load();
		LocationState resultTarget = new LocationState();
		resultTarget.possibleRooms = aPtoRoom.find(targetSensor);
		LocationState resultNeighbor = new LocationState();
		resultNeighbor.possibleRooms = aPtoRoom.find(neighborSensor);
		List<String> intersection = resultTarget.possibleRooms;
		boolean flag = intersection.retainAll(resultNeighbor.possibleRooms);
		if(!flag){
			return 0.0;//no intersection, return 0
		}
		targetRoomAffinity = roomAffinity(targetMac,resultTarget.possibleRooms);
		neighborRoomAffinity = roomAffinity(neighborMac,resultNeighbor.possibleRooms);
		//ihe: check size of targetRoomAffinity and neighborRoomAffinity, should be same
		Double deviceAffinity = 0.0;

		for(int i = 0;i<intersection.size();i++){
			deviceAffinity +=targetRoomAffinity.roomaffinity.get(resultTarget.possibleRooms.indexOf(intersection.get(i)));
			deviceAffinity +=neighborRoomAffinity.roomaffinity.get(resultNeighbor.possibleRooms.indexOf(intersection.get(i)));
		}
		return deviceAffinity;
	}

	public static void ReadObservationFromTipperOneUser(String mac, String beginTime, String endTime) {
		ResultSet rs;
		Connect connectServer = new Connect("server", serverDatabase);// OBSERVATION
		Connection serverConnection = connectServer.getConnection();
		RawDevice rawDevice = new RawDevice();
		rawDevice.mac = mac;
		rawDevice.timeStamp = endTime;
		try {
			Statement stmtlocal = serverConnection.createStatement();
			String sql = String.format(
					"select timeStamp, sensor_id from OBSERVATION_CLEAN where payload='%s' and timeStamp >= '%s' and timeStamp <= '%s'",
					mac, beginTime, endTime);
			rs = stmtlocal.executeQuery(sql);
			while (rs.next()) {
				OBSERVATION ob = new OBSERVATION();//ihe: test here to move ob out of the loop
				ob.timeStamp = rs.getString(1);
				ob.sensorID = rs.getString(2);
				rawDevice.observations.add(ob);
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		rawdevices.add(rawDevice);
		connectServer.close();
	}

	public static void FilterObservation(String timeStamp, int learnDays){
		//transform data from rawdevices to devices: filter out those data are not in time t;
		//System.out.println(rawdevices.size());
		//System.out.println(rawdevices.get(0).mac + " " + rawdevices.get(0).timeStamp + " " + rawdevices.get(0).observations.size());
		for(int i=0;i<rawdevices.size();i++){
			Device device = new Device();
			device.mac = rawdevices.get(i).mac;
			device.timeStamp = rawdevices.get(i).timeStamp;
			int c = 0;//c is the pointer to scan all observations for device i
			int p=0;
			//System.out.println("size " + rawdevices.get(i).observations.size());
			for(int k=0;k<learnDays;k++){
				//scan for each days
				//System.out.println("start");
				int flag = 0;
				//System.out.println("hey "+c+ " " +rawdevices.get(i).observations.size());
				if(c>=rawdevices.get(i).observations.size()-1){
					break;
				}
				//System.out.println("XXXX");
				OBSERVATION ob = new OBSERVATION();
				String standardtime = getDay(timeStamp,-learnDays+k);
				String rawTime = rawdevices.get(i).observations.get(c).timeStamp;
				String endDay = getDay(standardtime,1).substring(0,11)+"00:00:00";//the end of day
				//System.out.println(standardtime + " " + rawTime + " " + endDay);
				if(Difference(rawTime,endDay)<0){
					//rawTime is not in this day, then go to next day
					ob.sensorID="null";
					ob.timeStamp="null";
					device.oneday.add(ob);
					//System.out.println("p1");
					continue;
				}
				//if codes go here, then rawTime is in or after this day, we try to find effective time;
				while(Difference(rawdevices.get(i).observations.get(c).timeStamp,standardtime)>20){
					//search for all the timeStamps before standardtime and also non-effective
					//System.out.println(rawdevices.get(i).observations.get(c).timeStamp);
					c++;
					if(c>=rawdevices.get(i).observations.size()-1) {
						ob.sensorID = "null";
						ob.timeStamp = "null";
						device.oneday.add(ob);
						//System.out.println("p2");
						break;
					}
				}
				if(c>=rawdevices.get(i).observations.size()-1) continue;
				//System.out.println("here");
				//if codes go here, check if the first timeStamp is effective
				int diff = Difference(rawdevices.get(i).observations.get(c).timeStamp,standardtime);
				if(diff<20 && diff>-20){//effective
					ob.sensorID = rawdevices.get(i).observations.get(c).sensorID;
					ob.timeStamp = rawdevices.get(i).observations.get(c).timeStamp;
					device.oneday.add(ob);
					flag = 1;
					c++;
					//System.out.println("p3");
					if(c>=rawdevices.get(i).observations.size()-1) continue;
					//System.out.println("found! " + ob.timeStamp);
				}
				//if you are here, then no effective tuple for this day
				if(flag == 0) {
					ob.sensorID = "null";
					ob.timeStamp = "null";
					device.oneday.add(ob);
					//System.out.println("p4");
				}
				//System.out.println("hey "+c);
				if(c>=rawdevices.get(i).observations.size()) continue;
				//jump to next day
				while(Difference(rawdevices.get(i).observations.get(c).timeStamp, endDay)>=0){
					//System.out.println(rawdevices.get(i).observations.get(c).timeStamp);
					//System.out.println("c= " +c);
					if(c>=rawdevices.get(i).observations.size()-1) break;
					c++;
				}
				//System.out.println(c);
				if(c>=rawdevices.get(i).observations.size()-1){
					break;
				}
				//System.out.println("hey* "+c);
			}
			for(int k=device.oneday.size();k<learnDays;k++){
				//to set the later days as null
				OBSERVATION ob = new OBSERVATION();
				ob.timeStamp="null";
				ob.sensorID="null";
				device.oneday.add(ob);
			}
			devices.add(device);
		}
	}

	public static void test(){
		System.out.println(rawdevices.size());
		System.out.println(rawdevices.get(0).mac + " " + rawdevices.get(0).timeStamp + " " + rawdevices.get(0).observations.size());
		System.out.println(devices.size());
		System.out.println(devices.get(0).oneday.size());
		for(int i=0;i<devices.get(0).oneday.size();i++){
			System.out.println(devices.get(0).oneday.get(i).timeStamp);
		}
	}

	public static String getClock(String day, int id) {// get the time point for one day, 6*12, starts from 08:00 AM, id
		// from 0
		Date clock = new Date();
		SimpleDateFormat dataformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		try {
			clock = dataformat.parse(day);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(clock);
		calendar.add(Calendar.MINUTE, id);
		Date m = calendar.getTime();
		String minute = dataformat.format(m);

		return minute;
	}

	public static String getDay(String Day, int id) {// generate day in each period, id from 0

		Date clock = new Date();
		SimpleDateFormat dataformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		try {
			clock = dataformat.parse(Day);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(clock);
		calendar.add(Calendar.DATE, id);
		Date m = calendar.getTime();
		String day = dataformat.format(m);

		return day;
	}

	public static String getMonth(String Day, int id) {

		Date clock = new Date();
		SimpleDateFormat dataformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		try {
			clock = dataformat.parse(Day);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(clock);
		calendar.add(Calendar.MONTH, id);
		Date m = calendar.getTime();
		String month = dataformat.format(m);

		return month;
	}

	public static int Difference(String fromDate, String toDate) {
		//return the difference between two time stamps
		SimpleDateFormat simpleFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		int minutes = 0;
		try {
			long from = simpleFormat.parse(fromDate).getTime();
			long to = simpleFormat.parse(toDate).getTime();
			minutes = (int) ((to - from) / 1000 / 60);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return minutes;
	}
	
}
