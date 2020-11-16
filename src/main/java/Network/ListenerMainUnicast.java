package Network;

import Data.DataManager;
import Messages.IPChange;
import Messages.TransferMetaInfo;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;

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

        if(this.isLocalLink)
            changeIP(nic.getLinkLocalAddresses());
        else
            changeIP(nic.getNONLinkLocalAddresses());
    }

    private void bindDatagramSocket(){
        boolean bound = false;
        while(!bound) {
            try {
                this.unicastSocket = new DatagramSocket(null);
                InetSocketAddress isa = new InetSocketAddress(this.currentIP, this.unicastPort);
                this.unicastSocket.bind(isa);
                System.out.println("(LISTENERMAINUNICAST) BOUND TO " + this.currentIP + ":" + this.unicastPort);
                bound = true;
            } catch (SocketException e) {
                //e.printStackTrace();
                System.out.println("(LISTENERMAINUNICAST) ERROR BINDING TO " + this.currentIP + ":" + this.unicastPort);
            }

            if(!bound){
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void changeIP(ArrayList<InetAddress> addresses) {

        if(!addresses.contains(this.currentIP)) {
            InetAddress ip = null;

            for (InetAddress addr : addresses) {
                if (addr.isLinkLocalAddress() == this.isLocalLink)
                    ip = addr;
            }

            if (ip != null) {
                this.currentIP = ip;

                if(this.unicastSocket != null && !this.unicastSocket.isClosed()) {
                    System.out.println("UNICASTSOCKET WASN'T CLOSED BEFORE THE CHANGEIP BIND");
                    this.unicastSocket.close();
                }

                bindDatagramSocket();

                System.out.println("hasConnection? " + this.hasConnection + " => TRUE");

                this.hasConnection = true;
                Thread t = new Thread(this);
                t.start();
                System.out.println("CHANGED IP AND CREATED NEW THREAD (changeIP)");

                System.out.println("Listening to NEW IP =>" + this.currentIP + " PORT =>" + this.unicastPort + "\nhasConnection? " + this.hasConnection);

                for (TransferReceiverManager trm : this.infoReceiverManager.values())
                    trm.changeIP(this.currentIP);
            }
            else {
                System.out.println("NEW ADDRESSES SET BUT NO CORRESPONDING IP (hasConnection => FALSE)");
                this.currentIP = null;
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
            System.out.println("UPDATED CONNECTION STATUS FROM " + oldHasConnection + " TO " + value);

        if(value && !oldHasConnection && this.currentIP != null){
            bindDatagramSocket();
            new Thread(this).start();
            System.out.println("Listening to NEW IP =>" + this.currentIP + " PORT =>" + this.unicastPort + " (updateConnectionStatus)");
        }
        else{
            if(!value && this.unicastSocket != null)
                this.unicastSocket.close();
        }

        for (TransferReceiverManager trm : this.infoReceiverManager.values())
            trm.updateHasConnection(value);

/*        if(value && !oldHasConnection) {
           Thread t = new Thread(this);
            t.start();
        }*/

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

            //CHANGE PARA ACEITAR OUTROS USOS DE HASH ALGORITHMS ??

            if (!this.receivedIDs.contains(tmi.transferID)) {
                this.receivedIDs.add(tmi.transferID);

                if (!this.dm.hasChunkManager(tmi.cmmi.Hash)) {

                    if (tmi.DocumentName != null)
                        this.dm.newDocument(tmi.cmmi.Hash, tmi.cmmi.datagramMaxSize, tmi.cmmi.numberOfChunks, tmi.DocumentName);
                    else
                        this.dm.newMessage(tmi.cmmi.Hash, tmi.cmmi.datagramMaxSize, tmi.cmmi.numberOfChunks);
                }


                TransferReceiverManager trm = new TransferReceiverManager(this, this.dm, this.currentIP, dp.getAddress(), dp.getPort(), this.nic, tmi, 1);
                trm.startReceiverManager();
                System.out.println("TRM STARTED");
                this.infoReceiverManager.put(tmi.transferID, trm);
            }
            else {
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
        System.out.println("NEW LISTENERMAINUNICAST THREAD");

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

        while(!this.unicastSocket.isClosed() && this.hasConnection && this.run){
            try {
                this.unicastSocket.receive(dp);

                processDatagramPacket(dp);
                System.out.println("RECEIVED SOMETHING FROM " + dp.getAddress());

                buf = new byte[MTU];
                dp = new DatagramPacket(buf, MTU);
            }
            catch (SocketException se){
                while(!this.unicastSocket.isClosed()) {
                    System.out.println("SAY WHAT!?!?!?!??!?!?!?!?!?!?!?!?!?!?!?!?!?!?!?!?!?!");
                    this.unicastSocket.close();
                }
                System.out.println("\t=>LMU DATAGRAMSOCKET CLOSED? " + this.unicastSocket.isClosed());
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("DIED unicastSocket.isClosed? " + this.unicastSocket.isClosed() + " hasConnection? " + this.hasConnection + " run? " + this.run);
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
        this.receivedIDs.remove((Object)id);
    }
}
