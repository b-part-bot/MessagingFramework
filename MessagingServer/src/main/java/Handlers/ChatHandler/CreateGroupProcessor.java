package Handlers.ChatHandler;

import Handlers.CoordinationHandler.GossipProcessor;
import Handlers.CoordinationHandler.RequestHandler;
import Models.ClientPojo;
import Models.Group;
import Models.Server.LeaderState;
import Models.Server.ServerInfo;
import Models.Server.StatusHandler;
import Services.MessageTransferServ;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CreateGroupProcessor {

    private final Logger logger = Logger.getLogger(Identity.class);
    private final MessageResponseHandler clientResponseHandler;
    private final RequestHandler serverRequestHandler = new RequestHandler();
    private final GossipProcessor gossipHandler = new GossipProcessor();

    public CreateGroupProcessor(MessageResponseHandler clientResponseHandler){
        this.clientResponseHandler = clientResponseHandler;
    }

    public boolean checkOwnerUnique(String owner){
        boolean isUniqueOwner = true;
        for (Iterator<Group> it = StatusHandler.getServerStateInstance().roomList.elements().asIterator(); it.hasNext(); ) {
            Group room = it.next();
            if (Objects.equals(room.getOwner(), owner)){
                isUniqueOwner = false;
            }
        }
        return isUniqueOwner;
    }

    public boolean checkRoomIdRules(String identity){
        boolean isIdentityGood;
        isIdentityGood = identity!= null && identity.matches("^[a-zA-Z][a-zA-Z0-9]*$")
                && identity.length()>=3
                && identity.length()<16;
        return isIdentityGood;
    }

    public String checkRoomIdUnique(String identity, String clientID){

        String isIdentityUnique = "true";
        ServerInfo currentServer = StatusHandler.getServerStateInstance().getCurrentServerData();
        ServerInfo leaderServer = StatusHandler.getServerStateInstance().getLeaderServerData();
        if (Objects.equals(currentServer.getServerID(), leaderServer.getServerID())){
            ConcurrentHashMap<String, Group> rooms = LeaderState.getInstance().getGlobalRoomList();
            for (Iterator<String> it = rooms.keys().asIterator(); it.hasNext(); ) {
                String room = it.next();
                if (Objects.equals(room, identity)){
                    isIdentityUnique = "false";
                }
            }
        } else {
            JSONObject request = serverRequestHandler.sendCreateRoomResponse(identity, clientID);
            MessageTransferServ.sendToServers(request, leaderServer.getServerAddress(), leaderServer.getCoordinationPort());
            isIdentityUnique = "askedFromLeader";
        }

        return isIdentityUnique;
    }

    public JSONObject moveToNewRoom(Group room, ClientPojo client){
        JSONObject response;
        String roomID = room.getRoomID();
        String formerID = new ClientListInGroupProcessor().getClientsRoomID(client.getIdentity());
        StatusHandler.getServerStateInstance().addClientToRoom(roomID, client);
        StatusHandler.getServerStateInstance().removeClientFromRoom(formerID, client);
        StatusHandler.getServerStateInstance().getGlobalRoomList().add(roomID);
        StatusHandler.getServerStateInstance().getGlobalRoomServersList().add(room.getServer());
        StatusHandler.getServerStateInstance().getGlobalRoomOwnersList().add(room.getOwner());
        ArrayList<String> roomclientids = new ArrayList<String>();
        for (ClientPojo roomclient : room.getClients()) {
            roomclientids.add(roomclient.getIdentity());
        }
        StatusHandler.getServerStateInstance().getGlobalRoomClientsList().add(roomclientids);
        LeaderState.getInstance().globalRoomList.put(roomID, room);
        response = clientResponseHandler.moveToRoomResponse(client.getIdentity(), formerID, roomID);
        return response;
    }

    public Map<String, JSONObject> createRoom(ClientPojo client, String roomid){
        Map<String, JSONObject> responses = new HashMap<>();
        boolean checkRoomIdRules = checkRoomIdRules(roomid);
        boolean checkOwnerUnique = checkOwnerUnique(client.getIdentity());
        ArrayList<ClientPojo> clients = new ArrayList<>();
        if (checkRoomIdRules && checkOwnerUnique) {
            String checkRoomIdUnique = checkRoomIdUnique(roomid, client.getIdentity());
            if (checkRoomIdUnique.equals("true")){
                Group room = new Group(roomid, System.getProperty("serverID"), client.getIdentity(), clients);
                StatusHandler.getServerStateInstance().roomList.put(roomid, room);
                logger.info("New room creation accepted");
                responses.put("client-only", clientResponseHandler.sendNewRoomResponse(roomid, "true"));
                responses.put("broadcast", moveToNewRoom(room, client));
                JSONObject gossipMsg = this.gossipHandler.gossipRoom("gossiproom", System.getProperty("serverID"), LeaderState.getInstance().getGlobalRoomList());
                responses.put("gossip", gossipMsg);
            } else if(checkRoomIdUnique.equals("askedFromLeader")){
                logger.info("Asked from leader");
                responses.put("askedFromLeader", null);
            } else {
                logger.info("New room creation rejected");
                responses.put("client-only", clientResponseHandler.sendNewRoomResponse(roomid, "false"));
            }
        } else {
            logger.info("New room creation rejected");
            responses.put("client-only", clientResponseHandler.sendNewRoomResponse(roomid, "false"));
        }
        return responses;
    }
}
