package Handlers.CoordinationHandler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import org.json.simple.JSONObject;

import Models.Group;
import Models.Server.LeaderState;

public class RoomListHandler {

    private final ResponseHandler serverResponseHandler = new ResponseHandler();

    public ArrayList<String> getRoomList () {
        ArrayList<String> roomList = new ArrayList<>();


        ConcurrentHashMap<String, Group> rooms = LeaderState.getInstance().getGlobalRoomList();
        for (Iterator<String> it = rooms.keys().asIterator(); it.hasNext(); ) {
            String room = it.next();
            roomList.add(room);
        }
        return roomList;
    }

    public JSONObject coordinatorRoomList(String clientID) {
        ArrayList<String> roomList = getRoomList();
        JSONObject response;
        response = this.serverResponseHandler.createAllRoomsListResponseFromLeader(roomList, clientID);
        return response;
    }
}