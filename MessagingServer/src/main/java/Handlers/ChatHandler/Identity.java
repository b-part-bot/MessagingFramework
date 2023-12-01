package Handlers.ChatHandler;

import Handlers.CoordinationHandler.GossipProcessor;
import Handlers.CoordinationHandler.RequestHandler;
import Models.ClientPojo;
import Models.Server.LeaderState;
import Models.Server.ServerInfo;
import Models.Server.StatusHandler;
import Services.ChatService.MessagingService;
import Services.MessageTransferServ;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Identity {
    private final Logger logger = Logger.getLogger(Identity.class);
    private final MessageResponseHandler clientResponseHandler;
    private final RequestHandler serverRequestHandler = new RequestHandler();
    private final GossipProcessor gossipHandler = new GossipProcessor();

    public Identity(MessageResponseHandler clientResponseHandler){
        this.clientResponseHandler = clientResponseHandler;
    }

    public boolean checkIdentityRules(String identity){
        boolean isIdentityGood;
        isIdentityGood = identity!= null && identity.matches("^[a-zA-Z][a-zA-Z0-9]*$")
                && identity.length()>=3
                && identity.length()<16;
        return isIdentityGood;
    }

    public String checkIdentityUnique(String identity) {
        String isIdentityUnique = "true";
        ServerInfo leaderServer = StatusHandler.getServerStateInstance().getLeaderServerData();
        if (StatusHandler.getServerStateInstance().isCurrentServerLeader()){
            ConcurrentHashMap<String, ClientPojo> clients = LeaderState.getInstance().getGlobalClients();
            for (Iterator<String> it = clients.keys().asIterator(); it.hasNext(); ) {
                String client = it.next();
                if (Objects.equals(client, identity)){
                    isIdentityUnique = "false";
                }
            }
        } else {
            JSONObject request = serverRequestHandler.sendNewIdentityResponse(identity);
            MessageTransferServ.sendToServers(request, leaderServer.getServerAddress(), leaderServer.getCoordinationPort());
            isIdentityUnique = "askedFromLeader";
        }
        return isIdentityUnique;
    }

    public JSONObject moveToPrimaryGroup(ClientPojo client){
        JSONObject response;
        String roomID = "PrimaryGroup-" + System.getProperty("serverID");
        StatusHandler.getServerStateInstance().addClientToRoom(roomID, client);
        response = clientResponseHandler.moveToRoomResponse(client.getIdentity(), "", roomID);
        return response;
    }

    public Map<String, JSONObject> addNewIdentity(MessagingService service, ClientPojo client, String identity){
        Map<String, JSONObject> responses = new HashMap<>();
        StatusHandler.getServerStateInstance().clientServices.put("1temp-"+identity, service); //
        boolean checkIdentityRules = checkIdentityRules(identity);
        String checkIdentityUnique = checkIdentityUnique(identity);
        if (checkIdentityRules && checkIdentityUnique.equals("true")){
            client.setIdentity(identity);
            client.setServer(System.getProperty("serverID"));
            client.setStatus("active");
            StatusHandler.getServerStateInstance().clients.put(identity, client);
            LeaderState.getInstance().globalClients.put(identity, client);
            StatusHandler.getServerStateInstance().clientServices.remove("1temp-"+identity, service); //
            StatusHandler.getServerStateInstance().clientServices.put(identity, service); //
            logger.info("New identity creation accepted");
            responses.put("client-only", clientResponseHandler.sendNewIdentityResponse("true"));
            responses.put("broadcast", moveToPrimaryGroup(client));
            //JSONObject gossipMsg = this.gossipHandler.gossipNewIdentity(System.getProperty("serverID"), identity);
            JSONObject gossipMsg = this.gossipHandler.gossip("gossipidentity", System.getProperty("serverID"), LeaderState.getInstance().globalClients);
            responses.put("gossip", gossipMsg);
        } else if(checkIdentityRules && checkIdentityUnique.equals("askedFromLeader")){
            logger.info("Asked from leader");
            responses.put("askedFromLeader", null);
        } else {
            StatusHandler.getServerStateInstance().clientServices.remove("1temp-"+identity); //
            logger.info("New identity creation rejected");
            responses.put("client-only", clientResponseHandler.sendNewIdentityResponse("false"));
        }
        return responses;
    }
}
