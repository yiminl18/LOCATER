package localizationDB;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
public class Testing {
    public static void main(String args[]) {
        /*String time = "2018-05-16 15:23:37";
        String mac = LocalDataMaintenance.emailToMac("peeyushg@uci.edu");
        int learnDays = 7;
        String beginTime = AffinityLearning.getDay(time, -learnDays);
        String endTime = time;
        AffinityLearning.ReadObservationFromTipperOneUser(mac,beginTime,endTime);
        AffinityLearning.FilterObservation(time,learnDays);
        AffinityLearning.test();*/
    LocationModel Users = new LocationModel();
        Users = LocalDataMaintenance.getMacs();
        for(int i=0;i<Users.Macs.size();i++){
            System.out.println(Users.Macs.get(i));
        }
    }
}
