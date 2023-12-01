package Handlers.ChatHandler;

import Handlers.CoordinationHandler.RequestHandler;

import Models.Group;
import Models.Server.LeaderState;
import Models.Server.ServerInfo;
import Models.Server.StatusHandler;
import Services.MessageTransferServ;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class GroupListHandler {

    private final Logger logger = Logger.getLogger(Identity.class);
    private final MessageResponseHandler clientResponseHandler;
    private final RequestHandler serverRequestHandler = new RequestHandler();

    public GroupListHandler(MessageResponseHandler clientResponseHandler) {
        this.clientResponseHandler = clientResponseHandler;

    }

    @SuppressWarnings("unchecked")
    public JSONObject getRoomList(String clientID) {
        JSONObject roomListResponse = new JSONObject();
        ArrayList<String> roomList = new ArrayList<>();
        ServerInfo leaderServer = StatusHandler.getServerStateInstance().getLeaderServerData();
        if (StatusHandler.getServerStateInstance().isCurrentServerLeader()) {
            ConcurrentHashMap<String, Group> rooms = LeaderState.getInstance().getGlobalRoomList();

            for (Iterator<String> it = rooms.keys().asIterator(); it.hasNext(); ) {
                String roomID = it.next();
                logger.info(roomID);
                roomList.add(roomID);
            }
            roomListResponse.put("type", "roomlist");
            roomListResponse.put("rooms", roomList);

        } else {
            JSONObject request = serverRequestHandler.createAllRoomsRequest(clientID);
            MessageTransferServ.sendToServers(request, leaderServer.getServerAddress(), leaderServer.getCoordinationPort());
            logger.info("send to leader");
        }

        logger.info("room List : " + roomList);
        return roomListResponse;
    }


}