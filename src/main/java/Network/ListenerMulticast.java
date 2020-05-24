package Network;

import Data.Chunk;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class ListenerMulticast {/*

    private boolean run = true;

    private InetAddress groupIP;
    private InetAddress myIP;
    private int mcport;
    private int ttl;

    private MulticastSocket mcs;

    private ArrayList<DatagramPacket> datagramTray; //Tabuleiro de DatagramPackets

    private ReentrantLock trayLock;

    private ScheduledExecutorService ses;

    public ListenerMulticast(InetAddress ip, InetAddress myIP, int mcport, int ttl){

        this.groupIP = ip;
        this.myIP = myIP;
        this.mcport = mcport;
        this.ttl = ttl;

        this.datagramTray = new ArrayList<DatagramPacket>();

        this.trayLock = new ReentrantLock();

        try {
            this.mcs = new MulticastSocket(this.mcport);
            this.mcs.joinGroup(this.groupIP);
        }
        catch(IOException e){
            e.printStackTrace();
        }

        this.ses = Executors.newSingleThreadScheduledExecutor();
    }

    private Runnable emptyPingTray = () -> {


        this.trayLock.lock();
        ArrayList <Ping> auxPing = (ArrayList<Ping>) this.pingTray.clone();
        this.pingTray.clear();
        this.trayLock.unlock();

        for(Ping ping : auxPing) {

            //printPing(ping);
            if (analisePing(ping)) {
                if(this.nh.registerNode(ping.requestID, ping.origin)) {
                    this.nh.addInConv(ping.origin);
                    sendPong(ping);
                }
            }
        }

    };

    private boolean analisePing(Ping ping) {
        boolean decision = false;
        int myNN1 = this.nt.getNumVN1() + this.nh.getInPingConvSize() ;

        // condição 1 VIZINHOS DIRETOS OU EM PROCESSO DE SER
        if((!ping.nbrN1.contains(this.myNode)) && (!this.nt.nbrN1Contains(ping.origin) && (!this.nh.contains(ping.origin)))){
            // condição 2 FALTA DE VIZINHOS??
            if((ping.nbrN1.size() < this.softcap) || (myNN1 < this.softcap)){
                decision = true;
            }
            else{
                // condição 3 SOU VIZINHO DE N2?
                if(!ping.nbrN2.contains(this.myNode)){
                    ArrayList <Nodo> myNbrs = new ArrayList<>(this.myN1Nbrs);
                    myNbrs.addAll(this.myN2Nbrs);
                    ArrayList <Nodo> pingNbrs = new ArrayList<>(ping.nbrN1);
                    pingNbrs.addAll(ping.nbrN2);

                    boolean interception = false;
                    // condição 4 INTERCEÇAO ENTRE OS VIZINHOS DE N1 e N2? MESMA REDE? É PRECISO MANDAR ALGUM QUIT??
                    for (Nodo n : myNbrs)
                        if (pingNbrs.contains(n)) {
                            interception = true;
                            break;
                        }

                        decision = !interception;
                }
            }
        }

        if(decision && myNN1+1 > this.hardcap) {
            this.nh.sendQuit();
        }

        return decision;
    }

    public void kill(){
        this.run = false;
        this.ses.shutdownNow();
        this.mcs.close();
    }

    public void writeDataManagerMetaInfoToRootFolder(){
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = null;
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(yourObject);
            out.flush();
            byte[] yourBytes = bos.toByteArray();
  ...
        } finally {
            try {
                bos.close();
            } catch (IOException ex) {
                // ignore close exception
            }
        }
    }

    /*
     * Reads and retrieves an Object from the root folder that corresponds to a DataManagerMetaInfo
     *
    private Chunk readDatagram(DatagramPacket d) {

        ByteArrayInputStream bis = new ByteArrayInputStream(d.getData());
        ObjectInput in = null;
        Object o = null;
        try {
            in = new ObjectInputStream(bis);
            o = in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                // ignore close exception
            }
        }
        Chunk c = null;
        if(o != null)
            c = (Chunk) o;

        return c;
    }

    public void run(){
        try{
            byte[] buf;
            DatagramPacket dp;
            Chunk cReceived;
            //this.ses.scheduleWithFixedDelay(sendPing, 0, 4, TimeUnit.SECONDS);
            //this.ses.scheduleWithFixedDelay(emptyPingTray, 3, 4, TimeUnit.SECONDS);
            //InetAddress myIP = InetAddress.getByName(this.myNode.ip);

            while(this.run){
                buf = new byte[1500];
                dp = new DatagramPacket(buf, 1500);
                this.mcs.receive(dp);

                //Filtragem por IP

                if (!dp.getAddress().equals(this.myIP)) {
                    this.trayLock.lock();
                    this.datagramTray.add(dp);
                    this.trayLock.unlock();
                }
            }
        }
        catch (SocketException se){
            //System.out.println("\t=>PING MULTICASTSOCKET CLOSED");
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
    */
}
