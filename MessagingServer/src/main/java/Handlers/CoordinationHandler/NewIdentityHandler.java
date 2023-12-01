package Handlers.CoordinationHandler;

import Handlers.ChatHandler.MessageResponseHandler;
import Models.ClientPojo;
import Models.Server.LeaderState;
import Models.Server.ServerInfo;
import Models.Server.StatusHandler;
import Services.ChatService.MessagingService;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class NewIdentityHandler {
    private final Logger logger = Logger.getLogger(Handlers.ChatHandler.Identity.class);
    private final RequestHandler serverRequestHandler = new RequestHandler();
    private final ResponseHandler serverResponseHandler = new ResponseHandler();
    private final MessageResponseHandler clientResponseHandler = new MessageResponseHandler();
    private final GossipProcessor gossipHandler = new GossipProcessor();

    public NewIdentityHandler() {
    }

    public boolean checkIdentityUnique(String identity) {
        boolean isIdentityUnique = true;
        ServerInfo currentServer = StatusHandler.getServerStateInstance().getCurrentServerData();
        ServerInfo leaderServer = StatusHandler.getServerStateInstance().getLeaderServerData();
        if (Objects.equals(currentServer.getServerID(), leaderServer.getServerID())) {
            ConcurrentHashMap<String, ClientPojo> clients = LeaderState.getInstance().getGlobalClients();
            for (Iterator<String> it = clients.keys().asIterator(); it.hasNext(); ) {
                String client = it.next();
                if (Objects.equals(client, identity)) {
                    isIdentityUnique = false;
                }
            }
        }
        return isIdentityUnique;
    }

    //public JSONObject coordinatorNewClientIdentity(Client client, String identity, String serverID) {
    public Map<String, JSONObject> coordinatorNewClientIdentity(ClientPojo client, String identity, String serverID) {
        Map<String, JSONObject> responses = new HashMap<>();
        JSONObject response;
        if (checkIdentityUnique(identity)) {
            client.setIdentity(identity);
            client.setServer(serverID);
            client.setStatus("active");
            LeaderState.getInstance().addClientToGlobalList(client);
            logger.info("New identity creation accepted");
            response = this.serverResponseHandler.sendNewIdentityServerResponse("true", identity);
            //JSONObject gossipMsg = this.gossipHandler.gossipNewIdentity(serverID, identity);
            JSONObject gossipMsg = this.gossipHandler.gossip("gossipidentity", System.getProperty("serverID"), LeaderState.getInstance().globalClients);
            responses.put("response", response);
            responses.put("gossip", gossipMsg);
        } else {
            logger.info("New identity creation rejected");
            response = this.serverResponseHandler.sendNewIdentityServerResponse("false", identity);
            responses.put("response", response);
        }
        return responses;
    }
    public JSONObject moveToPrimaryGroup(ClientPojo client){
        JSONObject response;
        String roomID = "PrimaryGroup-" + System.getProperty("serverID");
        StatusHandler.getServerStateInstance().addClientToRoom(roomID, client);
        response = clientResponseHandler.moveToRoomResponse(client.getIdentity(), "", roomID);
        return response;
    }
    public Map<String, JSONObject> leaderApprovedNewClientIdentity(String isApproved, ClientPojo client, String identity){
        Map<String, JSONObject> responses = new HashMap<>();
        if(isApproved.equals("true")){
            logger.info("New identity creation accepted");
            client.setIdentity(identity);
            client.setServer(System.getProperty("serverID"));
            client.setStatus("active");
            MessagingService service = StatusHandler.getServerStateInstance().clientServices.get("1temp-"+identity); //
            service.setClient(client);
            StatusHandler.getServerStateInstance().clients.put(client.getIdentity(), client);
            StatusHandler.getServerStateInstance().clientServices.remove("1temp-"+identity, service); //
            StatusHandler.getServerStateInstance().clientServices.put(identity, service); //
            responses.put("client-only", clientResponseHandler.sendNewIdentityResponse("true"));
            responses.put("broadcast", moveToPrimaryGroup(client));
        }else if(isApproved.equals("false")){
            StatusHandler.getServerStateInstance().clientServices.get("1temp-"+identity).stop();
            StatusHandler.getServerStateInstance().clientServices.remove("1temp-"+identity); //
            logger.info("New identity creation rejected");
            StatusHandler.getServerStateInstance().clientServices.remove("1temp-"+identity); //
            responses.put("client-only", clientResponseHandler.sendNewIdentityResponse("false"));
        }
        return responses;
    }
}
