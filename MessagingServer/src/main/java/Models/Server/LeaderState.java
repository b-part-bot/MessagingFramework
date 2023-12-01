package Models.Server;

import Models.ClientPojo;
import Models.Group;

import java.util.concurrent.ConcurrentHashMap;

public class LeaderState {
    public final ConcurrentHashMap<String, Group> globalRoomList = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, ClientPojo> globalClients = new ConcurrentHashMap<>();
    private static LeaderState instance;

    public static synchronized LeaderState getInstance() {
        ServerInfo currentServer = StatusHandler.getServerStateInstance().getCurrentServerData();
        ServerInfo leaderServer = StatusHandler.getServerStateInstance().getLeaderServerData();
        if (instance == null && currentServer.getServerID().equals(leaderServer.getServerID())) {
            instance = new LeaderState();
        }
        return instance;
    }

    public ConcurrentHashMap<String, ClientPojo> getGlobalClients() {
        return globalClients;
    }
    public void addClientToGlobalList(ClientPojo client){
        this.globalClients.put(client.getIdentity(),client);
    }
    public ConcurrentHashMap<String, Group> getGlobalRoomList() {
        return globalRoomList;
    }
    public void addRoomToGlobalList(Group room){
        this.globalRoomList.put(room.getRoomID(),room);
    }
}
