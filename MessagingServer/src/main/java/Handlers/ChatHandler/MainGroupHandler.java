package Handlers.ChatHandler;

import java.util.ArrayList;
import java.util.Objects;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import Handlers.CoordinationHandler.GossipProcessor;
import Handlers.CoordinationHandler.RequestHandler;
import Models.Group;
import Models.Server.LeaderState;
import Models.Server.ServerInfo;
import Models.Server.StatusHandler;
import Services.MessageTransferServ;
import Services.CoordinationService.GossipService;

public class MainGroupHandler extends Thread{

    Logger logger = Logger.getLogger(MainGroupHandler.class);
    private final GossipProcessor gossipHandler = new GossipProcessor();

    public void run(){
        while (true) {
            if (StatusHandler.getServerStateInstance().getLeaderServerData() != null) {
                createPrimaryGroup();
                break;
            }
        }
    }

    public void createPrimaryGroup() {
        ServerInfo currentServer = StatusHandler.getServerStateInstance().getCurrentServerData();
        ServerInfo leaderServer = StatusHandler.getServerStateInstance().getLeaderServerData();
        String serverID = System.getProperty("serverID");
        String roomID = "PrimaryGroup-" + serverID;
        Group PrimaryGroup = new Group(roomID, serverID, "", new ArrayList<>());
        StatusHandler.getServerStateInstance().roomList.put(roomID, PrimaryGroup);
        StatusHandler.getServerStateInstance().getGlobalRoomList().add(roomID);
        StatusHandler.getServerStateInstance().getGlobalRoomServersList().add(System.getProperty("serverID"));
        StatusHandler.getServerStateInstance().getGlobalRoomOwnersList().add("");
        ArrayList<String> roomclientids = new ArrayList<String>();
        StatusHandler.getServerStateInstance().getGlobalRoomClientsList().add(roomclientids);
        if (Objects.equals(currentServer.getServerID(), leaderServer.getServerID())) {
            LeaderState.getInstance().globalRoomList.put(roomID, PrimaryGroup);
            logger.info("Main Hall Created");
            // System.out.println(LeaderState.getInstance().getGlobalRoomList());
            JSONObject gossipMsg = this.gossipHandler.gossipRoom("gossiproom", System.getProperty("serverID"), LeaderState.getInstance().getGlobalRoomList());
            Thread gossipService = new GossipService("send", "gossiproom", gossipMsg);
            gossipService.start();
        } else {
            MessageTransferServ.sendToServers(new RequestHandler().sendPrimaryGroupResponse(PrimaryGroup.getRoomID()), leaderServer.getServerAddress(), leaderServer.getCoordinationPort());
        }
    }
}
