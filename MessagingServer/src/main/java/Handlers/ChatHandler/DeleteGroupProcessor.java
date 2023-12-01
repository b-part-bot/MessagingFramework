package Handlers.ChatHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.json.simple.JSONObject;

import Handlers.CoordinationHandler.GossipProcessor;
import Handlers.CoordinationHandler.ResponseHandler;
import Models.ClientPojo;
import Models.Group;
import Models.Server.LeaderState;
import Models.Server.StatusHandler;

public class DeleteGroupProcessor {
    private final MessageResponseHandler clientResponseHandler;
    private final ResponseHandler serverResponseHandler = new ResponseHandler();
    private final GossipProcessor gossipHandler = new GossipProcessor();

    public DeleteGroupProcessor(MessageResponseHandler clientResponseHandler){
        this.clientResponseHandler = clientResponseHandler;
    }

    public Map<String, ArrayList<JSONObject>> deleteRoom(String roomID, ClientPojo client){
        Map<String, ArrayList<JSONObject>> responses = new HashMap<>();
        ConcurrentMap<String, Group> roomList = StatusHandler.getServerStateInstance().getRoomList();
        ArrayList<JSONObject> broadcastClientResponse = new ArrayList<>();
        ArrayList<JSONObject> clientOnlyResponse = new ArrayList<>();
        ArrayList<JSONObject> broadcastServerResponse = new ArrayList<>();
        ArrayList<JSONObject> gossipResponse = new ArrayList<>();
        if(roomList.containsKey(roomID)){
            Group deleteRoom = roomList.get(roomID);
            if(deleteRoom.getOwner().equals(client.getIdentity())){
                ArrayList<ClientPojo> deleteRoomClients = deleteRoom.getClients();
                StatusHandler.getServerStateInstance().roomList.remove(deleteRoom.getRoomID());
                JSONObject serverBroadcastRespond = serverResponseHandler.deleteRoomServerRespond(System.getProperty("serverID"), roomID);
                broadcastServerResponse.add(serverBroadcastRespond);
                responses.put("broadcastServers", broadcastServerResponse);

                StatusHandler.getServerStateInstance().roomList.get("PrimaryGroup-"+System.getProperty("serverID")).addClientList(deleteRoomClients);
                for(ClientPojo movingClient: deleteRoomClients){
                    JSONObject broadcastRoomChange = this.clientResponseHandler
                            .broadCastRoomChange(movingClient.getIdentity(), "deletedRoom", "PrimaryGroup-"+System.getProperty("serverID"));
                    broadcastClientResponse.add(broadcastRoomChange);
                }
                JSONObject approveResponse = this.clientResponseHandler.deleteRoomResponse(roomID, "true");
                responses.put("broadcastClients", broadcastClientResponse);
                clientOnlyResponse.add(approveResponse);
                responses.put("client-only", clientOnlyResponse);

                String currentServer = StatusHandler.getServerStateInstance().getCurrentServerData().getServerID();
                String leaderserver = StatusHandler.getServerStateInstance().getLeaderServerData().getServerID();
                if (currentServer.equals(leaderserver)) {
                    LeaderState.getInstance().getGlobalRoomList().remove(roomID);
                    JSONObject gossipMsg = this.gossipHandler.gossipRoom("gossiproom", System.getProperty("serverID"), LeaderState.getInstance().globalRoomList);
                    gossipResponse.add(gossipMsg);
                    responses.put("gossip", gossipResponse);
                }
                return responses;
            }
        }
        // Response to reject
        JSONObject rejectResponse = this.clientResponseHandler.deleteRoomResponse(roomID, "false");
        clientOnlyResponse.add(rejectResponse);
        responses.put("client-only", clientOnlyResponse);
        return responses;

    }
}
