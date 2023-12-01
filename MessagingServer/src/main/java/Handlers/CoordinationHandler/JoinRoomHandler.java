package Handlers.CoordinationHandler;

import Handlers.ChatHandler.ClientListInGroupProcessor;
import Handlers.ChatHandler.MessageResponseHandler;
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

public class JoinRoomHandler {
    private final Logger logger = Logger.getLogger(Handlers.ChatHandler.Identity.class);
    private final RequestHandler serverRequestHandler = new RequestHandler();
    private final ResponseHandler serverResponseHandler = new ResponseHandler();
    private final MessageResponseHandler clientResponseHandler = new MessageResponseHandler();
    ClientListInGroupProcessor clientListInRoomHandler= new ClientListInGroupProcessor();
    String serverid = "";

    public boolean checkRoomIdExist(String identity){

        boolean isIdentityExist = false;
        ServerInfo currentServer = StatusHandler.getServerStateInstance().getCurrentServerData();
        ServerInfo leaderServer = StatusHandler.getServerStateInstance().getLeaderServerData();
        if (Objects.equals(currentServer.getServerID(), leaderServer.getServerID())){
            ConcurrentHashMap<String, Group> rooms = LeaderState.getInstance().getGlobalRoomList();
            for (Iterator<String> it = rooms.keys().asIterator(); it.hasNext(); ) {
                String room = it.next();
                if (Objects.equals(room, identity)){
                    isIdentityExist = true;
                }
            }
        }
        return isIdentityExist;
    }

    public boolean getJoinRoomServerData(String roomid) {
        Boolean isJoinRoomServerDataExist = false;
        ServerInfo currentServer = StatusHandler.getServerStateInstance().getCurrentServerData();
        ServerInfo leaderServer = StatusHandler.getServerStateInstance().getLeaderServerData();
        if (Objects.equals(currentServer.getServerID(), leaderServer.getServerID())){
            serverid = LeaderState.getInstance().getGlobalRoomList().get(roomid).getServer();
            isJoinRoomServerDataExist = true;
        }
        return isJoinRoomServerDataExist;
    }

    public String getJoinRoomServerData(String roomid, String clientID) {
        String isJoinRoomServerDataExist = "false";
        ServerInfo currentServer = StatusHandler.getServerStateInstance().getCurrentServerData();
        ServerInfo leaderServer = StatusHandler.getServerStateInstance().getLeaderServerData();
        if (Objects.equals(currentServer.getServerID(), leaderServer.getServerID())){
            serverid = LeaderState.getInstance().getGlobalRoomList().get(roomid).getServer();
            isJoinRoomServerDataExist = "true";
        } else {
            JSONObject request = serverRequestHandler.sendJoinRoomResponse(roomid, clientID);
            MessageTransferServ.sendToServers(request, leaderServer.getServerAddress(), leaderServer.getCoordinationPort());
            isJoinRoomServerDataExist = "askedFromLeader";
        }
        return isJoinRoomServerDataExist;
    }

    public JSONObject moveToNewRoom(String former, String roomid, ClientPojo client){
        JSONObject response;
        StatusHandler.getServerStateInstance().addClientToRoom(roomid, client);
        StatusHandler.getServerStateInstance().removeClientFromRoom(former, client);
        response = clientResponseHandler.moveToRoomResponse(client.getIdentity(), former, roomid);
        return response;
    }

    public JSONObject coordinatorRoomExist(String clientID, String roomID) {
        JSONObject response;
        if (checkRoomIdExist(roomID)) {
            logger.info("Join room - room id exist");
            response = this.serverResponseHandler.sendRoomExistResponse(roomID, "true", clientID);
        } else {
            logger.info("Join room - room id not exist");
            response = this.serverResponseHandler.sendRoomExistResponse(roomID, "false", clientID);
        }
        return response;
    }

    public JSONObject coordinatorRoomRoute(String clientID, String roomID) {
        JSONObject response;
        ServerInfo serverData = null;
        String host = "";
        String port = "";
        if (getJoinRoomServerData(roomID)) {
            logger.info("leader accepted - Get room route");
            if (StatusHandler.getServerStateInstance().getServersList().containsKey(serverid)){
                serverData = StatusHandler.getServerStateInstance().getServersList().get(serverid);
                host = serverData.getServerAddress();
                port = Integer.toString(serverData.getClientPort());
            } else {
                serverData = StatusHandler.getServerStateInstance().getCurrentServerData();
                host = serverData.getServerAddress();
                port = Integer.toString(serverData.getClientPort());
            }
            response = this.serverResponseHandler.sendGetRoomRouteResponse("true", roomID, host, port, clientID);
        } else {
            logger.info("leader rejected - Get room route");
            response = this.serverResponseHandler.sendGetRoomRouteResponse("false", roomID, host, port, clientID);
        }
        return response;
    }


    public Map<String, JSONObject> leaderApprovedRoomExist(String isExist, ClientPojo client, String roomid){
        Map<String, JSONObject> responses = new HashMap<>();
        if(isExist.equals("true")){
            String currentChatRoom = clientListInRoomHandler.getClientsRoomID(client.getIdentity());
            Group newRoom = StatusHandler.getServerStateInstance().roomList.get(roomid);
            if (newRoom !=null) {
                logger.info("Join room within same server is accepted");
                JSONObject roomChangedResponse = moveToNewRoom(currentChatRoom, roomid, client);
                responses.put("broadcast-all",roomChangedResponse);
            }
            else {
                String getJoinRoomServerData = getJoinRoomServerData(roomid, client.getIdentity());
                if (getJoinRoomServerData.equals("true")) {
                    ServerInfo serverData = StatusHandler.getServerStateInstance().getServersList().get(serverid);
                    String host = serverData.getServerAddress();
                    String port = Integer.toString(serverData.getClientPort());
                    JSONObject routeResponse = this.clientResponseHandler.sendNewRouteMessage(roomid, host, port);
                    JSONObject roomChangeResponse = this.clientResponseHandler.broadCastRoomChange(client.getIdentity(), currentChatRoom, roomid);
                    StatusHandler.getServerStateInstance().roomList.get(currentChatRoom).removeClient(client);
                    StatusHandler.getServerStateInstance().clients.remove(client.getIdentity());

                    responses.put("client-only",routeResponse);
                    responses.put("broadcast-former",roomChangeResponse);
                } else if (getJoinRoomServerData.equals("askedFromLeader")) {
                    logger.info("Asked from leader");
                    responses.put("askedFromLeader", null);
                }
            }
        } else if (isExist.equals("false")){
            logger.info("Join room rejected - room not exist");
            JSONObject roomChangedResponse = this.clientResponseHandler.broadCastRoomChange(client.getIdentity(),roomid,roomid);
            responses.put("client-only",roomChangedResponse);
        }
        return responses;
    }
    public Map<String, JSONObject> leaderApprovedRoomRoute(String isExist, ClientPojo client, String roomid, String host, String port){
        System.out.println("leader approved room route");
        Map<String, JSONObject> responses = new HashMap<>();
        if (isExist.equals("true")) {
            String currentChatRoom = clientListInRoomHandler.getClientsRoomID(client.getIdentity());
            JSONObject routeResponse = this.clientResponseHandler.sendNewRouteMessage(roomid, host, port);
            JSONObject roomChangeResponse = this.clientResponseHandler.broadCastRoomChange(client.getIdentity(), currentChatRoom, roomid);
            StatusHandler.getServerStateInstance().roomList.get(currentChatRoom).removeClient(client);
            StatusHandler.getServerStateInstance().clients.remove(client.getIdentity());

            responses.put("client-only",routeResponse);
            responses.put("broadcast-former",roomChangeResponse);
        }
        return responses;
    }
}
