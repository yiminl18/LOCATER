package service;

import dao.Connect;
import dao.LocalDataGeneration;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocationService {

    static public void main(String[] args) {
//        LocationState result = queryTimestamp("Roberto Yus", "2018-06-05 13:00:00", 1200,
//                1, 1, 0.8f, 120, 2);
//        result.print();
//        LocationState result = new0526QueryTimestamp("Roberto Yus", "2018-01-23 14:25:15",
//                "20180101", "20180630", 20, 150, 300, 20, 50, 0.1);
//        LocationState result = newQueryTimestamp("Primal", "2018-03-27 13:46:00");
//        fullQueryTimestamp("Roberto Yus", "2018-01-23 14:25:15",16,79,
//                "20180108","20180402",2);
//        LocationState result = newQueryTimestampByEmail("peeyushg@uci.edu","2018-05-01 12:26:51");
        LocationState result = queryByHashedMacAddress("8e6047388b50ce6399137a0331b9c7a4fae73d25",
                "2017-08-28 16:10:30");
//        result.print();
//        LocationState result = newQueryTimestampBySemanticEntityID("184684", "2018-03-17 17:09:00");
//		LocationState result1 = querySemanticObservationBySemanticID(184684, "2018-02-08 19:40:00", 1800L);
//		LocationState result2 = querySemanticObservationBySemanticID(184684, "2018-03-17 17:00:00", 1800L);
//		result1.print();
//		result2.print();
//        String s = getPersonFromWifi("8e6047388b50ce6399137a0331b9c7a4fae73d25");
//        System.out.println(s);
    }

    public static LocationState new0526QueryTimestamp(String name, String timeStamp, String start, String end, int pos,
                                                      int neg, int validity, int pos_r, int neg_r, double freq) {
        // Use this for the default parameters (for upper level)
        String tableName = LocalDataGeneration.getTableName(name);
        if (!checkLocalTableExist(tableName)) {
            System.out.println(String.format("Generating local data for %s first, which may take a while.", name));
            if (!LocalDataGeneration.generateData(name)) {
                System.out.println(
                        String.format("Query failed (return null), since local data cannot be generated for %s", name));
                return null;
            }
        }
        CloseableHttpClient httpClient = HttpClients.createDefault();
        URI uri = null;
        try {
            uri = new URIBuilder().setScheme("http").setHost("localhost").setPort(9096).setPath("/newfquery")
                    .setParameter("table_name", tableName).setParameter("user_name", name)
                    .setParameter("time", timeStamp).setParameter("start", start).setParameter("end", end)
                    .setParameter("pos", String.valueOf(pos)).setParameter("neg", String.valueOf(neg))
                    .setParameter("validity", String.valueOf(validity)).setParameter("pos_r", String.valueOf(pos_r))
                    .setParameter("neg_r", String.valueOf(neg_r)).setParameter("freq", String.valueOf(freq)).build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        HttpGet get = new HttpGet(uri);

        try {
            CloseableHttpResponse response = httpClient.execute(get);
            String answer = EntityUtils.toString(response.getEntity());
            answer = answer.replaceAll("\"", "");
            answer = answer.replaceAll("\n", "");
            LocationState result = new LocationState();
            if (answer.charAt(0) == 'i') {
                result.inside = true;
                result.accessPoint = answer.split(" ")[1];
                APtoRoom aPtoRoom = new APtoRoom();
                aPtoRoom.load();
                result.possibleRooms = aPtoRoom.find(result.accessPoint);
            } else {
                result.inside = false;
            }
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static LocationState queryTimestamp(String name, String timeStamp, int alpha, int mode, int modeVal,
                                               float gama, int beta, int probMode) {
        /**
         * mode represents different ways to generate the data to learn the
         * distribution. 1. Divide by block 2. Only use the same week day of 'modeVal'
         * weeks before and after 3. Use 'modeVal' days before and after (except
         * sat,sun) 4. Combine both same day of a week with consecutive days
         */

        String tableName = LocalDataGeneration.getTableName(name);
        if (!checkLocalTableExist(tableName)) {
            System.out
                    .println(String.format("Need to generate local data for %s first, which may take a while.", name));
            if (!LocalDataGeneration.generateData(name)) {
                System.out.println(
                        String.format("Query failed (return null), since local data cannot be generated for %s", name));
                return null;
            }
        }

        List<DailyActivity> trainActivities;
        LocalDateTime dateTime = LocalDateTime.parse(timeStamp, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"));
        LocalDate thisDay = dateTime.toLocalDate();
        if (mode == 1) {
            if (thisDay.isAfter(LocalDate.of(2018, 4, 1))) {
                trainActivities = LocationPrediction.buildTimeSeries(tableName, "2018-04-02", "2018-06-11");
            } else if (thisDay.isAfter(LocalDate.of(2018, 1, 7))) {
                trainActivities = LocationPrediction.buildTimeSeries(tableName, "2018-01-08", "2018-03-19");
            } else {
                trainActivities = LocationPrediction.buildTimeSeries(tableName, "2017-09-25", "2017-12-11");
            }
        } else if (mode == 2) {
            List<LocalDate> dates = new ArrayList<>();
            dates.add(thisDay);
            for (int i = 1; i < modeVal; i++) {
                dates.add(thisDay.minusDays(modeVal * 7));
                dates.add(thisDay.plusDays(modeVal * 7));
            }
            trainActivities = new ArrayList<>();
            LocationPrediction.buildTimeSeriesAddMany(dates, tableName, trainActivities);
        } else if (mode == 3) {
            LocalDate dateStart, dateEnd;
            int minus = 1;
            for (int i = 1; i < modeVal; i++) {
                while (thisDay.minusDays(minus).getDayOfWeek() == DayOfWeek.SATURDAY
                        || thisDay.minusDays(minus).getDayOfWeek() == DayOfWeek.SUNDAY) {
                    ++minus;
                }
                ++minus;
            }
            dateStart = thisDay.minusDays(minus - 1);
            int plus = 1;
            for (int i = 1; i < modeVal; i++) {
                while (thisDay.plusDays(plus).getDayOfWeek() == DayOfWeek.SATURDAY
                        || thisDay.plusDays(plus).getDayOfWeek() == DayOfWeek.SUNDAY) {
                    ++plus;
                }
                ++plus;
            }
            dateEnd = thisDay.plusDays(plus);
            trainActivities = LocationPrediction.buildTimeSeries(tableName, dateStart.toString(), dateEnd.toString());
        } else if (mode == 4) {
            // gama*p(consecutive)+(1-gama)*p(same day of week)
            List<DailyActivity> train1 = new ArrayList<>();
            List<DailyActivity> train2 = new ArrayList<>();
            LocalDate dateStart, dateEnd;
            List<LocalDate> dates = new ArrayList<>();
            dates.add(thisDay);
            int minus = 1;
            for (int i = 1; i < modeVal; i++) {
                while (thisDay.minusDays(minus).getDayOfWeek() == DayOfWeek.SATURDAY
                        || thisDay.minusDays(minus).getDayOfWeek() == DayOfWeek.SUNDAY) {
                    ++minus;
                }
                if (minus % 7 == 0) {
                    dates.add(thisDay.minusDays(minus));
                }
                ++minus;
            }
            dateStart = thisDay.minusDays(minus - 1);
            int plus = 1;
            for (int i = 1; i < modeVal; i++) {
                while (thisDay.plusDays(plus).getDayOfWeek() == DayOfWeek.SATURDAY
                        || thisDay.plusDays(plus).getDayOfWeek() == DayOfWeek.SUNDAY) {
                    ++plus;
                }
                if (plus % 7 == 0) {
                    dates.add(thisDay.plusDays(plus));
                }
                ++plus;
            }
            dateEnd = thisDay.plusDays(plus);
            train1 = LocationPrediction.buildTimeSeries(tableName, dateStart.toString(), dateEnd.toString());
            LocationPrediction.buildTimeSeriesAddMany(dates, tableName, train2);
            DailyDistribution d1 = LocationPrediction.calcDistribution(train1, alpha, beta);
            DailyDistribution d2 = LocationPrediction.calcDistribution(train2, alpha, beta);
            DailyDistribution distr = new DailyDistribution(beta);
            int totalUnit = distr.totalUnit;
            for (int i = 0; i < totalUnit; i++) {
                distr.inProb[i] = gama * d1.inProb[i] + (1 - gama) * d2.inProb[i];
                Map<String, Double> map = new HashMap<>();
                Set<String> stringSet = new HashSet<>();
                stringSet.addAll(d1.sensorDistr.get(i).keySet());
                stringSet.addAll(d2.sensorDistr.get(i).keySet());
                for (String s : stringSet) {
                    map.put(s, d1.sensorDistr.get(i).getOrDefault(s, 0d) * gama
                            + (1 - gama) * d2.sensorDistr.get(i).getOrDefault(s, 0d));
                }
                distr.sensorDistr.add(map);
            }
            return LocationPrediction.predictForATimestamp(distr, tableName, timeStamp, alpha, probMode);
        } else {
            trainActivities = new ArrayList<>();
            System.out.println("Training data generation mode can only be 1,2,3 or 4.");
        }
        DailyDistribution distribution = LocationPrediction.calcDistribution(trainActivities, alpha, beta);
        return LocationPrediction.predictForATimestamp(distribution, tableName, timeStamp, alpha, probMode);
    }

    public static LocationState newQueryTimestamp(String name, String timeStamp) {
        // Use this for the default parameters (for upper level)
        String tableName = LocalDataGeneration.getTableName(name);
        if (!checkLocalTableExist(tableName)) {
            System.out.println(String.format("Generating local data for %s first, which may take a while.", name));
            if (!LocalDataGeneration.generateData(name)) {
                System.out.println(
                        String.format("Query failed (return null), since local data cannot be generated for %s", name));
                return null;
            }
        }
        CloseableHttpClient httpClient = HttpClients.createDefault();
        URI uri = null;
        try {
            uri = new URIBuilder().setScheme("http").setHost("localhost").setPort(9096).setPath("/query")
                    .setParameter("table_name", tableName).setParameter("user_name", name)
                    .setParameter("time", timeStamp).build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        HttpGet get = new HttpGet(uri);

        try {
            CloseableHttpResponse response = httpClient.execute(get);
            String answer = EntityUtils.toString(response.getEntity());
            answer = answer.replaceAll("\"", "");
            answer = answer.replaceAll("\n", "");
            LocationState result = new LocationState();
            if (answer.charAt(0) == 'i') {
                result.inside = true;
                result.accessPoint = answer.split(" ")[1];
                APtoRoom aPtoRoom = new APtoRoom();
                aPtoRoom.load();
                result.possibleRooms = aPtoRoom.find(result.accessPoint);
            } else {
                result.inside = false;
            }
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static LocationState newQueryTimestampByEmail(String email, String timeStamp) {
        String tableName = LocalDataGeneration.getTableNameFromEmail(email);
        if (!checkLocalTableExist(tableName)) {
            System.out.println(String.format("Generating local data for %s first, which may take a while.", email));
            if (!LocalDataGeneration.generateDataFromEmail(email)) {
                System.out.println(String
                        .format("Query failed (return null), since local data cannot be generated for %s", email));
                return null;
            }
        }
        CloseableHttpClient httpClient = HttpClients.createDefault();
        URI uri = null;
        try {
            uri = new URIBuilder().setScheme("http").setHost("localhost").setPort(9096).setPath("/query")
                    .setParameter("table_name", tableName).setParameter("user_name", email)
                    .setParameter("time", timeStamp).build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        HttpGet get = new HttpGet(uri);

        try {
            CloseableHttpResponse response = httpClient.execute(get);
            String answer = EntityUtils.toString(response.getEntity());
            answer = answer.replaceAll("\"", "");
            answer = answer.replaceAll("\n", "");
            LocationState result = new LocationState();
            if (answer.charAt(0) == 'i') {
                result.inside = true;
                result.accessPoint = answer.split(" ")[1];
                APtoRoom aPtoRoom = new APtoRoom();
                aPtoRoom.load();
                result.possibleRooms = aPtoRoom.find(result.accessPoint);
            } else {
                result.inside = false;
            }
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static LocationState newQueryTimestampBySemanticEntityID(String sid, String timeStamp) {
        String tableName = LocalDataGeneration.getTableNameFromSemanticID(sid);
        if (!checkLocalTableExist(tableName)) {
            System.out.println(String
                    .format("Generating local data for semantic entity id %s first, which may take a while.", sid));
            if (!LocalDataGeneration.generateDataFromSemanticID(sid)) {
                System.out.println(
                        String.format("Query failed (return null), since local data cannot be generated for %s", sid));
                return null;
            }
        }
        CloseableHttpClient httpClient = HttpClients.createDefault();
        URI uri = null;
        try {
            uri = new URIBuilder().setScheme("http").setHost("localhost").setPort(9096).setPath("/newquery")
                    .setParameter("table_name", tableName).setParameter("user_name", "sid" + sid)
                    .setParameter("time", timeStamp).build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        HttpGet get = new HttpGet(uri);

        try {
            CloseableHttpResponse response = httpClient.execute(get);
            String answer = EntityUtils.toString(response.getEntity());
            answer = answer.replaceAll("\"", "");
            answer = answer.replaceAll("\n", "");
            LocationState result = new LocationState();
            if (answer.charAt(0) == 'i') {
                result.inside = true;
                result.accessPoint = answer.split(" ")[1];
                APtoRoom aPtoRoom = new APtoRoom();
                aPtoRoom.load();
                result.possibleRooms = aPtoRoom.find(result.accessPoint);
            } else {
                result.inside = false;
            }
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // outside_threshold set to 1800L. Means leave half an hour count as outside.
    public static LocationState querySemanticObservationBySemanticID(int semanticId, String timeStamp,
                                                                     long outside_threshold) {
        LocationState result = new LocationState();
        try (Connection serverConnection = new Connect("server").getConnection()) {
            PreparedStatement getLastEventTimeStamp = serverConnection.prepareStatement("select max(timeStamp) "
                    + "from SEMANTIC_OBSERVATION " + "where semantic_entity_id = ? " + "  and timeStamp <= ?;");
            getLastEventTimeStamp.setInt(1, semanticId);
            getLastEventTimeStamp.setString(2, timeStamp);

            PreparedStatement getNextEventTimeStamp = serverConnection.prepareStatement("select min(timeStamp) "
                    + "from SEMANTIC_OBSERVATION " + "where semantic_entity_id = ? " + "  and timeStamp > ?;");
            getNextEventTimeStamp.setInt(1, semanticId);
            getNextEventTimeStamp.setString(2, timeStamp);

            ResultSet lastEventTimeStampSet = getLastEventTimeStamp.executeQuery();
            ResultSet nextEventTimeStampSet = getNextEventTimeStamp.executeQuery();
            String lastEventTimeStamp = null, nextEventTimestamp = null;
            if (lastEventTimeStampSet.next()) {
                lastEventTimeStamp = lastEventTimeStampSet.getString(1);
            }
            if (nextEventTimeStampSet.next()) {
                nextEventTimestamp = nextEventTimeStampSet.getString(1);
            }

            if (lastEventTimeStamp == null) {
                System.out.printf(
                        "The timestamp %s for sid %d is before the first timestamp of this sid in semantic observation table.\n",
                        timeStamp, semanticId);
                result.setFalse();
                return result;
            }

            // Compute the delta in seconds to the cloest connection event
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime lastEventTime = LocalDateTime.from(f.parse(lastEventTimeStamp));
            LocalDateTime queryTime = LocalDateTime.from(f.parse(timeStamp));
            long delta = Duration.between(lastEventTime, queryTime).getSeconds();
            LocalDateTime closestEventTime = lastEventTime;
            if (nextEventTimestamp != null) {
                LocalDateTime nextEventTime = LocalDateTime.from(f.parse(nextEventTimestamp));
                long delta_new = Duration.between(queryTime, nextEventTime).getSeconds();
                if (delta_new < delta) {
                    delta = delta_new;
                    closestEventTime = nextEventTime;
                }
            }
//            Debug use only.
//            else {
//                System.out.printf("The timestamp %s for sid %d is after the last timestamp of this sid in semantic observation table.\n", timeStamp, semanticId);
//            }

            // Query all the candidate rooms
            if (delta >= outside_threshold) {
                result.setFalse();
                return result;
            }

            PreparedStatement getAllRooms = serverConnection.prepareStatement(
                    "select payload " + "from SEMANTIC_OBSERVATION where timeStamp = ? and semantic_entity_id = ?;");
            getAllRooms.setString(1, closestEventTime.format(f));
            getAllRooms.setInt(2, semanticId);
            ResultSet allRoomsSet = getAllRooms.executeQuery();
            List<String> allRooms = new ArrayList<>();
            while (allRoomsSet.next()) {
                String roomPayload = allRoomsSet.getString(1);
                Pattern pattern = Pattern.compile(": (.*?)}");
                Matcher matcher = pattern.matcher(roomPayload);
                if (matcher.find()) {
                    allRooms.add(matcher.group(1));
                }
            }
            result.inside = true;
            result.possibleRooms = allRooms;
            if (allRooms.size() == 0) {
                result.accessPoint = "";
            } else {
                APtoRoom aPtoRoom = new APtoRoom();
                aPtoRoom.load();
                RoomToAP roomToAP = new RoomToAP(aPtoRoom);
                Set<String> ap_possibilities = roomToAP.allAPsOfRoom(allRooms.get(0));
                for (int i = 1; i < allRooms.size(); ++i) {
                    ap_possibilities.retainAll(roomToAP.allAPsOfRoom(allRooms.get(i)));
                    if (ap_possibilities.size() <= 1) {
                        break;
                    }
                }
                if (ap_possibilities.size() == 0) {
                    result.accessPoint = "cannot inference ap from candidate rooms";
                } else {
                    result.accessPoint = (String) ap_possibilities.toArray()[0];
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        result.setFalse();
        return result;
    }

    public static LocationState fullQueryTimestamp(String name, String timeStamp, int pos, int neg, String start_day,
                                                   String end_day, int step) {
        // Use this for the tuning parameters (run independent experiment for only this
        // part)
        String tableName = LocalDataGeneration.getTableName(name);
        if (!checkLocalTableExist(tableName)) {
            System.out
                    .println(String.format("Need to generate local data for %s first, which may take a while.", name));
            if (!LocalDataGeneration.generateData(name)) {
                System.out.println(
                        String.format("Query failed (return null), since local data cannot be generated for %s", name));
                return null;
            }
        }
        CloseableHttpClient httpClient = HttpClients.createDefault();
        URI uri = null;
        try {
            uri = new URIBuilder().setScheme("http").setHost("localhost").setPort(9096).setPath("/fquery")
                    .setParameter("table_name", tableName).setParameter("user_name", name)
                    .setParameter("time", timeStamp).setParameter("pos", String.valueOf(pos))
                    .setParameter("neg", String.valueOf(neg)).setParameter("start_day", start_day)
                    .setParameter("end_day", end_day).setParameter("step", String.valueOf(step)).build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        HttpGet get = new HttpGet(uri);
//        RequestConfig config = RequestConfig.custom()
//                .setConnectionRequestTimeout(3000)
//                .setConnectTimeout(3000)
//                .setSocketTimeout(3000)
//                .build();
//        get.setConfig(config);
        try {
            CloseableHttpResponse response = httpClient.execute(get);
            String answer = EntityUtils.toString(response.getEntity());
            answer = answer.replaceAll("\"", "");
            answer = answer.replaceAll("\n", "");
//            System.out.println(answer);
            LocationState result = new LocationState();
            if (answer.charAt(0) == 'i') {
                result.inside = true;
                result.accessPoint = answer.split(" ")[1];
                APtoRoom aPtoRoom = new APtoRoom();
                aPtoRoom.load();
                result.possibleRooms = aPtoRoom.find(result.accessPoint);
            } else {
                result.inside = false;
            }
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static LocationState baselineTimestamp(String name, String timeStamp, int alpha) {
        // alpha means the seconds of threshold, should be 3600 if it is an hour.
        LocationState result = new LocationState();
        List<DailyActivity> trainActivities = new ArrayList<>();
        LocalDateTime dateTime = LocalDateTime.parse(timeStamp, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"));
        LocalDate thisDay = dateTime.toLocalDate();
        LocalTime thisTime = dateTime.toLocalTime();
        List<LocalDate> dates = new ArrayList<>();
        dates.add(thisDay);
        String tableName = LocalDataGeneration.getTableName(name);
        LocationPrediction.buildTimeSeriesAddMany(dates, tableName, trainActivities);

        DailyActivity activityThisday = null;
        try {
            activityThisday = trainActivities.get(0);
        } catch (IndexOutOfBoundsException e) {
            result.inside = false;
            return result;
        }
        List<LocalTime> allTimes = activityThisday.times;
        int totalOb = allTimes.size();
        if (thisTime.isBefore(allTimes.get(0)) || thisTime.isAfter(allTimes.get(totalOb - 1))) {
            result.inside = false;
            return result;
        }
        int i = 0;
        while (i < totalOb && !allTimes.get(i).isAfter(thisTime)) {
            ++i;
        }
        LocalTime b1 = allTimes.get(i - 1);
        LocalTime b2 = allTimes.get(i);
        if (Duration.between(b1, b2).toMillis() >= alpha * 1000) {
            result.inside = false;
            return result;
        }
        result.inside = true;
        result.accessPoint = activityThisday.sensors.get(i - 1);
        APtoRoom aPtoRoom = new APtoRoom();
        aPtoRoom.load();
        result.possibleRooms = aPtoRoom.find(result.accessPoint);
        return result;
    }

    static boolean checkLocalTableExist(String tableName) {
        try (Connect connect = new Connect("true-local")) {
            Statement statement = connect.getConnection().createStatement();
            statement.execute(String.format("select 1 from %s limit 1;", tableName));
        } catch (SQLException e) {
            return false;
        }
        return true;
    }

    static String getPersonFromWifi(String hased_sensor_id) {
        try (Connect connect = new Connect("server")) {
            Connection connection = connect.getConnection();
            PreparedStatement ps = connection.prepareStatement("select u.name\n" + "from SENSOR as s,\n"
                    + "     USER as u\n" + "where s.USER_ID = u.SEMANTIC_ENTITY_ID\n" + "  and s.id = ?");
            ps.setString(1, hased_sensor_id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString(1);
            } else {
                return "UNDEFINED PERSON";
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "UNDEFINED PERSON";
        }
    }

    public static LocationState queryByHashedMacAddress(String hashedMac, String timeStamp) {
        long t1 = System.currentTimeMillis();
        String tableName = LocalDataGeneration.getTableName(hashedMac);
        if (!checkLocalTableExist(tableName)) {
            System.out.println(String.format("Generating local data for %s first, which may take a while.", hashedMac));
            if (!LocalDataGeneration.generateDataFromCleanUsingHashedMac(hashedMac)) {
                System.out.println(String
                        .format("Query failed (return null), since local data cannot be generated for %s", hashedMac));
                return null;
            }
        }
        long t2 = System.currentTimeMillis();
        //System.out.println(String.format("Check table time: %d ms", t2 - t1));
        t2 = System.currentTimeMillis();
        CloseableHttpClient httpClient = HttpClients.createDefault();
        URI uri = null;
        try {
            uri = new URIBuilder().setScheme("http").setHost("localhost").setPort(9096).setPath("/newquery")
                    .setParameter("table_name", tableName).setParameter("user_name", hashedMac)
                    .setParameter("time", timeStamp).build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        HttpGet get = new HttpGet(uri);

        try {
            CloseableHttpResponse response = httpClient.execute(get);
            String answer = EntityUtils.toString(response.getEntity());
            long t3 = System.currentTimeMillis();
            //System.out.println(String.format("Python server running time: %d ms", t3 - t2));
            answer = answer.replaceAll("\"", "");
            answer = answer.replaceAll("\n", "");
            LocationState result = new LocationState();
            if (answer.charAt(0) == 'i') {
                result.inside = true;
                result.accessPoint = answer.split(" ")[1];
                APtoRoom aPtoRoom = new APtoRoom();
                aPtoRoom.load();
                result.possibleRooms = aPtoRoom.find(result.accessPoint);
            } else {
                result.inside = false;
            }
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static LocationState queryByHashedMacAddressWithoutCache(String hashedMac, String timeStamp) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        URI uri = null;
        try {
            uri = new URIBuilder().setScheme("http").setHost("localhost").setPort(9096).setPath("/nocachequery")
                    .setParameter("mac", hashedMac)
                    .setParameter("time", timeStamp).build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        HttpGet get = new HttpGet(uri);

        try {
            CloseableHttpResponse response = httpClient.execute(get);
            String answer = EntityUtils.toString(response.getEntity());
            answer = answer.replaceAll("\"", "");
            answer = answer.replaceAll("\n", "");
            LocationState result = new LocationState();
            if (answer.charAt(0) == 'i') {
                result.inside = true;
                result.accessPoint = answer.split(" ")[1];
                APtoRoom aPtoRoom = new APtoRoom();
                aPtoRoom.load();
                result.possibleRooms = aPtoRoom.find(result.accessPoint);
            } else {
                result.inside = false;
            }
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}