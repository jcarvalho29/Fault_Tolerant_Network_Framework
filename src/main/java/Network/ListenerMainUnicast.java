package Network;

import Data.DataManager;
import Messages.TransferMetaInfo;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ListenerMainUnicast implements Runnable{

    private boolean run = true;

    private DatagramSocket unicastSocket;
    private int nicIndex;
    private InetAddress currentIP;
    private boolean isLocalLink;
    private int unicastPort;
    private int MTU;
    private int NICCapacity;

    private HashMap<Integer, TransferReceiverManager> infoReceiverManager;

    private ScheduledExecutorService ipChangeCheckerSES;

    private DataManager dm;

    private ArrayList<Integer> receivedIDs;

    public ListenerMainUnicast(DataManager dm, int nicIndex, boolean isLocalLink, int unicastPort, int NICCapacity){

        try {
            this.dm = dm;
            this.nicIndex = nicIndex;
            NetworkInterface nic = NetworkInterface.getByIndex(this.nicIndex);
            this.isLocalLink = isLocalLink;
            this.currentIP = null;

            while(this.currentIP == null){
                ArrayList <InetAddress> addresses = Collections.list(nic.getInetAddresses());
                for(InetAddress ip : addresses)
                    if(this.isLocalLink == ip.isLinkLocalAddress())
                        this.currentIP = ip;
            }

            this.MTU = nic.getMTU();
            this.NICCapacity = NICCapacity;

            this.infoReceiverManager = new HashMap<Integer, TransferReceiverManager>();
            this.receivedIDs = new ArrayList<Integer>();

            this.unicastPort = unicastPort;
            this.unicastSocket = new DatagramSocket(null);
            InetSocketAddress isa = new InetSocketAddress(this.currentIP, this.unicastPort);
            this.unicastSocket.bind(isa);

            System.out.println("Listening to IP =>" + this.currentIP + " PORT =>" + this.unicastPort);

            this.ipChangeCheckerSES = Executors.newSingleThreadScheduledExecutor();

            this.ipChangeCheckerSES.schedule(this.checkIPChange,2, TimeUnit.SECONDS);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    private final Runnable checkIPChange = () -> {
        //CASE THAT THERES NO NIC
        try{
            NetworkInterface nic = NetworkInterface.getByIndex(this.nicIndex);
            InetAddress ip = null;

            if(nic != null) {

                Enumeration<InetAddress> inetEnum = nic.getInetAddresses();
                while(inetEnum.hasMoreElements() && ip == null) {
                    ip = inetEnum.nextElement();
                    if(!this.isLocalLink == ip.isLinkLocalAddress())
                        ip = null;
                }
            }

            if(ip != null) {
                if(!this.currentIP.equals(ip)) {
                    this.currentIP = ip;

                    this.unicastSocket.close();

                    this.unicastSocket = new DatagramSocket(null);
                    InetSocketAddress isa = new InetSocketAddress(this.currentIP, this.unicastPort);
                    this.unicastSocket.bind(isa);
                    System.out.println("Listening to NEW IP =>" + this.currentIP + " PORT =>" + this.unicastPort);

                    for (TransferReceiverManager trm : this.infoReceiverManager.values())
                        trm.changeIP(this.currentIP);
                }
                else
                    for (TransferReceiverManager trm : this.infoReceiverManager.values())
                        trm.updateHasConnection();
            }
            else{
                System.out.println("        NO CONNECTION");
                for(TransferReceiverManager trm : this.infoReceiverManager.values())
                    trm.changeIP(null);
            }

        } catch (SocketException e) {
            e.printStackTrace();
        }

        this.ipChangeCheckerSES.schedule(this.checkIPChange,200, TimeUnit.MILLISECONDS);
    };


    public void kill(){
        this.run = false;
        this.unicastSocket.close();

        for (Integer id : this.infoReceiverManager.keySet())
            this.infoReceiverManager.get(id).kill();

        this.infoReceiverManager.clear();
        this.receivedIDs.clear();
    }

    private void processDatagramPacket(DatagramPacket dp){

            Object obj = getObjectFromBytes(dp.getData());
            TransferMetaInfo tmi;

            tmi = (TransferMetaInfo) obj;

            //MUDAR PARA ACEITAR OUTROS USOS DE HASH ALGORITHMS

            if(!this.receivedIDs.contains(tmi.transferID)) {
                this.receivedIDs.add(tmi.transferID);

                if (!this.dm.hasChunkManager(tmi.cmmi.Hash)) {

                    if (tmi.DocumentName != null)
                        this.dm.newDocument(tmi.cmmi.Hash, tmi.cmmi.datagramMaxSize, tmi.cmmi.numberOfChunks, tmi.DocumentName);
                    else
                        this.dm.newMessage(tmi.cmmi.Hash, tmi.cmmi.datagramMaxSize, tmi.cmmi.numberOfChunks);
                }


                TransferReceiverManager trm = new TransferReceiverManager(this, this.dm, dp.getAddress(), dp.getPort(), tmi, this.MTU, this.NICCapacity,5);
                trm.startReceiverManager();
                System.out.println("TRM STARTED");
                this.infoReceiverManager.put(tmi.transferID, trm);
            }
            else {
                this.infoReceiverManager.get(tmi.transferID).sendTransferMultiReceiverInfo();
                System.out.println("TMRI RESENT");
            }

    }
    public void run(){
        byte[] buf;
        DatagramPacket dp;

        while(this.run){
            try {
                buf = new byte[this.MTU];
                dp = new DatagramPacket(buf, this.MTU);
                this.unicastSocket.receive(dp);

                processDatagramPacket(dp);
                System.out.println("RECEIVED SOMETHING FROM " + dp.getAddress());
            }
            catch (SocketException se){
                //System.out.println("\t=>PONG DATAGRAMSOCKET CLOSED");
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Object getObjectFromBytes(byte[] data){
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInput in = null;
        Object o = null;

        try {
            in = new ObjectInputStream(bis);
            o = in.readObject();
        }
        catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        finally {
            try {
                if (in != null) {
                    in.close();
                }
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        return o;
    }

    public void endTransfer(int id) {
        this.infoReceiverManager.remove(id);
        this.receivedIDs.remove(new Integer(id));
    }
}
