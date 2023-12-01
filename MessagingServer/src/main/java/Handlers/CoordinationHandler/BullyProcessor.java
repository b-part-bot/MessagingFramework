package Handlers.CoordinationHandler;

import org.json.simple.JSONObject;

import java.util.ArrayList;

public class BullyProcessor {

    @SuppressWarnings("unchecked")
    public JSONObject iamUpMessage(){
        JSONObject message = new JSONObject();
        message.put("type", "iamup");
        message.put("serverid", System.getProperty("serverID"));
        return message;
    }

    @SuppressWarnings("unchecked")
    public JSONObject electionMessage(){
        JSONObject message = new JSONObject();
        message.put("type", "election");
        message.put("serverid", System.getProperty("serverID"));
        return message;
    }

    @SuppressWarnings("unchecked")
    public JSONObject viewMessage(ArrayList<String> clientids, ArrayList<String> roomids, ArrayList<String> roomservers, ArrayList<String> roomowners, ArrayList<ArrayList<String>> roomclientids){
        JSONObject message = new JSONObject();
        message.put("type", "view");
        message.put("serverid", System.getProperty("serverID"));
        message.put("clientids", clientids);
        message.put("roomids", roomids);
        message.put("roomservers", roomservers);
        message.put("roomowners", roomowners);
        message.put("roomclientids", roomclientids);
        return message;
    }

    @SuppressWarnings("unchecked")
    public JSONObject answerMessage(){
        JSONObject message = new JSONObject();
        message.put("type", "answer");
        message.put("serverid", System.getProperty("serverID"));
        return message;
    }

    @SuppressWarnings("unchecked")
    public JSONObject nominationMessage(){
        JSONObject message = new JSONObject();
        message.put("type", "nomination");
        return message;
    }

    @SuppressWarnings("unchecked")
    public JSONObject coordinatorMessage(){
        JSONObject message = new JSONObject();
        message.put("type", "coordinator");
        message.put("serverid", System.getProperty("serverID"));
        return message;
    }

    @SuppressWarnings("unchecked")
    public JSONObject heartBeatMessage(){
        JSONObject message = new JSONObject();
        message.put("type", "heartbeat");
        message.put("serverid", System.getProperty("serverID"));
        return message;
    }

}
