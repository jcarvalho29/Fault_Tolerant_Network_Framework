package Network;

import Data.DataManager;
import Messages.TransferMetaInfo;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;

public class ListenerMainUnicast implements Runnable{

    private boolean run = true;

    private DatagramSocket unicastSocket;
    private String ip;
    private int unicastPort;
    private int MTU;
    private int NICCapacity;

    private HashMap<Integer, TransferReceiverManager> infoReceiverManager;


    private DataManager dm;

    private ArrayList<Integer> receivedIDs;

    public ListenerMainUnicast(DataManager dm, String ip, int unicastPort, int MTU, int NICCapacity){

        this.dm = dm;
        this.ip = ip;
        this.unicastPort = unicastPort;
        this.MTU = MTU;
        this.NICCapacity = NICCapacity;

        this.receivedIDs = new ArrayList<Integer>();

        this.infoReceiverManager = new HashMap<Integer, TransferReceiverManager>();

        try {

            this.unicastSocket = new DatagramSocket(null);
            InetSocketAddress isa = new InetSocketAddress(this.ip, this.unicastPort);
            this.unicastSocket.bind(isa);
        } catch (SocketException e) {
            e.printStackTrace();
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
            TransferMetaInfo tmi = null;

            tmi = (TransferMetaInfo) obj;

            //MUDAR PARA ACEITAR OUTROS USOS DE HASH ALGORITHMS

            if(!this.receivedIDs.contains(tmi.ID)) {
                System.out.println("NAO RECEBI ESTE ID");
                this.receivedIDs.add(tmi.ID);

                if (!this.dm.hasChunkManager(tmi.cmmi.Hash)) {
                    System.out.println("AINDA NAO TENHO ESTE CM");

                    if (tmi.DocumentName != null)
                        this.dm.newDocument(tmi.cmmi.Hash, tmi.cmmi.datagramMaxSize, tmi.cmmi.numberOfChunks, tmi.DocumentName);
                    else
                        this.dm.newMessage(tmi.MacAddress, tmi.cmmi.Hash, tmi.cmmi.datagramMaxSize, tmi.cmmi.numberOfChunks);
                }


                TransferReceiverManager trm = new TransferReceiverManager(this, this.dm, dp.getAddress(), dp.getPort(), tmi, this.MTU, this.NICCapacity,20);
                trm.startReceiverManager();
                System.out.println("TRM STARTED");
                this.infoReceiverManager.put(tmi.ID, trm);
            }
            else
                this.infoReceiverManager.get(tmi.ID).sendTransferMultiReceiverInfo();

    }
    public void run(){
        try {
            byte[] buf;
            DatagramPacket dp;

            while(this.run){
                buf = new byte[this.MTU];
                dp = new DatagramPacket(buf, this.MTU);
                this.unicastSocket.receive(dp);

                processDatagramPacket(dp);
                System.out.println("RECEIVED SOMETHING FROM " + dp.getAddress());
            }

        }
        catch (SocketException se){
            //System.out.println("\t=>PONG DATAGRAMSOCKET CLOSED");
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] getBytesFromObject(Object obj) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = null;

        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(obj);
            out.flush();

        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            try {
                bos.close();
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        byte[] data = bos.toByteArray();

        return data;
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

    public void remove(int id) {
        this.infoReceiverManager.remove(id);
        this.receivedIDs.remove(new Integer(id));
    }
}
