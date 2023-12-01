package Services.ChatService;

import Handlers.ChatHandler.*;
import Models.ClientPojo;
import Models.Server.StatusHandler;
import Services.CoordinationService.GossipService;
import Services.MessageTransferServ;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MessagingService extends Thread {
    private final Logger logger = Logger.getLogger(MessagingService.class);
    private final  Socket clientSocket;
    private final JSONParser parser = new JSONParser();
    private ClientPojo client;
    private final MessageResponseHandler clientResponseHandler = new MessageResponseHandler();
    private boolean running = true;

    public MessagingService(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public void stopThread() {
        running = false;
    }

    public void setClient(ClientPojo client){
        this.client = client;
    }

    private void quitMethod(boolean interruption) {
        List<MessagingService> clientThreads_quit = StatusHandler.getServerStateInstance().getClientServicesInRoomByClient(this.client);
        Map<String, ArrayList<JSONObject>> quitResponses = new QuitHandler(this.clientResponseHandler).handleQuit(this.client);
        if (quitResponses.containsKey("gossip")){
            Thread gossipService = new GossipService("send", "gossipidentity", quitResponses.get("gossip").get(0));
            gossipService.start();
        }
        if (quitResponses.containsKey("broadcastServers")){
            MessageTransferServ.sendToServersBroadcast(quitResponses.get("broadcastServers").get(0));
        }
        if (quitResponses.containsKey("broadcastClients")) {
            for (JSONObject response : quitResponses.get("broadcastClients")) {
                MessageTransferServ.sendAll(clientThreads_quit, response);
            }
        }
        if (!interruption) {
            if (quitResponses.containsKey("reply")) {
                MessageTransferServ.send(this.clientSocket, quitResponses.get("reply").get(0));
            }
            if (quitResponses.containsKey("client-only")) {
                MessageTransferServ.send(this.clientSocket, quitResponses.get("client-only").get(0));
            }
        }
        // server closes the connection
        StatusHandler.getServerStateInstance().clientServices.remove(this.client.getIdentity());
        this.stopThread();
    }

    public void run() {
        while (running) {
            try {
                assert clientSocket != null;
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                JSONObject message = (JSONObject) parser.parse(in.readLine());
                String type = (String) message.get("type");
                //System.out.println(message);

                switch (type) {
                    case "list" -> {
                        logger.info("Received message type list");
                        JSONObject roomListResponse = new GroupListHandler(this.clientResponseHandler).getRoomList(client.getIdentity());
                        logger.info(roomListResponse.isEmpty());
                        logger.info(roomListResponse);
                        if (!roomListResponse.isEmpty()){
                            MessageTransferServ.send(this.clientSocket, roomListResponse);
                        }
                    }
                    case "who" -> {
                        logger.info("Received message type who");
                        ClientListInGroupProcessor clientListInRoomHandler = new ClientListInGroupProcessor();
                        String roomID = clientListInRoomHandler.getClientsRoomID(client.getIdentity());
                        ArrayList<String> identities = clientListInRoomHandler.getClientsInRoom(roomID);
                        String owner = clientListInRoomHandler.getRoomOwner(roomID);
                        JSONObject clientInRoomListResponse = new MessageResponseHandler().sendClientListInChatRoom(roomID, identities, owner);
                        MessageTransferServ.send(this.clientSocket, clientInRoomListResponse);
                    }
                    case "createroom" -> {
                        logger.info("Received message type createroom");
                        List<MessagingService> clientThreads_formerRoom = StatusHandler.getServerStateInstance().getClientServicesInRoomByClient(this.client);
                        Map<String, JSONObject> responses = new CreateGroupProcessor(this.clientResponseHandler).createRoom(client, (String) message.get("roomid"));
                        if (!responses.containsKey("askedFromLeader")) {
                            MessageTransferServ.send(this.clientSocket, responses.get("client-only"));
                            if (responses.containsKey("broadcast")) {
                                MessageTransferServ.send(this.clientSocket, responses.get("broadcast"));
                                MessageTransferServ.sendAll(clientThreads_formerRoom, responses.get("broadcast"));
                            }
                            if (responses.containsKey("gossip")) {
                                Thread gossipService = new GossipService("send", "gossiproom", responses.get("gossip"));
                                gossipService.start();
                            }
                        }
                    }
                    case "joinroom" -> {
                        logger.info("Received message type joinroom");
                        List<MessagingService> clientThreads_formerRoom = StatusHandler.getServerStateInstance().getClientServicesInRoomByClient(this.client);
                        Map<String, JSONObject> responses = new JoinGroupProcessor(this.clientResponseHandler).joinRoom(client, (String) message.get("roomid"));
                        List<MessagingService> clientThreads_joinedRoom = StatusHandler.getServerStateInstance().getClientServicesInRoomByClient(this.client);
                        if (!responses.containsKey("askedFromLeader")) {
                            if (responses.containsKey("client-only")) {
                                MessageTransferServ.send(this.clientSocket, responses.get("client-only"));
                            }
                            if (responses.containsKey("broadcast-all")) {
                                MessageTransferServ.send(this.clientSocket, responses.get("broadcast-all"));
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
                    case "movejoin" -> {
                        logger.info("Received message type movejoin");
                        client = new ClientPojo();
                        String identity = (String) message.get("identity");
                        client.setIdentity(identity);
                        client.setServer(System.getProperty("serverID"));
                        client.setStatus("active");
                        //System.out.print(ServerState.getServerStateInstance().clients);
                        StatusHandler.getServerStateInstance().clients.put(identity, this.client);
                        //LeaderState.getInstance().globalClients.put(identity, client);
                        StatusHandler.getServerStateInstance().clientServices.put(identity, this);

                        Map<String, JSONObject> movejoinResponses = new MoveJoinHandler(this.clientResponseHandler).movejoin((String) message.get("former"), (String) message.get("roomid"), identity, this.client);
                        List<MessagingService> clientThreads_movejoin = StatusHandler.getServerStateInstance().getClientServicesInRoomByClient(this.client);
                        //System.out.println(this.clientSocket.getOutputStream());
                        //System.out.println(movejoinResponses.get("client-only"));

                        MessageTransferServ.send(this.clientSocket, movejoinResponses.get("client-only"));
                        MessageTransferServ.send(this.clientSocket, movejoinResponses.get("broadcast"));
                        MessageTransferServ.sendAll(clientThreads_movejoin, movejoinResponses.get("broadcast"));

                    }
                    case "deleteroom" -> {
                        logger.info("Received message type deleteroom");
                        Map<String, ArrayList<JSONObject>> deleteRoomResponses = new DeleteGroupProcessor(this.clientResponseHandler).deleteRoom((String) message.get("roomid"), this.client);
                        if(deleteRoomResponses.containsKey("broadcastServers")){
                            MessageTransferServ.sendToServersBroadcast(deleteRoomResponses.get("broadcastServers").get(0));
                        }
                        if (deleteRoomResponses.containsKey("broadcastClients")) {

                            List<MessagingService> clientThreads_deleteRoom = StatusHandler.getServerStateInstance().getClientServicesInRoomByClient(this.client);
                            for (JSONObject deleteResponse : deleteRoomResponses.get("broadcastClients")) {
                                MessageTransferServ.send(this.clientSocket, deleteResponse);
                                MessageTransferServ.sendAll(clientThreads_deleteRoom, deleteResponse);
                            }
                        }
                        if(deleteRoomResponses.containsKey("gossip")){
                            JSONObject gossipMsg = deleteRoomResponses.get("gossip").get(0);
                            Thread gossipService = new GossipService("send", "gossiproom", gossipMsg);
                            gossipService.start();
                        }
                        MessageTransferServ.send(this.clientSocket, deleteRoomResponses.get("client-only").get(0));
                    }
                    case "quit" -> {
                        logger.info("Received message type quit");
                        quitMethod(false);
                    }
                    case "newidentity" -> {
                        logger.info("Received message type newidentity");
                        this.client = new ClientPojo();
                        Map<String, JSONObject> responses = new Identity(this.clientResponseHandler).addNewIdentity(this, client, (String) message.get("identity"));
                        if (!responses.containsKey("askedFromLeader")) {
                            List<MessagingService> clientThreads_newId = StatusHandler.getServerStateInstance().getClientServicesInRoomByClient(this.client);
                            MessageTransferServ.send(this.clientSocket, responses.get("client-only"));
                            if (responses.containsKey("broadcast")) {
                                MessageTransferServ.send(this.clientSocket, responses.get("broadcast"));
                                if (responses.containsKey("broadcast")) {
                                    MessageTransferServ.sendAll(clientThreads_newId, responses.get("broadcast"));
                                }
                                if (responses.containsKey("gossip")) {
                                    //Thread gossipService = new GossipService("send", "gossipnewidentity", responses.get("gossip"));
                                    Thread gossipService = new GossipService("send", "gossipidentity", responses.get("gossip"));
                                    gossipService.start();
                                }
                            } else{
                                this.stopThread(); //close thread if the newIdentity rejected - Only for newIdentity
                            }
                        }
                    }
                    case "message" -> {
                        logger.info("Received message type message");
                        String clientID = this.client.getIdentity();
                        String content = (String) message.get("content");
                        List<MessagingService> clientThreads = StatusHandler.getServerStateInstance().getClientServicesInRoomByClient(this.client);
                        JSONObject messageResponse = new MessageHandler(this.clientResponseHandler).processMessage(clientID, content);
                        MessageTransferServ.sendAll(clientThreads, messageResponse);
                    }
                }
            } catch (IOException e) {
                logger.info("Abrupt disconnection by client");
                quitMethod(true);
            } catch (ParseException e) {
                logger.error("Parse Exception occurred" + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    public Socket getClientSocket() {
        return this.clientSocket;
    }

}
