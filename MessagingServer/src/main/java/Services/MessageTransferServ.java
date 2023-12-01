package Services;

import Models.Server.ServerInfo;
import Models.Server.StatusHandler;
import Services.ChatService.MessagingService;
import Services.CoordinationService.CoordinationService;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

public class MessageTransferServ {
    static Logger logger = Logger.getLogger(CoordinationService.class);

    private MessageTransferServ() {
    }

    public static void send(Socket socket, JSONObject message) {
        try {
            OutputStream out = socket.getOutputStream();
            out.write((message.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sendAll(List<MessagingService> clientThreads, JSONObject message) {
        for (MessagingService service : clientThreads) {
            send(service.getClientSocket(), message);
        }
    }

    public static void sendToServers(JSONObject message, String host, int port) {
        try {
            Socket socket = new Socket(host, port);
            DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
            dataOutputStream.write((message.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
            dataOutputStream.flush();
        } catch (IOException e) {

        }
    }

    public static void sendToServersBroadcast(JSONObject message) {
        ConcurrentMap<String, ServerInfo> serverList = StatusHandler.getServerStateInstance().getServersList();
        ServerInfo currentServer = StatusHandler.getServerStateInstance().getCurrentServerData();
        for (ConcurrentMap.Entry<String, ServerInfo> entry : serverList.entrySet()) {
            if (!currentServer.getServerID().equals(entry.getKey())) {
                sendToServers(message, entry.getValue().getServerAddress(), entry.getValue().getCoordinationPort());
            }
        }
    }

    public static void leaderKeepAlive(JSONObject message, String host, int port) throws IOException {
        Socket socket = new Socket(host, port);
        DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
        dataOutputStream.write((message.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
        dataOutputStream.flush();
    }

    public static void sendSpecificBroadcast(ArrayList<ServerInfo> servers, JSONObject message) {
        for (ServerInfo server : servers) {
            sendToServers(message, server.getServerAddress(), server.getCoordinationPort());
        }
    }
}
