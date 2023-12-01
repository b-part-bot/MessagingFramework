import Handlers.ChatHandler.MainGroupHandler;
import Models.Server.ServerInfo;
import Models.Server.StatusHandler;
import Services.ConfigurationProcessor;

import java.net.*;
import java.io.*;
import java.nio.channels.*;
import java.util.Set;

import Services.CoordinationService.CoordinationService;
import Services.CoordinationService.FastBullyAlgo;
import Services.CoordinationService.GossipService;
import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import Services.ChatService.MessagingService;

public class Server {
    public static void main(String[] args) throws IOException {

        String serverID;
        String serversConf;
        InputConfiguration values = new InputConfiguration();
        CmdLineParser parser = new CmdLineParser(values);

        try {
            parser.parseArgument(args);
            serverID = values.getServerId();
            serversConf = values.getServerConfig();
            System.setProperty("serverID", serverID);

            Logger logger = Logger.getLogger(Server.class);
            logger.info("Server configuration.");

            new ConfigurationProcessor().readConfigFile(serverID, serversConf);

            FastBullyAlgo.initializeService();
            GossipService.initializeService();

            Thread PrimaryGroupHandler = new MainGroupHandler();
            PrimaryGroupHandler.start();


            ServerInfo server_info = StatusHandler.getServerStateInstance().getCurrentServerData();

            Selector selector = Selector.open();
            int[] ports = {server_info.getClientPort(), server_info.getCoordinationPort()};

            for (int port : ports) {
                ServerSocketChannel server = ServerSocketChannel.open();
                server.configureBlocking(false);
                server.socket().bind(new InetSocketAddress(port));
                server.register(selector, SelectionKey.OP_ACCEPT);
            }

            while (selector.isOpen()) {
                selector.select();
                Set<SelectionKey> readyKeys = selector.selectedKeys();
                for (SelectionKey key : readyKeys) {
                    if (key.isAcceptable()) {
                        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
                        if (channel!=null) {
                            Socket socket = channel.socket();
                            int port = socket.getLocalPort();
                            if (server_info.getClientPort() == port) {
                                MessagingService clientThread = new MessagingService(socket);
                                clientThread.start();
                            } else {
                                CoordinationService coordinatorThread = new CoordinationService(socket);
                                coordinatorThread.start();
                            }
                        }
                    }
                }
            }

        } catch (CmdLineException e) {
            e.printStackTrace();
        }

    }
}