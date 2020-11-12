package Network;

import Data.DataManager;
import Messages.IPChange;
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

    private NIC nic;
    private DatagramSocket unicastSocket;
    private InetAddress currentIP;
    private boolean isLocalLink;
    private int unicastPort;
    private boolean hasConnection;

    private int NICCapacity;

    private HashMap<Integer, TransferReceiverManager> infoReceiverManager;


    private DataManager dm;

    private ArrayList<Integer> receivedIDs;

    public ListenerMainUnicast(DataManager dm, NIC nic, boolean isLocalLink, int unicastPort, int NICCapacity){

        this.dm = dm;
        this.nic = nic;
        this.isLocalLink = isLocalLink;
        this.currentIP = null;
        this.unicastPort = unicastPort;
        this.hasConnection = false;


        this.NICCapacity = NICCapacity;

        this.infoReceiverManager = new HashMap<Integer, TransferReceiverManager>();
        this.receivedIDs = new ArrayList<Integer>();

        changeIP(nic.addresses);
    }

    public void changeIP(ArrayList<InetAddress> addresses) {

        if(!addresses.contains(this.currentIP)) {
            InetAddress ip = null;

            for (InetAddress addr : addresses) {
                if (addr.isLinkLocalAddress() == this.isLocalLink)
                    ip = addr;
            }

            if (ip != null) {
                try {
                    this.currentIP = ip;

                    if(this.unicastSocket != null) {
                        this.unicastSocket.close();
                        System.out.println("CLOSED UNICASTSOCKET!!");
                    }

                    this.unicastSocket = new DatagramSocket(null);
                    InetSocketAddress isa = new InetSocketAddress(this.currentIP, this.unicastPort);
                    this.unicastSocket.bind(isa);

                    System.out.println("hasConnection? " + this.hasConnection);

                    this.hasConnection = true;
                    Thread t = new Thread(this);
                    t.start();
                    System.out.println("CHANGED IP AND CREATED NEW THREAD");

                    System.out.println("Listening to NEW IP =>" + this.currentIP + " PORT =>" + this.unicastPort + "\nhasConnection? " + this.hasConnection);

                    for (TransferReceiverManager trm : this.infoReceiverManager.values())
                        trm.changeIP(this.currentIP);
                }
                catch (SocketException e) {
                    e.printStackTrace();
                    System.out.println(ip);
                }
            }
            else {
                System.out.println("NEW IP SET BUT NO CORRESPONDING IP (hasConnection => FALSE)");
                updateConnectionStatus(false);
            }
        }
        else {
            System.out.println("SAME IP AS BEFORE");
            if (!this.hasConnection) {
                System.out.println("    BUT HAD NO CONNECTION (hasConnection => TRUE)");
                updateConnectionStatus(true);
            }
        }
    }

    public void updateConnectionStatus(boolean value){

        boolean oldHasConnection = this.hasConnection;
        this.hasConnection = value;

        for (TransferReceiverManager trm : this.infoReceiverManager.values())
            trm.updateHasConnection(value);

        if(value && !oldHasConnection) {
            System.out.println("Listening to NEW IP =>" + this.currentIP + " PORT =>" + this.unicastPort + " NO NEW THREAD");
/*            Thread t = new Thread(this);
            t.start();*/
        }

    }

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

        if(obj instanceof TransferMetaInfo) {

            TransferMetaInfo tmi;
            tmi = (TransferMetaInfo) obj;

            //MUDAR PARA ACEITAR OUTROS USOS DE HASH ALGORITHMS

            if (!this.receivedIDs.contains(tmi.transferID)) {
                this.receivedIDs.add(tmi.transferID);

                if (!this.dm.hasChunkManager(tmi.cmmi.Hash)) {

                    if (tmi.DocumentName != null)
                        this.dm.newDocument(tmi.cmmi.Hash, tmi.cmmi.datagramMaxSize, tmi.cmmi.numberOfChunks, tmi.DocumentName);
                    else
                        this.dm.newMessage(tmi.cmmi.Hash, tmi.cmmi.datagramMaxSize, tmi.cmmi.numberOfChunks);
                }


                TransferReceiverManager trm = new TransferReceiverManager(this, this.dm, dp.getAddress(), dp.getPort(), this.nic, tmi, 1);
                trm.startReceiverManager();
                System.out.println("TRM STARTED");
                this.infoReceiverManager.put(tmi.transferID, trm);
            } else {
                this.infoReceiverManager.get(tmi.transferID).sendTransferMultiReceiverInfo();
                System.out.println("TMRI RESENT");
            }
        }
        else{
            if(obj instanceof IPChange){
                IPChange ipc = (IPChange) obj;

                if(this.receivedIDs.contains(ipc.transferID)){
                    TransferReceiverManager trm = this.infoReceiverManager.get(ipc.transferID);

                    trm.changeDestIP(ipc.newIP);
                    System.out.println("SENDER CHANGED IP");
                }
            }
        }
    }
    public void run(){
        System.out.println("NEW (LISTENERMAINUNICAST)");

        int MTU;
        int tries = 0;

        while((MTU = this.nic.getMTU()) == -1 && tries < 4) {
            try {
                tries++;
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (tries == 4)
            MTU = 1500;

        byte[] buf = new byte[MTU];
        DatagramPacket dp;
        dp = new DatagramPacket(buf, MTU); // EXCEPTION o MTU PODE SER 0 QUANDO nenhum NIC TEM UMA CONEXÃO ON STARTUP (COM O SLEEP EM CIMA NAO)

        while(this.hasConnection && this.run){
            try {
                this.unicastSocket.receive(dp);

                processDatagramPacket(dp);
                System.out.println("RECEIVED SOMETHING FROM " + dp.getAddress());

                buf = new byte[MTU];
                dp = new DatagramPacket(buf, MTU);
            }
            catch (SocketException se){
                if(!this.unicastSocket.isClosed()) {
                    System.out.println("SAY WHAT!?!?!?!??!?!?!?!?!?!?!?!?!?!?!?!?!?!?!?!?!?!");
                    this.unicastSocket.close();
                }
                System.out.println("\t=>LMU DATAGRAMSOCKET CLOSED? " + this.unicastSocket.isClosed());

                //close = true;
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("DIED hasConnection? " + this.hasConnection + " run? " + this.run);
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
