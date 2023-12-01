package Handlers.ChatHandler;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

public class MessageHandler {
    private final Logger logger = Logger.getLogger(Identity.class);
    private final MessageResponseHandler clientResponseHandler;

    public MessageHandler(MessageResponseHandler clientResponseHandler){
        this.clientResponseHandler = clientResponseHandler;
    }

    public JSONObject processMessage(String clientID, String content){
        JSONObject response;
        logger.info("Broadcasting Message");
        response = clientResponseHandler.broadCastMessage(clientID, content);
        return response;
    }
}
