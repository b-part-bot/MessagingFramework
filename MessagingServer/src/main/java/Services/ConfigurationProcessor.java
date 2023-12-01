package Services;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import Models.Server.ServerInfo;
import Models.Server.StatusHandler;
import org.apache.log4j.Logger;

public class ConfigurationProcessor {

    private final Logger logger = Logger.getLogger(ConfigurationProcessor.class);

    public void readConfigFile(String currentServerID, String serversConfig){
        try{
            BufferedReader buf = new BufferedReader(new FileReader(serversConfig));
            List<ServerInfo> serverDataList = new ArrayList<ServerInfo>();
            String lineJustFetched = null;
            String[] wordsArray;

            while(true){
                lineJustFetched = buf.readLine();
                if(lineJustFetched == null){
                    break;
                }else{
                    wordsArray = lineJustFetched.split("\t");
                    String serverID = wordsArray[0];
                    String serverAddress = wordsArray[1];
                    int clientPort = Integer.parseInt(wordsArray[2]);
                    int coordinationPort = Integer.parseInt(wordsArray[3]);
                    ServerInfo severData = new ServerInfo(serverID, serverAddress, clientPort, coordinationPort);
                    serverDataList.add(severData);
                }
            }

            buf.close();
            StatusHandler.getServerStateInstance().setServerState(serverDataList, currentServerID);
            logger.info("Server Configuration Added");

        }catch(Exception e){
            logger.error("Exception while reading configuration due to: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
