package Models;

import java.util.ArrayList;

public class Group {

    private String roomID;
    private String server;
    private String owner;
    private ArrayList<ClientPojo> clients;

    public Group(String roomID, String server, String owner, ArrayList<ClientPojo> clients) {
        this.roomID = roomID;
        this.server = server;
        this.owner = owner;
        this.clients = clients;
    }

    public String getRoomID() {
        return roomID;
    }

    public void setRoomID(String roomID) {
        this.roomID = roomID;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public ArrayList<ClientPojo> getClients() {
        return clients;
    }

    public void setClients(ArrayList<ClientPojo> clients) {
        this.clients = clients;
    }

    public void addClient(ClientPojo client){
        this.clients.add(client);
    }

    public void removeClient(ClientPojo client) {
        this.clients.remove(client);
    }

    public synchronized void removeClientByClienID(String clientID){
        for(ClientPojo client :this.clients){
            if(client.identity.equals(clientID)){
                this.clients.remove(client);
                break;
            }
        }
    }

    public void addClientList(ArrayList<ClientPojo> client){
        this.clients.addAll(client);
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}
