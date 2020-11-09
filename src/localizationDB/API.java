package localizationDB;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import spark.Request;
import spark.Response;

import java.lang.reflect.Array;
import java.util.Arrays;

import static spark.Spark.*;

public class API {


    public static void main(String[] args) {

        Initialization.Initialize();

        //ipAddress("0.0.0.0");
        // Configure Spark
        port(4567);
        threadPool(8);
        // Set up routes
        post("/location",  API::localize);

    }

    public static JSONObject localize(Request req, Response res) throws JSONException {

        try {
            System.out.println("Request Received " + req.body()); 
            JSONObject json = new JSONObject(req.body());

            LocationSet Location = LocationPrediction.getLocation(
                    json.get("mac").toString(),
                    json.get("timestamp").toString(),
                    14
                    );
            System.out.println(Location.buildingLocation + " " + Location.regionLocation + " " + Location.roomLocation);

            double probs[] = new double[DBHLocationMap.numLocations];
            int label = 0;
            double max_ = 0.0;

            if (!Location.buildingLocation.equals("null")) {
                for (int i = 0; i < Location.probabilities.size(); i++) {
                    try {
			if (Double.isNaN(Location.probabilities.get(i))) {
                            continue;
                        }
                        probs[DBHLocationMap.locationMap.get(
                                Integer.parseInt(Location.rooms.get(i)))] = Location.probabilities.get(i);

                        if ( Location.probabilities.get(i) > max_) {
                            label = DBHLocationMap.locationMap.get(
                                    Integer.parseInt(Location.rooms.get(i)));
                            max_ = Location.probabilities.get(i);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            System.out.println("Result returned");
            JSONObject response = new JSONObject();
            response.put("label", label);
            response.put("prob_dist", probs);
            return response;

        } catch (Exception e) {
            e.printStackTrace();
            JSONObject response = new JSONObject();
            response.put("label", 0);
            response.put("prob_dist", new double[DBHLocationMap.numLocations]);
            return response;
        }
    }

}
