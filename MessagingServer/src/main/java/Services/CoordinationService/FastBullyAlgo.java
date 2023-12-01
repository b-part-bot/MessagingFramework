package Services.CoordinationService;

import Handlers.CoordinationHandler.BullyProcessor;
import Models.Server.ServerInfo;
import Models.Server.StatusHandler;
import Services.MessageTransferServ;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

public class FastBullyAlgo extends Thread {

    private static final Logger logger = Logger.getLogger(FastBullyAlgo.class);
    private final String operation; // wait or send message
    private String request;
    private JSONObject reply;
    private static final BullyProcessor messageHandler = new BullyProcessor();
    /**
     * Following variables are visible to all threads (since static) and
     * any read or write operation on them will be visible to all other threads in the class atomically (since volatile).
     **/
    private static volatile boolean electionInProgress = false;
    private static volatile List<JSONObject> viewMessagesReceived;
    private static volatile List<JSONObject> answerMessageReceived;
    private static volatile JSONObject coordinationMessageReceived = null;
    private static volatile JSONObject nominationMessageReceived = null;
    private static volatile boolean nominationStart = false;

    public FastBullyAlgo(String operation, String request) {
        this.operation = operation;
        this.request = request;
    }

    public FastBullyAlgo(String operation) {
        this.operation = operation;
    }

    public void setReply(JSONObject reply) {
        this.reply = reply;
    }

    public String getHighestPriorityServersByID(String currentServerID, List<JSONObject> responses) {
        String highestPriorityServerID = currentServerID;
        for (JSONObject response : responses) {
            String serverID = response.get("serverid").toString();
            if (Integer.parseInt(highestPriorityServerID.substring(1)) > Integer.parseInt(serverID.substring(1))) {
                highestPriorityServerID = serverID;
            }
        }
        return highestPriorityServerID;
    }

    public ArrayList<ServerInfo> getHigherPriorityServers(ServerInfo currentServerData) {
        ConcurrentMap<String, ServerInfo> serverList = StatusHandler.getServerStateInstance().getServersList();
        ArrayList<ServerInfo> higherPriorityServers = new ArrayList<>();
        for (ConcurrentMap.Entry<String, ServerInfo> entry : serverList.entrySet()) {
            if (Integer.parseInt(entry.getKey().substring(1)) < Integer.parseInt(currentServerData.getServerID().substring(1))) {
                higherPriorityServers.add(entry.getValue());
            }
        }
        return higherPriorityServers;
    }

    public ArrayList<ServerInfo> getLowPriorityServers(ServerInfo currentServerData) {
        ConcurrentMap<String, ServerInfo> serverList = StatusHandler.getServerStateInstance().getServersList();
        ArrayList<ServerInfo> lowPriorityServers = new ArrayList<>();
        for (ConcurrentMap.Entry<String, ServerInfo> entry : serverList.entrySet()) {
            if (Integer.parseInt(entry.getKey().substring(1)) > Integer.parseInt(currentServerData.getServerID().substring(1))) {
                lowPriorityServers.add(entry.getValue());
            }
        }
        return lowPriorityServers;
    }

    public void removeNominationSend(String ServerID) {
        for (JSONObject response : answerMessageReceived) {
            if (response.get("serverid").equals(ServerID)) {
                answerMessageReceived.remove(response);
                break;
            }
        }
    }

    public void run() {
        // send messages to other servers corresponding Fast Bully Algorithm.
        switch (operation) {
            case ("heartbeat") -> {
                while (true) {
                    try {
                        StatusHandler serverState = StatusHandler.getServerStateInstance();
                        if (!serverState.getCurrentServerData().getServerID().equals(serverState.getLeaderServerData().getServerID())) {
                            Thread.sleep(1500);
                            StatusHandler newServerState = StatusHandler.getServerStateInstance();
                            MessageTransferServ.leaderKeepAlive(messageHandler.heartBeatMessage(), newServerState.getLeaderServerData().getServerAddress(), newServerState.getLeaderServerData().getCoordinationPort());
                        }
                    } catch (IOException e) {
                        if (!electionInProgress) {
                            electionInProgress = true;
                            answerMessageReceived = Collections.synchronizedList(new ArrayList<>());
                            StatusHandler newServerState = StatusHandler.getServerStateInstance();
                            logger.info("Leader detected as down. Sending Election message");
                            ArrayList<ServerInfo> higherPriorityServers = getHigherPriorityServers(newServerState.getCurrentServerData());
                            MessageTransferServ.sendSpecificBroadcast(higherPriorityServers, messageHandler.electionMessage());
                            FastBullyAlgo fastBullyService = new FastBullyAlgo("wait", "answerMessageWait");
                            fastBullyService.start();
                        }
                    } catch (InterruptedException e) {
                        logger.error("Interrupt Exception occurred. Error Message: " + e.getMessage());
                    }
                }
            }
            case ("wait") -> {
                switch (request) {
                    case ("viewMessageWait") -> {
                        try {
                            Thread.sleep(1000);
                            ServerInfo currentServer = StatusHandler.getServerStateInstance().getCurrentServerData();
                            String currentServerID = currentServer.getServerID();
                            if (viewMessagesReceived.isEmpty()) {
                                logger.info("Becoming leader");
                                StatusHandler.getServerStateInstance().setLeader(currentServer);
                            } else {
                                logger.info("View received messages.");
                                JSONObject message = viewMessagesReceived.get(0);
                                StatusHandler.getServerStateInstance().setGlobalClientIDs((ArrayList<String>) message.get("clientids"));
                                StatusHandler.getServerStateInstance().setGlobalRoomList((ArrayList<String>) message.get("roomids"));
                                StatusHandler.getServerStateInstance().setGlobalRoomServersList((ArrayList<String>) message.get("roomservers"));
                                StatusHandler.getServerStateInstance().setGlobalRoomOwnersList((ArrayList<String>) message.get("roomowners"));
                                StatusHandler.getServerStateInstance().setGlobalRoomClientsList((ArrayList<ArrayList<String>>) message.get("roomclientids"));

                                // Check highest priority server from views
                                String highestPriorityServerID = getHighestPriorityServersByID(currentServerID, viewMessagesReceived);
                                if (highestPriorityServerID.equals(currentServerID)) {
                                    // If Current Server has the highest priority broadcast coordinator message to others
                                    logger.info("Current server become the leader and sending coordinator message");
                                    StatusHandler.getServerStateInstance().setLeader(currentServer);
                                    MessageTransferServ.sendToServersBroadcast(messageHandler.coordinatorMessage()); //broadcast coordinator message
                                } else {
                                    // Else save the highest priority server as leader
                                    logger.info("Leader set to : " + highestPriorityServerID);
                                    ServerInfo leaderData = StatusHandler.getServerStateInstance().getServerDataById(highestPriorityServerID);
                                    StatusHandler.getServerStateInstance().setLeader(leaderData);
                                }
                            }
                            FastBullyAlgo heartBeat = new FastBullyAlgo("heartbeat");
                            heartBeat.start();
                            electionInProgress = false;
                        } catch (InterruptedException e) {
                            logger.error("Exception occurred in wait thread");
                            e.printStackTrace();
                        }
                    }
                    case ("answerMessageWait") -> {
                        try {
                            Thread.sleep(500);
                            if (electionInProgress) {
                                ServerInfo currentServer = StatusHandler.getServerStateInstance().getCurrentServerData();
                                String currentServerID = currentServer.getServerID();
                                if (answerMessageReceived.isEmpty()) {
                                    logger.info("Wait timeout. Initiating leader election process");
                                    StatusHandler.getServerStateInstance().setLeader(currentServer);
                                    ArrayList<ServerInfo> lowPriorityServes = getLowPriorityServers(currentServer);
                                    MessageTransferServ.sendSpecificBroadcast(lowPriorityServes, messageHandler.coordinatorMessage());
                                    electionInProgress = false;
                                    nominationStart = false;
                                } else {
                                    String highestPriorityServerID = getHighestPriorityServersByID(currentServerID, answerMessageReceived);
                                    ServerInfo highestPriorityServerData = StatusHandler.getServerStateInstance().getServerDataById(highestPriorityServerID);
                                    logger.info("Sending Nomination message");
                                    nominationStart = true;
                                    MessageTransferServ.sendToServers(messageHandler.nominationMessage(), highestPriorityServerData.getServerAddress(), highestPriorityServerData.getCoordinationPort());
                                    removeNominationSend(highestPriorityServerID);
                                    FastBullyAlgo fastBullyService = new FastBullyAlgo("wait", "coordinationMessageWait");
                                    fastBullyService.start();
                                }
                            }
                        } catch (InterruptedException e) {
                            logger.error("Exception occurred in wait thread");
                            e.printStackTrace();
                        }
                    }
                    case ("coordinationMessageWait") -> {
                        try {
                            Thread.sleep(2000);
                            if (electionInProgress) {
                                if (coordinationMessageReceived == null) {
                                    FastBullyAlgo fastBully = new FastBullyAlgo("wait", "answerMessageWait");
                                    fastBully.start();
                                } else {
                                    logger.info("Coordinator message Received");
                                    electionInProgress = false;
                                    nominationStart = false;
                                    String leaderServerID = (String) coordinationMessageReceived.get("serverid");
                                    logger.info("Set the leader to server : " + leaderServerID);
                                    ServerInfo leaderServer = StatusHandler.getServerStateInstance().getServerDataById(leaderServerID);
                                    StatusHandler.getServerStateInstance().setLeader(leaderServer);
                                    coordinationMessageReceived = null;
                                    answerMessageReceived = Collections.synchronizedList(new ArrayList<>());
                                }
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    case ("nominationCoordinationWait") -> {
                        try {
                            // busy wait for 1500 milliseconds
                            int counter = 0;
                            while (true) {
                                counter+=1;
                                Thread.sleep(50);
                                if(!(nominationMessageReceived == null) && counter == 30){
                                    break;
                                }
                            }
                            if (electionInProgress) {
                                if (!(nominationMessageReceived == null)) {
                                    logger.info("Nomination message Received");
                                    ServerInfo leaderServer = StatusHandler.getServerStateInstance().getCurrentServerData();
                                    StatusHandler.getServerStateInstance().setLeader(leaderServer);
                                    logger.info("Current server become leader and Sending Coordinator Message");
                                    MessageTransferServ.sendToServersBroadcast(messageHandler.coordinatorMessage());
                                    nominationMessageReceived = null;
                                    nominationStart = false;
                                    electionInProgress = false;
                                } else if (!(coordinationMessageReceived == null)) {
                                    logger.info("Coordinator message Received");
                                    logger.info("Set the leader to server : " + coordinationMessageReceived.get("serverid"));
                                    String leaderServerID = (String) coordinationMessageReceived.get("serverid");
                                    ServerInfo leaderServer = StatusHandler.getServerStateInstance().getServerDataById(leaderServerID);
                                    StatusHandler.getServerStateInstance().setLeader(leaderServer);
                                    coordinationMessageReceived = null;
                                    nominationStart = false;
                                    electionInProgress = false;
                                } else {
                                    electionInProgress = true;
                                    logger.info("Coordination message or nomination message did not received and restart election procedure");
                                    answerMessageReceived = Collections.synchronizedList(new ArrayList<>());
                                    StatusHandler newServerState = StatusHandler.getServerStateInstance();
                                    ArrayList<ServerInfo> higherPriorityServers = getHigherPriorityServers(newServerState.getCurrentServerData());
                                    MessageTransferServ.sendSpecificBroadcast(higherPriorityServers, messageHandler.electionMessage());
                                    FastBullyAlgo fastBullyService = new FastBullyAlgo("wait", "answerMessageWait");
                                    fastBullyService.start();
                                }
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            case ("send") -> {
                switch (request) {
                    case "iamup" -> {
                        // send iam up message
                        if (!electionInProgress) {
                            logger.info("Alive message sent");
                            electionInProgress = true;
                            viewMessagesReceived = Collections.synchronizedList(new ArrayList<>());
                            MessageTransferServ.sendToServersBroadcast(messageHandler.iamUpMessage());
                            FastBullyAlgo fastBullyService = new FastBullyAlgo("wait", "viewMessageWait");
                            fastBullyService.start();
                        }
                    }
                    case "election" -> logger.info("Election message sent");
                    case "nomination" -> logger.info("Nominating");
                    case "coordinator" -> logger.info("Coordinator msg sent");
                    case "answer" -> {
                        logger.info("Sending Answer Message");
                        ServerInfo requestServer = StatusHandler.getServerStateInstance().getServerDataById((String) this.reply.get("serverid"));
                        MessageTransferServ.sendToServers(messageHandler.answerMessage(), requestServer.getServerAddress(), requestServer.getCoordinationPort());
                        FastBullyAlgo fastBullyService = new FastBullyAlgo("wait", "nominationCoordinationWait");
                        fastBullyService.start();
                    }
                    case "view" -> {
                        logger.info("Sending View Message");
                        ServerInfo requestServer = StatusHandler.getServerStateInstance().getServerDataById((String) this.reply.get("serverid"));
                        // ArrayList<String> activeServers = new ArrayList<>(); // TO DO: get actual active servers.
                        ArrayList<String> clientids = StatusHandler.getServerStateInstance().globalClientId;
                        ArrayList<String> roomids = StatusHandler.getServerStateInstance().globalRoomList;
                        ArrayList<String> roomservers = StatusHandler.getServerStateInstance().globalRoomServersList;
                        ArrayList<String> roomowners = StatusHandler.getServerStateInstance().globalRoomOwnersList;
                        ArrayList<ArrayList<String>> roomclientids = StatusHandler.getServerStateInstance().globalRoomClientsList;
                        JSONObject response = messageHandler.viewMessage(clientids, roomids, roomservers, roomowners, roomclientids);
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        MessageTransferServ.sendToServers(response, requestServer.getServerAddress(), requestServer.getCoordinationPort());
                    }
                }
            }
        }
    }

    public static void receiveBullyMessage(JSONObject response) {
        String type = (String) response.get("type");
//        System.out.println("Receiving To Bully Service: " + response);
        switch (type) {
            case ("heartbeat") -> {
                // heartbeat received to the leader server - don't do anything
            }
            case "iamup" -> {
                logger.info("Alive Received");
                FastBullyAlgo fastBullyService = new FastBullyAlgo("send", "view");
                fastBullyService.setReply(response);
                fastBullyService.start();
            }
            case "election" -> {
                electionInProgress = true;
                logger.info("Election message Received");
                FastBullyAlgo fastBullyService = new FastBullyAlgo("send", "answer");
                fastBullyService.setReply(response);
                fastBullyService.start();
            }
            case "nomination" -> nominationMessageReceived = response;
            case "coordinator" -> {
                if (nominationStart) {
                    coordinationMessageReceived = response;
                } else {
                    electionInProgress = false;
                    logger.info("Coordinator message Received");
                    String leaderServerID = (String) response.get("serverid");
                    logger.info("Set the leader to server :  " + leaderServerID);
                    ServerInfo leaderServer = StatusHandler.getServerStateInstance().getServerDataById(leaderServerID);
                    StatusHandler.getServerStateInstance().setLeader(leaderServer);
                }
            }
            case "answer" -> {
                logger.info("Answer message Received");
                answerMessageReceived.add(response);
            }
            case "view" -> viewMessagesReceived.add(response);
        }
    }

    public static void initializeService() {
        Thread fastBullyService = new FastBullyAlgo("send", "iamup");
        fastBullyService.start();
    }

}
