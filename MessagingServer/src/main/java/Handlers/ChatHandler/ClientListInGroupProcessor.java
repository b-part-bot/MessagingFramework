package Handlers.ChatHandler;

import Models.ClientPojo;
import Models.Group;
import Models.Server.StatusHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

public class ClientListInGroupProcessor {

    public String getClientsRoomID(String identity){

        String roomid = null;
        Collection<Group> roomsList = StatusHandler.getServerStateInstance().roomList.values();
        for (Group room : roomsList) {
            for (int j = 0; j < room.getClients().size(); j++) {
                if (Objects.equals(room.getClients().get(j).getIdentity(), identity)) {
                    roomid = room.getRoomID();
                    break;
                }
            }
        }
        return roomid;
    }

    public String getRoomOwner(String roomid){

        return StatusHandler.getServerStateInstance().roomList.get(roomid).getOwner();
    }

    public ArrayList<String> getClientsInRoom(String roomid){
        ArrayList<String> clients = new ArrayList<>();
        ArrayList<ClientPojo> clientsList = StatusHandler.getServerStateInstance().roomList.get(roomid).getClients();
        for (ClientPojo client : clientsList){
            clients.add(client.getIdentity());
        }
        return clients;
    }
}