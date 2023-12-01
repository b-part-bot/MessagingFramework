package Models.Server;

import Models.ClientPojo;
import Models.Group;
import Services.ChatService.MessagingService;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class StatusHandler {
    private static StatusHandler serverState;
    private ServerInfo currentServerData;
    private ServerInfo leaderServerData;
    private ServerInfo richNeighborData;
    private boolean isIgnorant;
    private final int initialRounds = 2;
    private final ConcurrentMap<String, ServerInfo> serversList = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, Group> roomList = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, ClientPojo> clients = new ConcurrentHashMap<>();
    public ArrayList<String> globalRoomList = new ArrayList<>();
    public ArrayList<String> globalRoomServersList = new ArrayList<>();
    public ArrayList<String> globalRoomOwnersList = new ArrayList<>();
    public ArrayList<ArrayList<String>> globalRoomClientsList = new ArrayList<>();
    public ArrayList<String> globalClientId = new ArrayList<>();
    public final ConcurrentHashMap<String, MessagingService> clientServices = new ConcurrentHashMap<>();
    private final Logger logger = Logger.getLogger(StatusHandler.class);


    public static synchronized StatusHandler getServerStateInstance() {
        if (serverState == null) {
            serverState = new StatusHandler();
        }
        return serverState;
    }

    public synchronized void setCurrentServerData(String serverID) {
        currentServerData = serversList.get(serverID);
    }

    public synchronized ServerInfo getServerDataById(String serverID) {
        return serversList.get(serverID);
    }

    public synchronized ServerInfo getCurrentServerData() {
        return currentServerData;
    }

    public synchronized ServerInfo getRichNeighborData() {
        return richNeighborData;
    }

    public synchronized Boolean getIsIgnorant() {
        return isIgnorant;
    }

    public synchronized int getInitialRounds() {
        return initialRounds;
    }

    public synchronized ArrayList<String> getGlobalRoomList() {
        return globalRoomList;
    }

    public synchronized ArrayList<String> getGlobalRoomServersList() {
        return globalRoomServersList;
    }

    public synchronized ArrayList<String> getGlobalRoomOwnersList() {
        return globalRoomOwnersList;
    }

    public synchronized ArrayList<ArrayList<String>> getGlobalRoomClientsList() {
        return globalRoomClientsList;
    }

    public synchronized ArrayList<String> getGlobalClientsIds() {
        return globalClientId;
    }

    public synchronized ConcurrentMap<String, ServerInfo> getServersList() {
        return serversList;
    }

    public synchronized List<ServerInfo> getServersListAsArray() {
        return new ArrayList<>(serversList.values());
    }

    public ServerInfo getLeaderServerData() {
        return leaderServerData;
    }

    public boolean isCurrentServerLeader(){
        return this.currentServerData.getServerID().equals(this.leaderServerData.getServerID());
    }

    public synchronized ConcurrentMap<String, Group> getRoomList(){
        return roomList;
    }

    public void setLeader(ServerInfo leaderServerData) {
        String prevLeaderID = leaderServerData.getServerID();
        this.leaderServerData = leaderServerData;
        if (prevLeaderID.equals(currentServerData.getServerID())){
            if(!globalClientId.isEmpty()) {
                for (String clientID: globalClientId){
                    ClientPojo client = new ClientPojo();
                    client.setIdentity(clientID);
                    LeaderState.getInstance().globalClients.put(clientID, client);
                }
            }
            if(!globalRoomList.isEmpty()){
                for (int j = 0; j< globalRoomList.size(); j++){
                    ArrayList<ClientPojo> clientsList = new ArrayList<>();
                    for (String clientID: globalRoomClientsList.get(j)){
                        ClientPojo client = new ClientPojo();
                        client.setIdentity(clientID);
                        clientsList.add(client);
                    }
                    Group room = new Group(globalRoomList.get(j),globalRoomServersList.get(j), globalRoomOwnersList.get(j), clientsList);
                    LeaderState.getInstance().globalRoomList.put(globalRoomList.get(j), room);
                }
            }
        }
    }

    public void setRichNeighborData(ServerInfo richNeighborData) {
        this.richNeighborData = richNeighborData;
    }

    public void setIsIgnorant(boolean isIgnorant) {
        this.isIgnorant = isIgnorant;
    }

    public synchronized void setGlobalRoomList(ArrayList<String> globalRoomList) {
        this.globalRoomList = globalRoomList;
    }

    public synchronized void setGlobalRoomServersList(ArrayList<String> globalRoomServersList) {
        this.globalRoomServersList = globalRoomServersList;
    }

    public synchronized void setGlobalRoomOwnersList(ArrayList<String> globalRoomOwnersList) {
        this.globalRoomOwnersList = globalRoomOwnersList;
    }

    public synchronized void setGlobalRoomClientsList(ArrayList<ArrayList<String>> globalRoomClientsList) {
        this.globalRoomClientsList = globalRoomClientsList;
    }

    public synchronized void setGlobalClientIDs(ArrayList<String> globalClientId) {
        this.globalClientId = globalClientId;
    }

    public synchronized void compareAndSetGlobalRoomClientsList(ArrayList<ArrayList<String>> globalRoomClientsList) {
        for (ArrayList<String> clientList: globalRoomClientsList) {
            if (!this.globalRoomClientsList.contains(clientList)) {
                this.globalRoomClientsList.add(clientList);
            }
        }
    }

//    public synchronized List<ServerData> getHigherServerInfo() {
//        return new ArrayList<>(higherPriorityServers.values());
//    }
//
//    public synchronized List<ServerData> getLowerServerInfo() { return new ArrayList<>(lowerPriorityServers.values());
//    }

    public synchronized void setServerState(List<ServerInfo> serversList, String myServerId) {
        for (ServerInfo server : serversList) {
            if (!server.getServerID().equals(myServerId)) {
                System.out.println("Add server: " + server.getServerID());
                addServer(server, myServerId);
            } else {
                this.currentServerData = server;
            }
        }
    }


    public synchronized void addServer(ServerInfo externalServer, String myServerId) {
//        if (compare(myServerId, externalServer.getServerID()) < 0) {
//            higherServerInfo.put(externalServer.getServerId(), externalServer);
//        } else {
////            lowerServerInfo.put(myServerId, externalServer);
//            lowerServerInfo.put(externalServer.getServerId(), externalServer);
//        }
        serversList.put(externalServer.getServerID(), externalServer);

    }

//    private int compare(String myServerId, String externalServerId) {
//        if (null != myServerId && null != externalServerId) {
//            Integer server1Id = Integer.parseInt(myServerId.substring(1));
//            Integer server2Id = Integer.parseInt(externalServerId.substring(1));
//            return server1Id - server2Id;
//        }
//        return 0;
//    }

//    public synchronized boolean isCoordinator(){
//        return currentServerData.equals(coordinatorServerData);
//    }

    public void addNewRoom(Group room) {
        roomList.put(room.getRoomID(), room);
        logger.info("Main Hall Created");
    }

    public void addClientToRoom(String roomID, ClientPojo client){
        roomList.get(roomID).addClient(client);
    }

    public void removeClientFromRoom(String roomID, ClientPojo client){
        roomList.get(roomID).removeClient(client);
    }

    public String removeClientFromRoomWithFormerRoom(ClientPojo client) {
        for (Group room: roomList.values()){
            if (room.getClients().contains(client)){
                String formerRoom = room.getRoomID();
                room.removeClientByClienID(client.getIdentity());
                return formerRoom;
            }
        }
        return null;
    }

    public Group getOwningRoom(String clientID) {
        for (Group room: roomList.values()){
            if (Objects.equals(room.getOwner(), clientID)){
                return room;
            }
        }
        return null;
    }

    // get all client threads in a room associated with a given client
    public List<MessagingService> getClientServicesInRoomByClient(ClientPojo sender){
        for (Group room: roomList.values()){
            if (room.getClients().contains(sender)){
                List<String> roomClients = room.getClients().stream().map(ClientPojo::getIdentity)
                        .filter(c -> !Objects.equals(c, sender.getIdentity())).toList();
                return roomClients.stream().map(clientServices::get).filter(Objects::nonNull).toList();
            }
        }
        return null;
    }

}
