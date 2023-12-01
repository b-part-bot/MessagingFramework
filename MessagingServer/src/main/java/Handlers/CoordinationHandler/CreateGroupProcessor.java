package Handlers.CoordinationHandler;

import Handlers.ChatHandler.ClientListInGroupProcessor;
import Handlers.ChatHandler.MessageResponseHandler;
import Models.ClientPojo;
import Models.Group;
import Models.Server.LeaderState;
import Models.Server.ServerInfo;
import Models.Server.StatusHandler;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CreateGroupProcessor {

    private final Logger logger = Logger.getLogger(Handlers.ChatHandler.Identity.class);
    private final RequestHandler serverRequestHandler = new RequestHandler();
    private final ResponseHandler serverResponseHandler = new ResponseHandler();
    private final MessageResponseHandler clientResponseHandler = new MessageResponseHandler();
    private final GossipProcessor gossipHandler = new GossipProcessor();

    public boolean checkRoomIdUnique(String identity){

        boolean isIdentityUnique = true;
        ServerInfo currentServer = StatusHandler.getServerStateInstance().getCurrentServerData();
        ServerInfo leaderServer = StatusHandler.getServerStateInstance().getLeaderServerData();
        if (Objects.equals(currentServer.getServerID(), leaderServer.getServerID())) {
            ConcurrentHashMap<String, Group> rooms = LeaderState.getInstance().getGlobalRoomList();
            for (Iterator<String> it = rooms.keys().asIterator(); it.hasNext(); ) {
                String room = it.next();
                if (Objects.equals(room, identity)) {
                    isIdentityUnique = false;
                }
            }
        }
        return isIdentityUnique;
    }

    public JSONObject moveToNewRoom(Group room, ClientPojo client){
        JSONObject response;
        String roomID = room.getRoomID();
        String formerID = new ClientListInGroupProcessor().getClientsRoomID(client.getIdentity());
        StatusHandler.getServerStateInstance().addClientToRoom(roomID, client);
        StatusHandler.getServerStateInstance().removeClientFromRoom(formerID, client);
        response = clientResponseHandler.moveToRoomResponse(client.getIdentity(), formerID, roomID);
        return response;
    }

    public Map<String, JSONObject> coordinatorNewRoomIdentity(String clientID, String roomid, String serverID) {
        Map<String, JSONObject> responses = new HashMap<>();
        JSONObject response;
        if (checkRoomIdUnique(roomid)) {
            ArrayList<ClientPojo> clients = new ArrayList<>();
            ClientPojo client = new ClientPojo();
            client.setServer(serverID);
            client.setIdentity(clientID);
            client.setStatus("active");
            clients.add(client);
            Group room = new Group(roomid, serverID, clientID, clients);
            LeaderState.getInstance().addRoomToGlobalList(room);
            logger.info("New room creation accepted");
            response = this.serverResponseHandler.sendCreateRoomServerResponse("true", roomid, clientID);
            responses.put("response", response);
            JSONObject gossipMsg = this.gossipHandler.gossipRoom("gossiproom", System.getProperty("serverID"), LeaderState.getInstance().getGlobalRoomList());
            responses.put("gossip", gossipMsg);
        } else {
            logger.info("New room creation rejected");
            response = this.serverResponseHandler.sendCreateRoomServerResponse("false", roomid, clientID);
            responses.put("response", response);
        }
        return responses;
    }

    public Map<String, JSONObject> leaderApprovedNewRoomIdentity(String isApproved, ClientPojo client, String roomid){
        Map<String, JSONObject> responses = new HashMap<>();
        if(isApproved.equals("true")){
            logger.info("New room creation accepted");
            ArrayList<ClientPojo> clients = new ArrayList<>();
            Group room = new Group(roomid, System.getProperty("serverID"), client.getIdentity(), clients);
            StatusHandler.getServerStateInstance().roomList.put(roomid, room);
            responses.put("client-only", clientResponseHandler.sendNewRoomResponse(roomid, "true"));
            responses.put("broadcast", moveToNewRoom(room, client));
        }else if(isApproved.equals("false")){
            logger.info("New room creation rejected");
            JSONObject createRoomResponse = this.clientResponseHandler.sendNewRoomResponse(roomid, "false");
            responses.put("client-only",createRoomResponse);
        }
        return responses;
    }
}