package Handlers.ChatHandler;

import Handlers.CoordinationHandler.GossipProcessor;
import Handlers.CoordinationHandler.RequestHandler;
import Handlers.CoordinationHandler.ResponseHandler;
import Models.ClientPojo;
import Models.Group;
import Models.Server.LeaderState;
import Models.Server.ServerInfo;
import Models.Server.StatusHandler;
import Services.MessageTransferServ;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class QuitHandler {
    private final Logger logger = Logger.getLogger(Identity.class);
    private final MessageResponseHandler clientResponseHandler;
    private final ResponseHandler serverResponseHandler = new ResponseHandler();
    private final RequestHandler serverRequestHandler = new RequestHandler();
    private final GossipProcessor gossipHandler = new GossipProcessor();

    public QuitHandler(MessageResponseHandler clientResponseHandler) {
        this.clientResponseHandler = clientResponseHandler;
    }

    public Map<String, ArrayList<JSONObject>> handleQuit(ClientPojo client) {

        Map<String, ArrayList<JSONObject>> responses = new HashMap<>();
        ArrayList<JSONObject> broadcastResponse = new ArrayList<>();
        ArrayList<JSONObject> clientOnlyResponse = new ArrayList<>();
        ArrayList<JSONObject> replyResponse = new ArrayList<>();
        ArrayList<JSONObject> broadcastServerResponse = new ArrayList<>();
        ArrayList<JSONObject> gossipResponse = new ArrayList<>();
        String clientID = client.getIdentity();
        StatusHandler.getServerStateInstance().clients.remove(clientID); // remove the client from the client list
        if (StatusHandler.getServerStateInstance().isCurrentServerLeader()) {
            LeaderState.getInstance().globalClients.remove(clientID);
            JSONObject gossipMsg = this.gossipHandler.gossip("gossipidentity", System.getProperty("serverID"), LeaderState.getInstance().globalClients);
            gossipResponse.add(gossipMsg);
            responses.put("gossip", gossipResponse);
        } else {
            ServerInfo leaderServer = StatusHandler.getServerStateInstance().getLeaderServerData();
            MessageTransferServ.sendToServers(serverRequestHandler.sendQuitClientResponse(clientID), leaderServer.getServerAddress(), leaderServer.getCoordinationPort());
        }
        Group deleteRoom = StatusHandler.getServerStateInstance().getOwningRoom(clientID);
        if (deleteRoom == null){
            String formerRoom = StatusHandler.getServerStateInstance().removeClientFromRoomWithFormerRoom(client); // delete client from room
            clientOnlyResponse.add(this.clientResponseHandler.broadCastRoomChange(clientID, formerRoom, ""));
            responses.put("client-only", clientOnlyResponse);
        } else {
            ArrayList<ClientPojo> deleteRoomClients = deleteRoom.getClients();
            deleteRoomClients.remove(client);
            //delete room
            StatusHandler.getServerStateInstance().roomList.remove(deleteRoom.getRoomID());
            JSONObject serverBroadcastResponse = serverResponseHandler.deleteRoomServerRespond(System.getProperty("serverID"), deleteRoom.getRoomID());
            broadcastServerResponse.add(serverBroadcastResponse);
            responses.put("broadcastServers", broadcastServerResponse);
            // Move to PrimaryGroup
            StatusHandler.getServerStateInstance().roomList.get("PrimaryGroup-"+System.getProperty("serverID")).addClientList(deleteRoomClients);
            for(ClientPojo movingClient: deleteRoomClients){
                JSONObject broadcastRoomChange = this.clientResponseHandler
                        .broadCastRoomChange(movingClient.getIdentity(), "deletedRoom", "PrimaryGroup-"+System.getProperty("serverID"));
                broadcastResponse.add(broadcastRoomChange);
            }
            replyResponse.add(this.clientResponseHandler
                    .broadCastRoomChange(client.getIdentity(), "deletedRoom", ""));
            responses.put("reply", replyResponse);
            responses.put("broadcastClients", broadcastResponse);
        }
        return responses;
    }
}
