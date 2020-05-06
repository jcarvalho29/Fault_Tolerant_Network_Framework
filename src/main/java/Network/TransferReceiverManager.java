package Network;

import Data.Chunk;
import Data.CompressedMissingChunksID;
import Data.DataManager;
import Messages.ChunkHeader;
import Messages.MissingChunksID;
import Messages.TransferMetaInfo;
import Messages.TransferMultiReceiverInfo;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TransferReceiverManager {
    private DataManager dm;
    private TransferMetaInfo tmi;

    private InetAddress destIP;
    private int destPort;

    private DatagramSocket unicastSocket;

    private int MTU;

    private ArrayList<FastUnicastListener> fastListeners;
    private ArrayList<Thread> fastListeners_Threads;
    private  ArrayList<Integer> fastListenersPorts;

    private int numberOfListeners;

    private ScheduledExecutorService TransferMultiReceiverInfoSES;
    private ScheduledExecutorService FastReceiversSES;

    private ArrayList<MissingChunksID> missingChunksIDS;
    private TransferMultiReceiverInfo tmri;
    private boolean TransferMultiReceiverInfoReceived;

    //TIMEOUTS

    private int consecutiveTimeouts;

    public TransferReceiverManager(DataManager dm, InetAddress destIP, int destPort, TransferMetaInfo tmi, int MTU, int numberOfListeners){
        this.TransferMultiReceiverInfoSES = Executors.newSingleThreadScheduledExecutor();
        this.FastReceiversSES = Executors.newSingleThreadScheduledExecutor();

        this.dm = dm;

        this.destIP = destIP;
        this.destPort = destPort;
        this.tmi = tmi;

        this.MTU = MTU;
        this.numberOfListeners = numberOfListeners;

        this.fastListeners = new ArrayList<FastUnicastListener>();
        this.fastListeners_Threads = new ArrayList<Thread>();
        this.fastListenersPorts = new ArrayList<Integer>();


        try {
            this.unicastSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }

        this.TransferMultiReceiverInfoReceived = false;
    }

    public void kill(){
        this.FastReceiversSES.shutdownNow();
        this.TransferMultiReceiverInfoSES.shutdownNow();
        this.unicastSocket.close();
        for(FastUnicastListener fus : this.fastListeners)
            fus.kill();
    }

    private void createFastListeners(){

        FastUnicastListener fus;

        for(int i = 0; i < numberOfListeners; i++){

            fus = new FastUnicastListener(this.MTU);
            Thread t = new Thread(fus);

            this.fastListeners.add(fus);
            this.fastListeners_Threads.add(t);
            this.fastListenersPorts.add(fus.port);
        }
    }

    private final Runnable sendTransferMultiReceiverInfo = () -> {
        if(!this.TransferMultiReceiverInfoReceived){
            try {
                byte[] data = getBytesFromObject(this.tmri);
                DatagramPacket dp = new DatagramPacket(data, data.length, this.destIP, this.destPort);
                this.unicastSocket.send(dp);
                Thread.sleep(300);

                for(MissingChunksID mcid : this.missingChunksIDS){
                    data = getBytesFromObject(mcid);
                    dp = new DatagramPacket(data, data.length, this.destIP, this.destPort);
                    this.unicastSocket.send(dp);
                    Thread.sleep(100);
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
        else
            this.TransferMultiReceiverInfoSES.shutdownNow();
    };

    private void createInitialDatagramPackets(){
        ArrayList<CompressedMissingChunksID> cmcIDs = this.dm.getCompressedMissingChunkIDs(this.tmi.cmmi.Hash, (int)(this.MTU * 0.7));
        this. missingChunksIDS = new ArrayList<MissingChunksID>();
        MissingChunksID mcID;

        int[] listenerPorts = new int[this.fastListenersPorts.size()];

        for(int i = 0; i < this.fastListenersPorts.size(); i++)
            listenerPorts[i] = this.fastListenersPorts.get(i);

        if(cmcIDs == null) {
            this.tmri = new TransferMultiReceiverInfo(this.tmi.ID, listenerPorts, 100, null);
        }
        else {
            this.tmri = new TransferMultiReceiverInfo(this.tmi.ID, listenerPorts, 100, cmcIDs.get(0));
        }

        for (int i = 1; cmcIDs != null && i < cmcIDs.size(); i++){
            mcID = new MissingChunksID(this.tmi.ID, cmcIDs.get(i));
            this.missingChunksIDS.add(mcID);
        }
    }


    private void sendMissingChunkIDs(int consecutiveTimeouts) {
        ArrayList<CompressedMissingChunksID> cmcIDs = this.dm.getCompressedMissingChunkIDs(this.tmi.cmmi.Hash, (int)(this.MTU * 0.85));
        ArrayList <MissingChunksID> mcIDs = new ArrayList<MissingChunksID>();
        MissingChunksID mcID;

        for (int i = 1; cmcIDs != null && i < cmcIDs.size(); i++){
            mcID = new MissingChunksID(this.tmi.ID, cmcIDs.get(i));
            mcIDs.add(mcID);
        }

        byte[] data;
        DatagramPacket dp;

        for(MissingChunksID mcid : mcIDs){
            try {
                data = getBytesFromObject(mcid);
                dp = new DatagramPacket(data, data.length, this.destIP, this.destPort);
                this.unicastSocket.send(dp);
                Thread.sleep(100);
            }
            catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateTimeoutStatus(boolean hasReceivedChunks){

        if(hasReceivedChunks)
            this.consecutiveTimeouts = 0;
        else
            this.consecutiveTimeouts++;

        if(this.consecutiveTimeouts > 0 && this.consecutiveTimeouts < 4)
            sendMissingChunkIDs(this.consecutiveTimeouts);
        else{
            if(this.consecutiveTimeouts == 4)
                this.kill();
        }
    }

    private final Runnable retrieveChunksFromFastListeners = () -> {
        ArrayList<Chunk> chunksReceived = new ArrayList<Chunk>();
        ArrayList<ChunkHeader> chunkHeaders;

        for(FastUnicastListener fus : this.fastListeners){
            chunkHeaders = fus.getChunkHeaders();
            if(chunkHeaders.size() > 0){
                this.TransferMultiReceiverInfoReceived = true;
            }
            for(ChunkHeader ch : chunkHeaders){
                if(ch.ID == this.tmi.ID)
                    chunksReceived.add(ch.chunk);
            }
        }

        if(chunksReceived.size()>0) {
            this.dm.addChunks(this.tmi.MacAddress, this.tmi.cmmi.Hash, chunksReceived);

            if (this.dm.isChunkManagerFull(this.tmi.cmmi.Hash)) {
                this.FastReceiversSES.shutdownNow();
                System.out.println("TRANSFER DONE");
            }
        }
        else{
            // tratar do timeout
        }
    };


    public void startReceiverManager() {
        createFastListeners();
        createInitialDatagramPackets();

        this.TransferMultiReceiverInfoSES.scheduleWithFixedDelay(sendTransferMultiReceiverInfo, 0, 10, TimeUnit.SECONDS);
        this.FastReceiversSES.scheduleWithFixedDelay(retrieveChunksFromFastListeners, 0, 1, TimeUnit.SECONDS);


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
