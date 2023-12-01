package coen317.client;

import coen317.client.data.State;
import coen317.client.messaging.recvMsgThread;
import coen317.client.messaging.sendMsgThread;
import org.json.simple.parser.ParseException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class MessagingClient {
    public static void main(String[] args) throws IOException, ParseException {
        Socket socket = null;
        String identity = null;
        boolean debug = false;
        try {
            //load command line args
            GetValues values = new GetValues();
            CmdLineParser parser = new CmdLineParser(values);
            try {
                parser.parseArgument(args);
                String hostname = values.getHost();
                identity = values.getIdentity();
                int port = values.getPort();
                debug = values.isDebug();
                socket = new Socket(hostname, port);
            } catch (CmdLineException e) {
                System.err.println("Error while parsing cmd line arguments: " + e.getLocalizedMessage());
            }

            State state = new State(identity, "");

            sendMsgThread messageSendThread = new sendMsgThread(socket, state, debug);
            Thread sendThread = new Thread(messageSendThread);
            sendThread.start();

            Thread receiveThread = new Thread(new recvMsgThread(socket, state, messageSendThread, debug));
            receiveThread.start();

        } catch (UnknownHostException e) {
            System.out.println("Unknown host");
        } catch (IOException e) {
            System.out.println("Communication Error: " + e.getMessage());
        }
    }
}
