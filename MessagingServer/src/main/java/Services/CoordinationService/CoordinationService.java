package Services.CoordinationService;

import Handlers.CoordinationHandler.*;
import Models.ClientPojo;
import Handlers.ChatHandler.MessageResponseHandler;
import Models.Group;
import Models.Server.LeaderState;
import Models.Server.ServerInfo;
import Models.Server.StatusHandler;
import Services.ChatService.MessagingService;
import Services.MessageTransferServ;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentMap;

public class CoordinationService extends Thread {
    private final Socket coordinationSocket;
    Logger logger = Logger.getLogger(CoordinationService.class);
    private final JSONParser parser = new JSONParser();
    private boolean running = true;
    private final ResponseHandler serverResponseHandler = new ResponseHandler();
    private final GossipProcessor gossipHandler = new GossipProcessor();

    public CoordinationService(Socket coordinationSocket) {
        this.coordinationSocket = coordinationSocket;
    }

    public void stopThread() {
        running = false;
    }

    public void run() {
        while (running) {
            try {
                assert coordinationSocket != null;
                BufferedReader in = new BufferedReader(new InputStreamReader(coordinationSocket.getInputStream(), StandardCharsets.UTF_8));
                JSONObject message = (JSONObject) parser.parse(in.readLine());
                String type = (String) message.get("type");
                switch (type) {
                    case "allrooms" -> {
                        logger.info("Received message type list");
                        JSONObject response = new RoomListHandler().coordinatorRoomList((String) message.get("clientid"));
                        ServerInfo requestServer = StatusHandler.getServerStateInstance().getServersList().get((String) message.get("serverid"));
                        MessageTransferServ.sendToServers(response, requestServer.getServerAddress(), requestServer.getCoordinationPort());
                    }
                    case "leaderallrooms" -> {
                        System.out.println(message.get("allrooms"));
                        JSONObject response = new MessageResponseHandler().sendRoomList((ArrayList<String>) message.get("allrooms"));
                        logger.info(response);
                        MessagingService chatClientService = StatusHandler.getServerStateInstance().clientServices.get(message.get("clientid"));
                        MessageTransferServ.send(chatClientService.getClientSocket(), response);
                    }
                    case "createroom" -> {
                        logger.info("Received message type createroom");
                        Map<String, JSONObject> responses = new CreateGroupProcessor().coordinatorNewRoomIdentity((String) message.get("clientid"), (String) message.get("roomid"), (String) message.get("serverid"));
                        JSONObject response = responses.get("response");
                        ServerInfo requestServer = StatusHandler.getServerStateInstance().getServersList().get((String) message.get("serverid"));
                        MessageTransferServ.sendToServers(response, requestServer.getServerAddress(), requestServer.getCoordinationPort());
                        if (responses.containsKey("gossip")) {
                            Thread gossipService = new GossipService("send", "gossiproom", responses.get("gossip"));
                            gossipService.start();
                        }
                    }
                    case "leadercreateroom" -> {
                        logger.info("Received message type leadercreateroom");
                        ClientPojo client = StatusHandler.getServerStateInstance().clients.get((String) message.get("clientid"));
                        MessagingService chatClientService = StatusHandler.getServerStateInstance().clientServices.get((String) message.get("clientid"));
                        List<MessagingService> clientThreads_formerRoom = StatusHandler.getServerStateInstance().getClientServicesInRoomByClient(client);
                        Map<String, JSONObject> responses = new CreateGroupProcessor().leaderApprovedNewRoomIdentity((String) message.get("approved"), client, (String) message.get("roomid"));
                        MessageTransferServ.send(chatClientService.getClientSocket(), responses.get("client-only"));
                        if (responses.containsKey("broadcast")) {
                            MessageTransferServ.send(chatClientService.getClientSocket(), responses.get("broadcast"));
                            MessageTransferServ.sendAll(clientThreads_formerRoom, responses.get("broadcast"));
                        }
                    }
                    case "roomexist" -> {
                        logger.info("Received message type roomexist");
                        JSONObject response = new JoinRoomHandler().coordinatorRoomExist((String) message.get("clientid"), (String) message.get("roomid"));
                        ServerInfo requestServer = StatusHandler.getServerStateInstance().getServersList().get((String) message.get("serverid"));
                        MessageTransferServ.sendToServers(response, requestServer.getServerAddress(), requestServer.getCoordinationPort());
                    }
                    case "leaderroomexist" -> {
                        logger.info("Received message type leaderroomexist");
                        ClientPojo client = StatusHandler.getServerStateInstance().clients.get((String) message.get("clientid"));
                        MessagingService chatClientService = StatusHandler.getServerStateInstance().clientServices.get((String) message.get("clientid"));
                        List<MessagingService> clientThreads_formerRoom = StatusHandler.getServerStateInstance().getClientServicesInRoomByClient(client);
                        Map<String, JSONObject> responses = new JoinRoomHandler().leaderApprovedRoomExist((String) message.get("exist"), client, (String) message.get("roomid"));
                        List<MessagingService> clientThreads_joinedRoom = StatusHandler.getServerStateInstance().getClientServicesInRoomByClient(client);

                        if (!responses.containsKey("askedFromLeader")) {
                            if (responses.containsKey("client-only")) {
                                MessageTransferServ.send(chatClientService.getClientSocket(), responses.get("client-only"));
                            }
                            if (responses.containsKey("broadcast-all")) {
                                MessageTransferServ.send(chatClientService.getClientSocket(), responses.get("broadcast-all"));
                                MessageTransferServ.sendAll(clientThreads_formerRoom, responses.get("broadcast-all"));
                                MessageTransferServ.sendAll(clientThreads_joinedRoom, responses.get("broadcast-all"));
                            }
                            if (responses.containsKey("broadcast-former")) {
                                MessageTransferServ.sendAll(clientThreads_formerRoom, responses.get("broadcast-former"));
                                StatusHandler.getServerStateInstance().clientServices.get(client.getIdentity()).stop();
                                StatusHandler.getServerStateInstance().clientServices.remove(client.getIdentity());
                            }
                        }

                    }
                    case "getroomroute" -> {
                        logger.info("Received message type getroomroute");
                        JSONObject response = new JoinRoomHandler().coordinatorRoomRoute((String) message.get("clientid"), (String) message.get("roomid"));
                        ServerInfo requestServer = StatusHandler.getServerStateInstance().getServersList().get((String) message.get("serverid"));
                        MessageTransferServ.sendToServers(response, requestServer.getServerAddress(), requestServer.getCoordinationPort());
                    }
                    case "leaderroomroute" -> {
                        logger.info("Received message type leaderroomroute");
                        ClientPojo client = StatusHandler.getServerStateInstance().clients.get((String) message.get("clientid"));
                        MessagingService chatClientService = StatusHandler.getServerStateInstance().clientServices.get((String) message.get("clientid"));
                        List<MessagingService> clientThreads_formerRoom = StatusHandler.getServerStateInstance().getClientServicesInRoomByClient(client);
                        Map<String, JSONObject> responses = new JoinRoomHandler().leaderApprovedRoomRoute((String) message.get("exist"), client, (String) message.get("roomid"), (String) message.get("host"), (String) message.get("port"));

                        if (responses.containsKey("client-only")) {
                            MessageTransferServ.send(chatClientService.getClientSocket(), responses.get("client-only"));
                        }
                        if (responses.containsKey("broadcast-former")) {
                            MessageTransferServ.sendAll(clientThreads_formerRoom, responses.get("broadcast-former"));
                            StatusHandler.getServerStateInstance().clientServices.get(client.getIdentity()).stop();
                            StatusHandler.getServerStateInstance().clientServices.remove(client.getIdentity());
                        }
                    }
                    case "joinroom" -> {
                        logger.info("Received message type joinroom");
                    }
                    case "deleteroom" -> {
                        logger.info("Received message type deleteroom");
                        String currentServer = StatusHandler.getServerStateInstance().getCurrentServerData().getServerID();
                        String leaderserver = StatusHandler.getServerStateInstance().getLeaderServerData().getServerID();
                        if (currentServer.equals(leaderserver)) {
                            LeaderState.getInstance().getGlobalRoomList().remove((String) message.get("roomid"));
                            JSONObject gossipMsg = this.gossipHandler.gossipRoom("gossiproom", System.getProperty("serverID"), LeaderState.getInstance().globalRoomList);
                            Thread gossipService = new GossipService("send", "gossiproom", gossipMsg);
                            gossipService.start();
                        }
                    }
                    case "quit" -> {
                        logger.info("Received message type quit");
                        LeaderState.getInstance().globalClients.remove((String) message.get("identity"));
                        JSONObject gossipMsg = this.gossipHandler.gossip("gossipidentity", System.getProperty("serverID"), LeaderState.getInstance().globalClients);
                        Thread gossipService = new GossipService("send", "gossipidentity", gossipMsg);
                        gossipService.start();
                    }
                    case "newidentity" -> {
                        logger.info("Received message type newidentity");
                        ClientPojo client = new ClientPojo();
                        Map<String, JSONObject> responses = new NewIdentityHandler().coordinatorNewClientIdentity(client, (String) message.get("identity"), (String) message.get("serverid"));
                        JSONObject response = responses.get("response");
                        //JSONObject response = new NewIdentityHandler().coordinatorNewClientIdentity(client, (String) message.get("identity"), (String) message.get("serverid"));
                        ServerInfo requestServer = StatusHandler.getServerStateInstance().getServersList().get((String) message.get("serverid"));
                        MessageTransferServ.sendToServers(response, requestServer.getServerAddress(), requestServer.getCoordinationPort());
                        if (responses.containsKey("gossip")) {
                            Thread gossipService = new GossipService("send", "gossipidentity", responses.get("gossip"));
                            gossipService.start();
                        }

                    }
                    case "leadernewidentity" -> {
                        logger.info("Received message type leadernewidentity");
                        ClientPojo client = new ClientPojo();
                        String newClientID = (String) message.get("identity");
                        MessagingService chatClientService = StatusHandler.getServerStateInstance().clientServices.get("1temp-"+newClientID);
                        Map<String, JSONObject> responses = new NewIdentityHandler().leaderApprovedNewClientIdentity((String) message.get("approved"), client, (String) message.get("identity"));
                        List<MessagingService> clientThreads_newId = StatusHandler.getServerStateInstance().getClientServicesInRoomByClient(client);
                        MessageTransferServ.send(chatClientService.getClientSocket(), responses.get("client-only"));
                        if (responses.containsKey("broadcast")) {
                            MessageTransferServ.send(chatClientService.getClientSocket(), responses.get("broadcast"));
                            MessageTransferServ.sendAll(clientThreads_newId, responses.get("broadcast"));
                        }
                    }
                    case "PrimaryGroup" -> {
                        String roomID = (String) message.get("roomid");
                        Group room = new Group(roomID, (String) message.get("serverid"), "", new ArrayList<>());
                        LeaderState.getInstance().globalRoomList.put(roomID, room);
                        JSONObject gossipMsg = this.gossipHandler.gossipRoom("gossiproom", System.getProperty("serverID"), LeaderState.getInstance().getGlobalRoomList());
                        Thread gossipService = new GossipService("send", "gossiproom", gossipMsg);
                        gossipService.start();
                    }
                    case "pushgossipidentity" -> {
                        logger.info("Received message type pushgossipidentity");
                        long gossiprounds = (long) message.get("rounds");
                        StatusHandler.getServerStateInstance().setIsIgnorant(false);
                        StatusHandler.getServerStateInstance().setGlobalClientIDs((ArrayList<String>) message.get("clientids"));
                        if (gossiprounds < StatusHandler.getServerStateInstance().getInitialRounds()){
                            Thread gossipService = new GossipService("push", "pushgossipidentity", message);
                            gossipService.start();
                        } else {
                            System.out.println("Gossip rounds exceed");
                            //ServerData richneighbour = ServerState.getServerStateInstance().getRichNeighborData();
                            JSONObject msg = new GossipProcessor().roundExceed("identity");

                            for (ConcurrentMap.Entry<String, ServerInfo> entry : StatusHandler.getServerStateInstance().getServersList().entrySet()) {
                                if (!entry.getKey().equals(message.get("serverid"))) {
                                    MessageTransferServ.sendToServers(msg, entry.getValue().getServerAddress(), entry.getValue().getCoordinationPort());
                                }
                            }
                        }
                    }
                    case "pushgossiproom" -> {
                        logger.info("Received message type pushgossiproom");
                        long gossiprounds = (long) message.get("rounds");
                        StatusHandler.getServerStateInstance().setIsIgnorant(false);
                        StatusHandler.getServerStateInstance().setGlobalRoomList((ArrayList<String>) message.get("roomids"));
                        StatusHandler.getServerStateInstance().setGlobalRoomServersList((ArrayList<String>) message.get("roomservers"));
                        StatusHandler.getServerStateInstance().setGlobalRoomOwnersList((ArrayList<String>) message.get("roomowners"));
                        StatusHandler.getServerStateInstance().setGlobalRoomClientsList((ArrayList<ArrayList<String>>) message.get("clientids"));
//                        System.out.println("Updated");
                        if (gossiprounds < StatusHandler.getServerStateInstance().getInitialRounds()){
                            Thread gossipService = new GossipService("push", "pushgossiproom", message);
                            gossipService.start();
                        } else {
                            System.out.println("Gossip rounds exceed");
                            //ServerData richneighbour = ServerState.getServerStateInstance().getRichNeighborData();
                            JSONObject msg = new GossipProcessor().roundExceed("room");

                            for (ConcurrentMap.Entry<String, ServerInfo> entry : StatusHandler.getServerStateInstance().getServersList().entrySet()) {
                                if (!entry.getKey().equals(message.get("serverid"))) {
                                    MessageTransferServ.sendToServers(msg, entry.getValue().getServerAddress(), entry.getValue().getCoordinationPort());
                                }
                            }
                        }
                    }
                    case "roundexceed" -> {
                        logger.info("Received message type roundexceed");
                        if (StatusHandler.getServerStateInstance().getIsIgnorant()){
                            String msgtype = (String) message.get("messagetype");
                            String host = StatusHandler.getServerStateInstance().getCurrentServerData().getServerAddress();
                            String port = Integer.toString(StatusHandler.getServerStateInstance().getCurrentServerData().getCoordinationPort());
                            switch (msgtype) {
                                case "identity" -> {
                                    JSONObject msg = new GossipProcessor().pullGossip("pullgossipidentity", host, port);
                                    ServerInfo richneighbour = StatusHandler.getServerStateInstance().getRichNeighborData();
                                    MessageTransferServ.sendToServers(msg, richneighbour.getServerAddress(), richneighbour.getCoordinationPort());
                                }
                                case "room" -> {
                                    JSONObject msg = new GossipProcessor().pullGossip("pullgossiproom", host, port);
                                    ServerInfo richneighbour = StatusHandler.getServerStateInstance().getRichNeighborData();
                                    MessageTransferServ.sendToServers(msg, richneighbour.getServerAddress(), richneighbour.getCoordinationPort());
                                }
                            }
                        }
                    }
                    case "pullgossip" -> {
                        logger.info("Received message type pullgossip");
                        String pulltype = (String) message.get("pulltype");
                        if (!StatusHandler.getServerStateInstance().getIsIgnorant()){
                            Thread gossipService = new GossipService("pull", pulltype, message);
                            gossipService.start();
                        } else {
                            logger.info("Rich neighbour is ignorant");
                            JSONObject msg = new GossipProcessor().isIgnorant(pulltype);
                            MessageTransferServ.sendToServers(msg, (String) message.get("host"), Integer.parseInt((String) message.get("port")));

                        }
                    }
                    case "isignorant" -> {
                        logger.info("Received message type isignorant");
                        String pulltype = (String) message.get("pulltype");
                        String host = StatusHandler.getServerStateInstance().getCurrentServerData().getServerAddress();
                        String port = Integer.toString(StatusHandler.getServerStateInstance().getCurrentServerData().getCoordinationPort());
                        //JSONObject msg = new GossipHandler().pullGossip("pullgossipidentity", host, port);
                        JSONObject msg = new GossipProcessor().pullGossip(pulltype, host, port);
                        ArrayList<String> serverlist = new ArrayList<String>(StatusHandler.getServerStateInstance().getServersList().keySet());
                        Random rand = new Random();
                        String randomneighbour = serverlist.get(rand.nextInt(serverlist.size()));
                        ServerInfo richneighbour = StatusHandler.getServerStateInstance().getServersList().get(randomneighbour);
                        MessageTransferServ.sendToServers(msg, richneighbour.getServerAddress(), richneighbour.getCoordinationPort());
                    }
                    case "pullupdate" -> {
                        logger.info("Received message type pullupdate");
//                        System.out.println("Updated");
//                        System.out.println(message);
                        //ServerState.getServerStateInstance().setIsIgnorant(false);
                        String updatedType = (String) message.get("updatetype");
                        switch (updatedType) {
                            case "identity" -> {
                                StatusHandler.getServerStateInstance().setGlobalClientIDs((ArrayList<String>) message.get("updatedlist"));
                            }
                            case "room" -> {
                                StatusHandler.getServerStateInstance().setGlobalRoomList((ArrayList<String>) message.get("updatedroomids"));
                                StatusHandler.getServerStateInstance().setGlobalRoomServersList((ArrayList<String>) message.get("roomservers"));
                                StatusHandler.getServerStateInstance().setGlobalRoomOwnersList((ArrayList<String>) message.get("roomowners"));
                                StatusHandler.getServerStateInstance().setGlobalRoomClientsList((ArrayList<ArrayList<String>>) message.get("clientids"));
                            }
                        }
                    }
                    default -> {
                        // Send other cases to FastBully Service to handle
//                        logger.info("Sending to fast bully service");
                        FastBullyAlgo.receiveBullyMessage(message);
                    }
                }
                this.stopThread(); // Finally, stop the thread as there is no need for these connections to remain active throughout the lifetime of the system
            } catch (IOException | ParseException e) {
                logger.error("Exception occurred" + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
