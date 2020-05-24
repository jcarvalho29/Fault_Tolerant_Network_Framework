package Network;

import Data.DataManager;
import Messages.TransferMetaInfo;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;

public class ListenerMainUnicast implements Runnable{

    private boolean run = true;

    private DatagramSocket unicastSocket;
    private int unicastPort;
    private int MTU;

    private HashMap<String, TransferReceiverManager> infoReceiverManager;


    private DataManager dm;

    private ArrayList<Integer> receivedIDs;

    public ListenerMainUnicast(DataManager dm, int unicastPort, int MTU){

        this.dm = dm;
        this.unicastPort = unicastPort;
        this.MTU = MTU;

        this.receivedIDs = new ArrayList<Integer>();

        this.infoReceiverManager = new HashMap<String, TransferReceiverManager>();

        try {

            this.unicastSocket = new DatagramSocket(this.unicastPort);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }


    public void kill(){
        this.run = false;
        this.unicastSocket.close();
        for (String hash : this.infoReceiverManager.keySet())
            this.infoReceiverManager.get(hash).kill();
    }

    private void processDatagramPacket(DatagramPacket dp){

            Object obj = getObjectFromBytes(dp.getData());
            TransferMetaInfo tmi = null;

            tmi = (TransferMetaInfo) obj;

            //MUDAR PARA ACEITAR OUTROS USOS DE HASH ALGORITHMS
            if (tmi != null) {
                System.out.println("TMI NAO É NULO");
                if(!this.receivedIDs.contains(tmi.ID)) {
                    System.out.println("NAO RECEBI ESTE ID");
                    this.receivedIDs.add(tmi.ID);
                    if (!this.dm.hasChunkManager(tmi.cmmi.Hash)) {
                        System.out.println("AIND NAO TENHO ESTE CM");
                        if (tmi.DocumentName != null)
                            this.dm.newDocument(tmi.MacAddress, tmi.cmmi.Hash, tmi.cmmi.numberOfChunks, tmi.DocumentName);
                        else
                            this.dm.newMessage(tmi.MacAddress, tmi.cmmi.Hash, tmi.cmmi.numberOfChunks);

                        System.out.println("HERE");
                    }
                    //!!!!!!!!!!!!!!!!!!!!!!! TRATAR DO CASO EM QUE JÁ POSSUI O CM

                    TransferReceiverManager trm = new TransferReceiverManager(this.dm, dp.getAddress(), dp.getPort(), tmi, this.MTU, 10);
                    trm.startReceiverManager();
                    System.out.println("TRM STARTED");
                    this.infoReceiverManager.put(tmi.cmmi.Hash, trm);
                }
                else
                    System.out.println("ALREADY HAVE THE ID");
            }
            else {
                System.out.println("TMI NULL ");
            }
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
                System.out.println("RECEIVED SOMETHING");
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
}
