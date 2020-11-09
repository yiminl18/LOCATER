/*
 * This code is used to:
 * 1. generate local data: observations and affinities
 * 2. maintain local data: add affinity and observations
 */
package localizationDB;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class LocalDataMaintenance {
	
	static String serverDatabase = "tippersdb_restored";
	static String localDatabase = "enrichdb";
	public static List<String> offices = new ArrayList<>();

	public static class SemanticObservations {
		int id;
		double confidence;
		String payload;
		String timeStamp;
		String semantic_entity_id;
		int so_type_id;
		int virtual_sensor_id;
	}

	public static class Observations {
		int id;
		String payload;
		String timeStamp;
		String sensor_id;
		int observation_type_id;
	}

	public static class Space_metadata {
		int importance;// lower number, higher importance
		String room;
		String roomType;
	}

	public static List<String> possibleRooms = new ArrayList<>();
	public static List<SemanticObservations> observations = new ArrayList<>();
	public static List<SemanticObservations> cleaned_observations = new ArrayList<>();
	public static List<Space_metadata> space_metadata = new ArrayList<>();

	public static String createStatementDB(String dababase) {
		return String.format("create database IF NOT EXISTS %s;", dababase);
	}

	public static void loadSpaceMetadata() {
		try {
			BufferedReader csvReader = new BufferedReader(new FileReader("space_metadata.csv"));
			String row;
			int count = 0;
			while ((row = csvReader.readLine()) != null) {
				count++;
				if (count == 1)
					continue;
				String[] data = row.split(",");
				Space_metadata space = new Space_metadata();
				// do something with the data
				space.room = data[0];
				space.importance = Integer.valueOf(data[1]);
				space.roomType = data[2];
				// System.out.println(space.room + " " + space.importance + " " +
				// space.roomType);
				space_metadata.add(space);
				possibleRooms.add(data[0]);
			}
			csvReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String readSemanticObservationFromTipperOneUser(String email, String beginTime, String endTime) {
		return String.format(
				"select  distinct SEMANTIC_OBSERVATION.payload, SEMANTIC_OBSERVATION.confidence, SEMANTIC_OBSERVATION.timeStamp"
						+ " from USER, SEMANTIC_OBSERVATION where USER.email='%s' and SEMANTIC_OBSERVATION.semantic_entity_id=USER.SEMANTIC_ENTITY_ID"
						+ " and SEMANTIC_OBSERVATION.timeStamp>='%s' and SEMANTIC_OBSERVATION.timeStamp<'%s' ORDER BY SEMANTIC_OBSERVATION.timeStamp",
				email, beginTime, endTime);
	}

	public static String readSemanticObservationFromTipper(String beginTime, String endTime) {
		return String.format("select * from SEMANTIC_OBSERVATION where "
				+ " SEMANTIC_OBSERVATION.timeStamp>='%s' and SEMANTIC_OBSERVATION.timeStamp<'%s' and so_type_id=4 order by timestamp,semantic_entity_id",
				beginTime, endTime);
	}

	public static String readObservationFromTipperOneUser(String mac, String time) {
		return String.format("select timeStamp, sensor_id from OBSERVATION where payload='%s' and timeStamp = '%s'",
				MacToPayload(mac), time);
	}

	public static String readSemanticObservationFromLocal(String email, String beginTime, String endTime) {
		return String.format("select * from %s where timestamp>='%s'  and timestamp<='%s';", userDBformat(email),
				beginTime, endTime);
	}

	public static String createstatementObservationTable(String email) {// create observation table for each user
		return String.format(
				"create table IF NOT EXISTS OBSERVATION%s (timestamp varchar(255), location varchar(255),confidence double)",
				email);
	}

	public static String createstatementAffintyTable(String email) {
		return String.format(
				"create table IF NOT EXISTS AFFINITY%s (email varchar(255), affinity double, time varchar(255))",
				email);
	}

	public static String createstatementCatcheObservationList() {
		return String.format(
				"create table IF NOT EXISTS CatcheObservationList (email varchar(255), time varchar(255), primary key (email,time));");
	}

	public static String createstatementUserTable() {
		return String.format("create table IF NOT EXISTS Users (email varchar(255))");
	}

	public static String createstatementOfficeTable() {
		return String.format("create table IF NOT EXISTS office(email varchar(255), office varchar(255))");
	}

	public static String createstatementInsertUser(String email) {
		return String.format("insert into Users (email)value('%s');", email);
	}

	public static String readUser() {
		return String.format("select * from USER where email is not null;");
	}

	public static String createStatementTableExists(String tableName) {
		return String.format(
				"select t.table_name from information_schema.TABLES t where t.TABLE_SCHEMA ='%s' and t.TABLE_NAME ='%s';",
				localDatabase, tableName);
	}

	public static String insertObservation(String tablename, String timestamp, String location, double confidence) {
		return String.format("insert into %s(timestamp,location,confidence)value('%s','%s',%s);", tablename, timestamp,
				location, confidence);
	}

	public static String insertAffinity(String tablename, String email, Double affinity, String datetime) {
		return String.format("insert into %s(email,affinity,time)value('%s',%s,'%s');", tablename, email, affinity,
				datetime);
	}

	public static void readSemanticObservation(String beginTime, String endTime) {
		ResultSet rs;
		//System.out.print(beginTime + " " + endTime);
		Connect connectServer = new Connect("server", serverDatabase);// OBSERVATION
		Connection serverConnection = connectServer.getConnection();// Observations is database
		try {
			Statement stmtserver = serverConnection.createStatement();
			String sql = readSemanticObservationFromTipper(beginTime, endTime);
			rs = stmtserver.executeQuery(sql);
			while (rs.next()) {
				SemanticObservations ob = new SemanticObservations();
				ob.id = rs.getInt(1);
				ob.confidence = rs.getDouble(2);
				ob.payload = rs.getString(3);
				ob.timeStamp = rs.getString(4);
				ob.semantic_entity_id = rs.getString(5);
				ob.so_type_id = rs.getInt(6);
				ob.virtual_sensor_id = rs.getInt(7);
				observations.add(ob);

			}
			rs.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectServer.close();
	}

	public static void captureSemanticObservation(String email, String beginTime, String endTime) {
		// load observation from tipper to local database
		ResultSet rs;
		Connect connectServer = new Connect("server", serverDatabase);// OBSERVATION
		Connection serverConnection = connectServer.getConnection();// Observations is database
		Connect connectLocal = new Connect("local", localDatabase);// OBSERVATION
		Connection localConnection = connectLocal.getConnection();
		try {
			Statement stmtserver = serverConnection.createStatement();
			Statement stmtlocal = localConnection.createStatement();
			String sql = readSemanticObservationFromTipperOneUser(email, beginTime, endTime);
			rs = stmtserver.executeQuery(sql);
			String location;
			double confidence;
			String time;
			while (rs.next()) {
				location = rs.getString(1);
				location = location.substring(13, location.length() - 1);
				confidence = rs.getDouble(2);
				time = rs.getString(3);
				// System.out.println(insertObservation("OBSERVATION"+ID(email), time, location,
				// confidence));
				stmtlocal.executeUpdate(insertObservation("OBSERVATION" + ID(email), time, location, confidence));
			}
			rs.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectServer.close();
		connectLocal.close();
	}

	public static void creatUserObservation(String email) {
		Connect connectLocal = new Connect("local", localDatabase);// OBSERVATION
		Connection localConnection = connectLocal.getConnection();
		try {
			Statement stmtlocal = localConnection.createStatement();
			// System.out.println(createstatementObservationTable(email));
			stmtlocal.executeUpdate(createstatementObservationTable(email));
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectLocal.close();
	}

	public static void insertUser(String email) {
		Connect connectLocal = new Connect("local", localDatabase);// OBSERVATION
		Connection localConnection = connectLocal.getConnection();
		try {
			Statement stmtlocal = localConnection.createStatement();
			stmtlocal.executeUpdate(createstatementInsertUser(email));
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectLocal.close();
	}

	public static void UpdateCachedObservationList(String email, String time) {
		Connect connectLocal = new Connect("local", localDatabase);// OBSERVATION
		Connection localConnection = connectLocal.getConnection();
		try {
			Statement stmtlocal = localConnection.createStatement();
			stmtlocal.executeUpdate(String.format("insert into CatcheObservationList (email,time)value('%s','%s');",
					email, time.substring(0, 7)));
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectLocal.close();
	}

	public static Boolean CheckCachedObservationList(String email, String time) {
		Connect connectLocal = new Connect("local", localDatabase);// OBSERVATION
		Connection localConnection = connectLocal.getConnection();
		ResultSet rs;
		int count = 0;
		try {
			Statement stmtlocal = localConnection.createStatement();
			rs = stmtlocal.executeQuery(
					String.format("select count(*) from CatcheObservationList where email = '%s' and time ='%s';",
							email, time.substring(0, 7)));
			while (rs.next()) {
				count = rs.getInt(1);
			}
			if (count >= 1) {
				return true;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectLocal.close();
		return false;
	}

	public static void createDB(String database) {
		Connect connectLocal = new Connect("local", localDatabase);// OBSERVATION
		Connection localConnection = connectLocal.getConnection();
		try {
			Statement stmtlocal = localConnection.createStatement();
			stmtlocal.executeUpdate(createStatementDB(database));
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectLocal.close();
	}

	public static void createOfficeTable() {
		Connect connectLocal = new Connect("local", localDatabase);// OBSERVATION
		Connection localConnection = connectLocal.getConnection();
		try {
			Statement stmtlocal = localConnection.createStatement();
			stmtlocal.executeUpdate(createstatementOfficeTable());
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectLocal.close();
	}

	public static void createCatcheObservationListTable() {
		Connect connectLocal = new Connect("local", localDatabase);// OBSERVATION
		Connection localConnection = connectLocal.getConnection();
		try {
			Statement stmtlocal = localConnection.createStatement();
			stmtlocal.executeUpdate(createstatementCatcheObservationList());
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectLocal.close();
	}

	public static void createObservationTable(String email) {
		Connect connectLocal = new Connect("local", localDatabase);// OBSERVATION
		Connection localConnection = connectLocal.getConnection();
		try {
			Statement stmtlocal = localConnection.createStatement();
			stmtlocal.executeQuery(createstatementObservationTable(email));
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectLocal.close();
	}

	public static void buildOfficeTable() {
		//this table uses email to get office
		if (TableExists("office"))
			return;// if office table has been built, no need to build again
		Connect connectLocal = new Connect("local", localDatabase);
		Connection localConnection = connectLocal.getConnection();
		Connect connectServer = new Connect("server", serverDatabase);
		Connection serverConnection = connectServer.getConnection();
		ResultSet rs;
		List<String> emails = new ArrayList<>();
		try {
			Statement stmtserver = serverConnection.createStatement();
			rs = stmtserver.executeQuery(
					String.format("select email from USER where office is not null and email is not null"));
			while (rs.next()) {
				emails.add(rs.getString(1));
			}
			Statement stmtlocal = localConnection.createStatement();
			//System.out.println(emails.size());
			for (int i = 0; i < emails.size(); i++) {
				rs = stmtserver.executeQuery(String.format(
						"select INFRASTRUCTURE.name from USER, INFRASTRUCTURE"
								+ " where USER.office=INFRASTRUCTURE.SEMANTIC_ENTITY_ID and USER.email='%s'",
						emails.get(i)));
				while (rs.next()) {
					stmtlocal.executeUpdate(String.format("insert into office(email,office) value('%s','%s')",
							emails.get(i), rs.getString(1)));
				}
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectLocal.close();
		connectServer.close();
	}

	public static void readOfficefromLocal() {
		Connect connectLocal = new Connect("local", localDatabase);// OBSERVATION
		Connection localConnection = connectLocal.getConnection();
		ResultSet rs;
		offices.clear();
		try {
			Statement stmtlocal = localConnection.createStatement();
			rs = stmtlocal.executeQuery(String.format("select distinct office from office"));
			while (rs.next()) {
				offices.add(rs.getString(1));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectLocal.close();
	}

	public static void createUserTable() {
		Connect connectLocal = new Connect("local", localDatabase);// OBSERVATION
		Connection localConnection = connectLocal.getConnection();
		try {
			Statement stmtlocal = localConnection.createStatement();
			stmtlocal.executeUpdate(createstatementUserTable());
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectLocal.close();
	}

	public static boolean TableExists(String tableName) {
		Connect connectLocal = new Connect("local", localDatabase);// OBSERVATION
		Connection localConnection = connectLocal.getConnection();
		ResultSet rs;
		String table = "";
		try {
			Statement statement = localConnection.createStatement();
			rs = statement.executeQuery(createStatementTableExists(tableName));
			while (rs.next()) {
				table = rs.getString(1);
			}
			if (table.equals("")) {
				return false;
			}
		} catch (SQLException e) {
		}
		connectLocal.close();
		return true;
	}

	public static void getUserTemp() {
		Connect connectServer = new Connect("server", serverDatabase);// OBSERVATION
		Connection serverConnection = connectServer.getConnection();
		ResultSet rs;
		File file = new File("SocialNet.txt");
		File file_1 = new File("SocialNet_email.txt");
		try {
			BufferedReader in = new BufferedReader(new FileReader(file));
			String line;

			FileWriter out = new FileWriter(file_1);
			BufferedWriter bw = new BufferedWriter(out);

			while ((line = in.readLine()) != null) {
				String name = "null";
				// System.out.println(line);
				Statement stmtlocal = serverConnection.createStatement();
				rs = stmtlocal.executeQuery(String.format("select email from USER where name = '%s'", line));
				while (rs.next()) {
					name = rs.getString(1);
				}
				rs.close();
				// System.out.println(name);
				if (name.equals("null"))
					continue;
				bw.write(name);
				bw.newLine();
			}
			bw.flush();
			bw.close();
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static LocationModel getUsers() {// get all registered users
		LocationModel Users = new LocationModel();
		File file = new File("SocialNet_email.txt");
		try {
			BufferedReader in = new BufferedReader(new FileReader(file));
			String line;
			while ((line = in.readLine()) != null) {
				Users.Macs.add(line);
			}
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Users;
	}

	public static LocationModel getMacs() {// get all registered users
		LocationModel Users = new LocationModel();
		File file = new File("socialNet_device.txt");
		try {
			BufferedReader in = new BufferedReader(new FileReader(file));
			String line;
			while ((line = in.readLine()) != null) {
				Users.Macs.add(line);
			}
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Users;
	}

	public static String ID(String email) {
		int l = email.indexOf("@");
		int r = email.indexOf(".");
		if (r > 0 && r < l) {
			return email.substring(0, r);
		} else {
			return email.substring(0, l);
		}
	}

	public static Boolean isValidEmail(String email) {
		if (email.indexOf("@") == -1) {
			return false;
		}
		return true;
	}

	public static Boolean isObservationExist(String email, String time) {
		Connect connectLocal = new Connect("local", localDatabase);// OBSERVATION
		Connection localConnection = connectLocal.getConnection();
		ResultSet rs;
		int count = 0;
		try {
			Statement stmtlocal = localConnection.createStatement();
			rs = stmtlocal.executeQuery(String.format(
					"select count(*) from CatcheObservationList where email='%s' and time = '%s'", email, time));
			while (rs.next()) {
				count = rs.getInt(1);
			}
			if (count >= 1) {
				return true;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectLocal.close();
		return false;
	}

	public static Boolean isRegistered(String semanticID) {
		Connect connectServer = new Connect("server", serverDatabase);// OBSERVATION
		Connection serverConnection = connectServer.getConnection();
		ResultSet rs;
		int count = 0;
		try {
			Statement stmtserver = serverConnection.createStatement();
			rs = stmtserver.executeQuery(String.format(
					"select count(*) from USER where USER.SEMANTIC_ENTITY_ID='%s' and email is not null", semanticID));
			while (rs.next()) {
				count = rs.getInt(1);
			}
			// System.out.println("count=" + count);
			if (count >= 1) {
				return true;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectServer.close();
		return false;
	}

	public static String findEmailBySemanticID(String semanticID) {
		Connect connectServer = new Connect("server", serverDatabase);// OBSERVATION
		Connection serverConnection = connectServer.getConnection();
		ResultSet rs;
		String email = "";
		try {
			Statement stmtserver = serverConnection.createStatement();
			rs = stmtserver
					.executeQuery(String.format("select email from USER where SEMANTIC_ENTITY_ID='%s'", semanticID));
			while (rs.next()) {
				email = rs.getString(1);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectServer.close();
		return email;
	}

	public static String beginTime(String time) {
		return time.substring(0, 8) + "01 00:00:00";
	}

	public static String endTime(String time) {
		return AffinityLearning.getMonth(beginTime(time), 1);
	}

	public static String userDBformat(String email) {
		return "OBSERVATION" + ID(email);
	}

	public static String MacToPayload(String payload) {
		return String.format("{\"client_id\":\"%s\"}", payload);
	}
	
	public static String payloadtoMac(String payload) {
		return payload.substring(14, payload.length()-2);
	}

	public static void writeSemanticObservation(String tableName, List<SemanticObservations> obs) {
		// wirte clean semantic observations into database
		Connect connectServer = new Connect("server", serverDatabase);// OBSERVATION
		Connection serverConnection = connectServer.getConnection();
		try {
			Statement stmtserver = serverConnection.createStatement();
			for (int i = 0; i < obs.size(); i++) {
				String sql = String.format(
						"insert into %s(id,confidence,payload,timeStamp,semantic_entity_id,so_type_id,virtual_sensor_id)value('%s','%s','%s','%s','%s','%s','%s','%s');",
						tableName, obs.get(i).id, obs.get(i).confidence, obs.get(i).payload, obs.get(i).timeStamp,
						obs.get(i).semantic_entity_id, obs.get(i).so_type_id, obs.get(i).virtual_sensor_id);
				stmtserver.executeUpdate(sql);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectServer.close();
	}

	public static String emailToMac(String email) {
		Connect connectServer = new Connect("server", serverDatabase);// OBSERVATION
		Connection serverConnection = connectServer.getConnection();
		ResultSet rs;
		String mac = "null";
		String temp = "null";
		int c=0;
		//System.out.println(email);
		try {
			Statement stmtserver = serverConnection.createStatement();
			rs = stmtserver.executeQuery(String.format("select distinct SENSOR.id from USER, SENSOR where USER.email='%s'\n"
					+ "and USER.SEMANTIC_ENTITY_ID = SENSOR.USER_ID \n"
					+ "and (SENSOR.sensor_type_id = 3 or SENSOR.sensor_type_id is null)", email));
			while (rs.next()) {
				temp = rs.getString(1);
				c++;
				if(countFrequency(temp)>0){
					mac = temp;
				}
			}
			if(c==1){
				mac=temp;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectServer.close();
		return mac;
	}

	public static String MacToEmail(String mac) {
		Connect connectServer = new Connect("server", serverDatabase);// OBSERVATION
		Connection serverConnection = connectServer.getConnection();
		ResultSet rs;
		String email = "null";
		try {
			Statement stmtserver = serverConnection.createStatement();
			rs = stmtserver.executeQuery(String.format("select USER.email from USER, SENSOR where SENSOR.id='%s'\n"
					+ "			and USER.SEMANTIC_ENTITY_ID = SENSOR.USER_ID ", mac));
			while (rs.next()) {
				mac = rs.getString(1);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectServer.close();
		return email;
	}

	public static void generateSocialDevices(){
		//this code only runs once, to transform socialNet_email to socialNet_mac
		LocationModel Users = new LocationModel();
		Users = LocalDataMaintenance.getUsers();
		File file = new File("socialNet_device.txt");
		try{
			FileWriter out = new FileWriter(file);
			BufferedWriter bw = new BufferedWriter(out);

			for(int i=0;i<Users.Macs.size();i++){
				//System.out.println(Users.Macs.get(i) + " " + emailToMac(Users.Macs.get(i)));
				bw.write(emailToMac(Users.Macs.get(i)));
				bw.newLine();
			}

			bw.flush();
			bw.close();

		}catch (IOException e){
			e.printStackTrace();
		}

	}

	public static Integer countFrequency(String mac){
		//count frequency of a device
		Connect connectServer = new Connect("server", serverDatabase);// OBSERVATION
		Connection serverConnection = connectServer.getConnection();
		ResultSet rs;
		int count=0;
		try {
			Statement stmtserver = serverConnection.createStatement();
			rs = stmtserver.executeQuery(String.format("select count(*) from OBSERVATION_CLEAN\n" +
					"WHERE payload='%s' \n" +
					"and timeStamp>='2018-01-01 00:00:00' \n" +
					"and timeStamp<='2018-03-01 00:00:00'",mac));
			while (rs.next()) {
				count = rs.getInt(1);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectServer.close();
		return count;
	}

	public static String isInDB(String mac, String time) {
		// this function is to check if (mac,time) is in tippers, if yes, return sensor_id;
		// else, return null;
		Connect connectServer = new Connect("server", serverDatabase);// OBSERVATION
		Connection serverConnection = connectServer.getConnection();
		ResultSet rs;
		String sensor_id = "null";
		try {
			Statement stmtserver = serverConnection.createStatement();
			rs = stmtserver.executeQuery(String.format("select sensor_id from OBSERVATION \n"
					+ "where timeStamp = '%s'\n" + "and payload = '{\"client_id\":\"%s\"}'", time, mac));
			while (rs.next()) {
				sensor_id = rs.getString(1);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectServer.close();
		return sensor_id;
	}

	public static String searchOffice(String mac) {
		String office = "null";
		Connect connectServer = new Connect("server", serverDatabase);// OBSERVATION
		Connection serverConnection = connectServer.getConnection();
		ResultSet rs;
		try {
			Statement stmtserver = serverConnection.createStatement();
			rs = stmtserver.executeQuery(String.format("select roomID from USER_ROOM where mac = '%s'", mac));
			while (rs.next()) {
				office = rs.getString(1);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectServer.close();
		return office;
	}
	//for testing --------------------------------------------------------------

	public static void MultupleQueries(String begintime, String mac, int length){
		Connect connectServer = new Connect("server", serverDatabase);// OBSERVATION
		Connection serverConnection = connectServer.getConnection();
		ResultSet rs;
		for(int i=0;i<length;i++){
			String time = AffinityLearning.getDay(begintime,-i);
			//System.out.println(time);
			try {
				Statement stmtserver = serverConnection.createStatement();
				rs = stmtserver.executeQuery(String.format("select * from OBSERVATION_CLEAN \n" +
						"where timestamp >='%s' \n" +
						"and  timestamp <='%s' \n" +
						"and payload = '%s'",AffinityLearning.getClock(time,-10), AffinityLearning.getClock(time,10), mac));
				while (rs.next()) {
					rs.getString(2);
					rs.getString(3);
					rs.getString(4);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		connectServer.close();
	}

	public static void SingleQuery(String time, String mac, int length){
		Connect connectServer = new Connect("server", serverDatabase);// OBSERVATION
		Connection serverConnection = connectServer.getConnection();
		ResultSet rs;
		String beginTime = AffinityLearning.getDay(time,-length);
		String endTime = time;
		try {
			Statement stmtserver = serverConnection.createStatement();
			rs = stmtserver.executeQuery(String.format("select * from OBSERVATION_CLEAN \n" +
					"where timestamp >='%s' \n" +
					"and  timestamp <='%s' \n" +
					"and payload = '%s'",beginTime, endTime, mac));
			while (rs.next()) {
				rs.getString(2);
				rs.getString(3);
				rs.getString(4);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectServer.close();
	}

	public static LocationModel ReadObservationInterval(String target_mac, String target_sensor_id, String beginTime, String endTime) {
		ResultSet rs;
		Connect connectServer = new Connect("server", serverDatabase);// OBSERVATION
		Connection serverConnection = connectServer.getConnection();
		LocationModel onlineDevices = new LocationModel();
		String mac, senser_id;
		//System.out.println(target_sensor_id);
		try {
			Statement stmtlocal = serverConnection.createStatement();
			String sql = String.format(
					"select distinct payload, sensor_id from OBSERVATION_CLEAN where timeStamp >= '%s' and timeStamp <= '%s'" +
							"and sensor_id = '%s' and payload<>'%s'",
					beginTime, endTime,target_sensor_id,target_mac);
			//System.out.println(sql);
			rs = stmtlocal.executeQuery(sql);
			while (rs.next()) {
				onlineDevices.isOnline.add(true);
				onlineDevices.Macs.add(rs.getString(1));
				onlineDevices.sensorID.add(rs.getString(2));
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		connectServer.close();
		return onlineDevices;
	}
}
