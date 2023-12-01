package Handlers.ChatHandler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import Handlers.CoordinationHandler.RequestHandler;
import Models.ClientPojo;
import Models.Group;
import Models.Server.LeaderState;
import Models.Server.ServerInfo;
import Models.Server.StatusHandler;
import Services.MessageTransferServ;

public class JoinGroupProcessor {

    ClientListInGroupProcessor clientListInRoomHandler= new ClientListInGroupProcessor();
    private final Logger logger = Logger.getLogger(Identity.class);
    private final MessageResponseHandler clientResponseHandler;
    String currentChatRoom = "";
    private final RequestHandler serverRequestHandler = new RequestHandler();
    String serverid = "";

    public JoinGroupProcessor(MessageResponseHandler clientResponseHandler){
        this.clientResponseHandler = clientResponseHandler;
    }

    public String checkRoomIdExist(String identity, String clientID){

        String isIdentityExist = "false";
        ServerInfo currentServer = StatusHandler.getServerStateInstance().getCurrentServerData();
        ServerInfo leaderServer = StatusHandler.getServerStateInstance().getLeaderServerData();
        if (Objects.equals(currentServer.getServerID(), leaderServer.getServerID())){
            ConcurrentHashMap<String, Group> rooms = LeaderState.getInstance().getGlobalRoomList();
            for (Iterator<String> it = rooms.keys().asIterator(); it.hasNext(); ) {
                String room = it.next();
                if (Objects.equals(room, identity)){
                    isIdentityExist = "true";
                }
            }
        } else {
            JSONObject request = serverRequestHandler.sendRoomExistResponse(identity, clientID);
            MessageTransferServ.sendToServers(request, leaderServer.getServerAddress(), leaderServer.getCoordinationPort());
            isIdentityExist = "askedFromLeader";
        }
        return isIdentityExist;
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

    public boolean checkClientisOwner(String clientid, String roomid){

        boolean isNotowner = true;
        String roomOwner = clientListInRoomHandler.getRoomOwner(roomid);

        if (roomOwner.equals(clientid)) {
            isNotowner = false;
        }
        return isNotowner;
    }

    public boolean checkRoomsinSameServer(String currentRoomServer, String newRoomServer){

        return currentRoomServer.equals(newRoomServer);
    }

    public JSONObject moveToNewRoom(String former, String roomid, ClientPojo client){
        JSONObject response;
        StatusHandler.getServerStateInstance().addClientToRoom(roomid, client);
        StatusHandler.getServerStateInstance().removeClientFromRoom(former, client);
        response = clientResponseHandler.moveToRoomResponse(client.getIdentity(), former, roomid);
        return response;
    }

    public Map<String, JSONObject> joinRoom(ClientPojo client, String roomid){
        Map<String, JSONObject> responses = new HashMap<>();
        currentChatRoom = clientListInRoomHandler.getClientsRoomID(client.getIdentity());
        String currentRoomOwner = clientListInRoomHandler.getRoomOwner(currentChatRoom);
        String currentRoomServer = StatusHandler.getServerStateInstance().roomList.get(currentChatRoom).getServer();

        if (!currentRoomOwner.equals(client.getIdentity())) {
            String isRoomExist = checkRoomIdExist(roomid, client.getIdentity());
            if (isRoomExist.equals("true")) {
                //String newRoomServer = ServerState.getServerStateInstance().roomList.get(roomid).getServer();
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
                        StatusHandler.getServerStateInstance().removeClientFromRoom(currentChatRoom, client);
                        StatusHandler.getServerStateInstance().clients.remove(client.getIdentity());
                        responses.put("client-only",routeResponse);
                        responses.put("broadcast-former",roomChangeResponse);
                    } else if (getJoinRoomServerData.equals("askedFromLeader")) {
                        logger.info("Asked from leader");
                        responses.put("askedFromLeader", null);
                    }
                }
            } else if (isRoomExist.equals("askedFromLeader")) {
                logger.info("Asked from leader");
                responses.put("askedFromLeader", null);
            } else {
                logger.info("Join room rejected - room not exist");
                JSONObject roomChangedResponse = this.clientResponseHandler.broadCastRoomChange(client.getIdentity(),roomid,roomid);
                responses.put("client-only",roomChangedResponse);
            }
        } else {
            logger.info("Join room rejected - Current room owner");
            JSONObject roomChangedResponse = this.clientResponseHandler.broadCastRoomChange(client.getIdentity(),roomid,roomid);
            responses.put("client-only",roomChangedResponse);
        }
        return responses;
    }

}
