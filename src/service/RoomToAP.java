package service;

import java.util.*;

public class RoomToAP {
	public static Map<String, List<String>> room2ap;

	public RoomToAP(APtoRoom aPtoRoom) {
		room2ap = new HashMap<>();
		for (Map.Entry<String, List<String>> entry : aPtoRoom.apMapRoom.entrySet()) {
			for (String room : entry.getValue()) {
				List<String> roomList = new ArrayList<String>();
				// room2ap.getOrDefault(room, new ArrayList<>()); ihe
				roomList.add(entry.getKey());
				room2ap.put(room, roomList);
			}
		}
	}

	public static void print() {
		System.out.println("Total Room: " + room2ap.size());
	}

	public static List<String> find(String room) {
		return room2ap.get(room);
	}

	public static Set<String> allAPsOfRoom(String room) {
		Set<String> aps = new HashSet<>();
		for (String ap : find(room)) {
			aps.add(ap);
		}
		return aps;
	}
}