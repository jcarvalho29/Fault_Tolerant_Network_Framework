package Network;

import Data.Chunk;
import Data.CompressedMissingChunksID;
import Data.DataManager;
import Messages.*;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class TransferReceiverManager implements Runnable{
    private ListenerMainUnicast mainListener;
    private DataManager dm;
    private TransferMetaInfo tmi;
    private ReceiverStats stats;

    private InetAddress destIP;
    private int destPort;

    private DatagramSocket unicastSocket;

    private int MTU;

    private long receivedDPDuringCycle;
    private ArrayList<FastUnicastListener> fastListeners;
    private  ArrayList<Integer> fastListenersPorts;

    private int numberOfListeners;

    private ScheduledExecutorService FastReceiversSES;

    private ArrayList<MissingChunkIDs> missingChunksIDS;
    private TransferMultiReceiverInfo tmri;
    private boolean TransferMultiReceiverInfoReceived;

    //TIMEOUTS
    private int consecutiveTimeouts;
    private boolean run = true;

    public TransferReceiverManager(ListenerMainUnicast ml, DataManager dm, InetAddress destIP, int destPort, TransferMetaInfo tmi, int MTU, int numberOfListeners){
        this.FastReceiversSES = Executors.newSingleThreadScheduledExecutor();

        this.mainListener = ml;
        this.dm = dm;
        this.stats = new ReceiverStats(MTU-100);

        this.destIP = destIP;
        this.destPort = destPort;
        this.tmi = tmi;

        this.MTU = MTU;
        this.numberOfListeners = numberOfListeners;

        this.receivedDPDuringCycle = 0;
        this.fastListeners = new ArrayList<FastUnicastListener>();
        this.fastListenersPorts = new ArrayList<Integer>();


        try {
            this.unicastSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }

        this.TransferMultiReceiverInfoReceived = false;
    }

    public void kill(){
        this.run = false;
        this.FastReceiversSES.shutdownNow();
        this.unicastSocket.close();

        for(FastUnicastListener fus : this.fastListeners)
            fus.kill();

        this.fastListeners.clear();
        this.fastListenersPorts.clear();
        this.mainListener.remove(this.tmi.ID);
    }

    private void createFastListeners(){
        FastUnicastListener fus;

        for(int i = 0; i < numberOfListeners; i++){

            fus = new FastUnicastListener(this.MTU);
            Thread t = new Thread(fus);

            t.start();
            this.fastListeners.add(fus);
            this.fastListenersPorts.add(fus.port);
            //System.out.println("PORT " + fus.port);
        }
    }

    public void sendTransferMultiReceiverInfo(){
        if(!this.TransferMultiReceiverInfoReceived){
            try {
                byte[] data = getBytesFromObject(this.tmri);
                DatagramPacket dp = new DatagramPacket(data, data.length, this.destIP, this.destPort);
                this.unicastSocket.send(dp);
                System.out.println("                    NEW TRMI SEND TIME");
                this.stats.markTrmiSendTime();
                Thread.sleep(50);

                for(MissingChunkIDs mcid : this.missingChunksIDS){
                    data = getBytesFromObject(mcid);
                    dp = new DatagramPacket(data, data.length, this.destIP, this.destPort);
                    this.unicastSocket.send(dp);
                    Thread.sleep(75);
                }

                this.stats.markTransferStartTime();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void createInitialDatagramPackets(){

        this. missingChunksIDS = new ArrayList<MissingChunkIDs>();
        MissingChunkIDs mcID;

        int[] listenerPorts = new int[this.fastListenersPorts.size()];

        for(int i = 0; i < this.fastListenersPorts.size(); i++)
            listenerPorts[i] = this.fastListenersPorts.get(i);

        ArrayList<CompressedMissingChunksID> cmcIDs = this.dm.getCompressedMissingChunkIDs(this.tmi.cmmi.Hash, (int)(this.MTU * 0.7));

        if(cmcIDs == null) {
            System.out.println("Sending CMCID NULL");
            this.tmri = new TransferMultiReceiverInfo(this.tmi.ID, listenerPorts, 1000, null);
        }
        else {
            System.out.println("SENDING CMCID WITH IDs");
            this.tmri = new TransferMultiReceiverInfo(this.tmi.ID, listenerPorts, 1000, cmcIDs.get(0));
        }

        for (int i = 1; cmcIDs != null && i < cmcIDs.size(); i++){
            ////////!!!!!!!!!!!!! MUDIFICAR O DPS PARA EFIETO DE FEEDBACK NO CONTROLO DE FLUXO
            mcID = new MissingChunkIDs(this.tmi.ID, cmcIDs.get(i), 1000);
            this.missingChunksIDS.add(mcID);
        }
    }


    private void sendMissingChunkIDs() {
        ArrayList<CompressedMissingChunksID> cmcIDs = this.dm.getCompressedMissingChunkIDs(this.tmi.cmmi.Hash, (int)(this.MTU * 0.85));
        ArrayList <MissingChunkIDs> mcIDs = new ArrayList<MissingChunkIDs>();
        MissingChunkIDs mcID;

        byte [] teste;

        for(CompressedMissingChunksID cmcid : cmcIDs){
            teste = getBytesFromObject(cmcid);
            System.out.println("MAX => " + this.MTU*0.85 + " ACTUAL SIZE => " + teste.length);
        }

        ////////!!!!!!!!!!!!! MUDIFICAR O DPS PARA EFIETO DE FEEDBACK NO CONTROLO DE FLUXO
        for (int i = 0; cmcIDs != null && i < cmcIDs.size(); i++){
            mcID = new MissingChunkIDs(this.tmi.ID, cmcIDs.get(i), 1000);
            mcIDs.add(mcID);
        }

        byte[] data;
        DatagramPacket dp;

        this.stats.markMCIDsSendTime();
        for(MissingChunkIDs mcid : mcIDs){
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

    private void updateCycleStats(){

        if(this.stats.getNumberOfTransferCycles() > 1) {
            long min = Long.MAX_VALUE;
            long timestamp;
            for (FastUnicastListener fus : this.fastListeners) {
                timestamp = fus.getFirstCMReceivedTimestamp();
                fus.resetFirstCMReceivedTimestamp();

                if (timestamp < min) {
                    min = timestamp;
                }

            }
            System.out.println("                NOT THE CYCLE");

            this.stats.markFirstRetransmittedCMReceivedTime(min);
        }
        else{
            for(FastUnicastListener fus : this.fastListeners)
                fus.resetFirstCMReceivedTimestamp();
        }

        //System.out.println("Marked cycle end");
        this.stats.registerDPReceivedInCycle(this.receivedDPDuringCycle);
        //System.out.println("Registered the CM received");
        this.receivedDPDuringCycle = 0;

        if(this.tmi.DocumentName == null) {
            this.stats.registerDPExpectedInCycle(this.dm.messages.get(this.tmi.cmmi.Hash).getNumberOfMissingChunks());
            //System.out.println("Registered the expected CM for the next cycle");
        } else {
            this.stats.registerDPExpectedInCycle(this.dm.documents.get(this.tmi.cmmi.Hash).cm.getNumberOfMissingChunks());
            //System.out.println("Registered the expected CM for the next cycle");
        }

        this.stats.markTransferCycleEnding();
        this.stats.markTransferCycleBeginning();
        //System.out.println("Cycle Stats updated");
    }

    private void updateTimeoutStatus(boolean hasReceivedChunks){

        if(hasReceivedChunks)
            this.consecutiveTimeouts = 0;
        else {
            this.consecutiveTimeouts++;
            System.out.println("TIMEOUT " + this.consecutiveTimeouts);
        }
        if(this.tmi.Confirmation){
            if (this.consecutiveTimeouts > 0 && this.consecutiveTimeouts < 60) {
                if(this.consecutiveTimeouts == 3) {
                    updateCycleStats();
                    sendMissingChunkIDs();
                    System.out.println("SENT MISSING CHUNKS DUE TO TIMEOUT!! == 3");
                }
                if(this.consecutiveTimeouts%12 == 0) {
                    sendMissingChunkIDs();
                    System.out.println("SENT MISSING CHUNKS DUE TO TIMEOUT!! %10");
                }
            }
            else {
                if (this.consecutiveTimeouts > 60){
                    this.kill();
                    System.out.println("KILLED! WAY TO MANY TIMEOUTS");
                    updateCycleStats();
                }

            }
        }
        else{
            this.kill();
            System.out.println("KILLED! DOESN'T NEED CONFIRMATION");
            updateCycleStats();
        }
    }

    private final Runnable retrieveChunksFromFastListeners = () -> {

    };

    private void sendOver(boolean wasInterrupted) {

                Over over= new Over(tmi.ID, wasInterrupted);

                byte[] data = getBytesFromObject(over);

                DatagramPacket dp = new DatagramPacket(data, data.length, destIP, destPort);

                int tries = 0;
                stats.markTransferEndTime();

                while(tries < 10) {
                    try {
                        unicastSocket.send(dp);
                        tries++;
                        Thread.sleep(300);
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println("OVER SENT");
                }
                stats.markProtocolEndTime();
                stats.printStats();

    }



    public void startReceiverManager() {

        this.stats.markProtocolStartTime();

        if(!this.dm.isChunkManagerFull(this.tmi.cmmi.Hash)) {
            if(this.fastListeners.size() == 0)
                createFastListeners();

            createInitialDatagramPackets();

            sendTransferMultiReceiverInfo();

            if(this.tmi.DocumentName == null)
                this.stats.registerDPExpectedInCycle(this.dm.messages.get(this.tmi.cmmi.Hash).getNumberOfMissingChunks());
            else
                this.stats.registerDPExpectedInCycle(this.dm.documents.get(this.tmi.cmmi.Hash).cm.getNumberOfMissingChunks());

            Thread t = new Thread(this);
            t.start();
        }
        else {
            sendOver(false);
            this.kill();
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

    private int getSleepTime(int cycleExecTime){
        int cycle = this.stats.getNumberOfTransferCycles();
        int rtt;
        int sleepTime = 0;

        if(cycle == 1){
            System.out.println("FIRST CYCLE");
            rtt = this.stats.handshakeRTT;
        }
        else{
            rtt = this.stats.getAverageRTT();
        }


        if(rtt == -1) {
            if(cycleExecTime < 500)
                sleepTime = 500 - cycleExecTime;
        }
        else {
            if(rtt > 150 && cycleExecTime < rtt)
                sleepTime = rtt - cycleExecTime;
            else
                sleepTime = 150;
        }

        return sleepTime;
    }

    @Override
    public void run() {

        long cycleStart;
        long cycleEnd;
        int tmri_Dropped = 0;
        boolean isFirstCycle = true;

        while(this.run) {
            cycleStart = System.currentTimeMillis();


            ArrayList<Chunk> chunksReceived = new ArrayList<Chunk>();
            ArrayList<ChunkMessage> chunkHeaders;

            for (FastUnicastListener fus : this.fastListeners) {
                chunkHeaders = fus.getChunkHeaders();
                for (ChunkMessage ch : chunkHeaders) {
                    if (ch.ID == this.tmi.ID) {
                        chunksReceived.add(ch.chunk);
                    }
                }
            }

            if (chunksReceived.size() > 0) {

                if(isFirstCycle){
                    isFirstCycle = false;
                    long min = Long.MAX_VALUE;
                    long receiveTime;
                    for(FastUnicastListener fus : this.fastListeners){
                        receiveTime = fus.getFirstCMReceivedTimestamp();
                        if (min > receiveTime)
                            min = receiveTime;
                    }
                    this.stats.setFirstChunkReceivedTime(min);
                    this.stats.setTransferCycleBeginning(min);
                }
                this.TransferMultiReceiverInfoReceived = true;
                this.receivedDPDuringCycle += chunksReceived.size();

                updateTimeoutStatus(true);
                this.dm.addChunks(this.tmi.cmmi.Hash, new ArrayList<Chunk>(chunksReceived));

                if (this.dm.isChunkManagerFull(this.tmi.cmmi.Hash)) {
                    this.FastReceiversSES.shutdownNow();
                    System.out.println("TRANSFER DONE");
                    updateCycleStats();
                    sendOver(false);
                    this.kill();
                }
            }
            else {
                if(this.TransferMultiReceiverInfoReceived || tmri_Dropped > 10) {
                    System.out.println("TIMEOUT TMRI_DROPPED => " + tmri_Dropped);
                    updateTimeoutStatus(false);
                }
                else {
                    tmri_Dropped++;
                    System.out.println("INC TMRI_DROPPED TO " + tmri_Dropped);
                    if(tmri_Dropped%5 == 0) {
                        sendTransferMultiReceiverInfo();
                        System.out.println("SENT TIMEOUT TMRI");
                    }
                }
            }

            cycleEnd = System.currentTimeMillis();

            int cycleExecTime = (int) (cycleEnd - cycleStart);
            int sleepTime = getSleepTime(cycleExecTime);
            try {
                System.out.println("SLEEP FOR 3*" + sleepTime);
                Thread.sleep(3*sleepTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
