package Network;

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
    private int NICCapacity;

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

    public TransferReceiverManager(ListenerMainUnicast ml, DataManager dm, InetAddress destIP, int destPort, TransferMetaInfo tmi, int MTU, int NICCapacity, int numberOfListeners){
        this.FastReceiversSES = Executors.newSingleThreadScheduledExecutor();

        this.mainListener = ml;
        this.dm = dm;
        this.stats = new ReceiverStats(MTU, NICCapacity, numberOfListeners, dm.documents.get(tmi.cmmi.Hash).cm.getNumberOfMissingChunks());

        this.destIP = destIP;
        this.destPort = destPort;
        this.tmi = tmi;

        this.MTU = MTU;
        this.NICCapacity = NICCapacity;
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

            fus = new FastUnicastListener(this.MTU, this.stats.getDPS());
            System.out.println("CREATED FASUNICAST WITH DPS AT " + this.stats.getDPS());
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
                    Thread.sleep(2);
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
            System.out.println("Sending CMCID NULL WITH DPS " + this.stats.getDPS());
            this.tmri = new TransferMultiReceiverInfo(this.tmi.ID, listenerPorts, this.stats.getDPS(), null);
        }
        else {
            System.out.println("SENDING CMCID WITH IDs WITH DPS " + this.stats.getDPS());
            this.tmri = new TransferMultiReceiverInfo(this.tmi.ID, listenerPorts, this.stats.getDPS(), cmcIDs.get(0));
        }

        for (int i = 1; cmcIDs != null && i < cmcIDs.size(); i++){
            ////////!!!!!!!!!!!!! MUDIFICAR O DPS PARA EFIETO DE FEEDBACK NO CONTROLO DE FLUXO
            mcID = new MissingChunkIDs(this.tmi.ID, cmcIDs.get(i), this.stats.getDPS());
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
        //retirei a confirmação de null
        int dps = this.stats.getDPS();
        for (int i = 0; i < cmcIDs.size(); i++){
            mcID = new MissingChunkIDs(this.tmi.ID, cmcIDs.get(i), dps);
            mcIDs.add(mcID);
        }
        System.out.println("MISSINGCHUNKIDS WITH DPS " + dps);

        byte[] data;
        DatagramPacket dp;

        this.stats.markMCIDsSendTime();
        for(MissingChunkIDs mcid : mcIDs){
            try {
                data = getBytesFromObject(mcid);
                dp = new DatagramPacket(data, data.length, this.destIP, this.destPort);
                this.unicastSocket.send(dp);
                Thread.sleep(5);
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

            int avgRTT = this.stats.getAverageRTT();
            int dps = this.stats.getDPS();
            for(FastUnicastListener ful: this.fastListeners)
                ful.changeByteArraysSize(avgRTT, dps);
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

        this.stats.calculateDPS();

        this.stats.markTransferCycleEnding();
        this.stats.markTransferCycleBeginning();
        //System.out.println("Cycle Stats updated");
    }

    private void updateTimeoutStatus(boolean hasReceivedChunks){

        if(hasReceivedChunks)
            this.consecutiveTimeouts = 0;
        else {
            this.consecutiveTimeouts++;
            //System.out.println("TIMEOUT " + this.consecutiveTimeouts);
        }
        if(this.tmi.Confirmation){
            if (this.consecutiveTimeouts > 0 && this.consecutiveTimeouts < 60) {
                if(this.consecutiveTimeouts == 3) {
                    updateCycleStats();
                    System.out.println("SENT MISSING CHUNKS DUE TO TIMEOUT!! == 3");
                    sendMissingChunkIDs();
                }
                if(this.consecutiveTimeouts%12 == 0) {
                    System.out.println("SENT MISSING CHUNKS DUE TO TIMEOUT!! %12");
                    sendMissingChunkIDs();
                }
            }
            else {
                if (this.consecutiveTimeouts > 60){
                    sendOver(true);
                    updateCycleStats();
                    this.kill();
                    System.out.println("KILLED! WAY TO MANY TIMEOUTS");
                }
            }
        }
        else{
            sendOver(false);
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
        int sleepTime = -cycleExecTime;

        if(cycle == 1){
            //System.out.println("FIRST CYCLE");
            rtt = this.stats.handshakeRTT;
        }
        else{
            rtt = this.stats.getAverageRTT();
        }


        System.out.println("CYCLE EXEC TIME " + cycleExecTime);
        if(rtt == -1)
            sleepTime += 500;
        else
            sleepTime += Math.max(rtt, 50);


        switch (this.consecutiveTimeouts) {
            case 1: {
                sleepTime *= 2;
                break;
            }
            case 2: {
                sleepTime *= 3;
                break;
            }
            case 3: {
                sleepTime *= 4;
                break;
            }
            default:
                break;
        }

        return sleepTime;
    }

    @Override
    public void run() {

        long cycleStart;
        long cycleEnd;

        int tmri_Dropped = 0;
        boolean isFirstCycle = true;
        boolean isCMFull = false;

        int receivedChunkMessages = 0;
        while(this.run) {
            cycleStart = System.currentTimeMillis();

            ArrayList<ChunkMessage[]> chunksReceived = new ArrayList<ChunkMessage[]>();
            ChunkMessage[] chunkHeaders;

            receivedChunkMessages = 0;
            for (FastUnicastListener ful : this.fastListeners) {
                chunkHeaders = ful.getChunkHeaders();
                receivedChunkMessages += chunkHeaders.length;
                chunksReceived.add(chunkHeaders);
            }

            if (receivedChunkMessages > 0) {

                ChunkMessage[] cmArray = new ChunkMessage[receivedChunkMessages];
                int numberOfCMinArray = 0;
                for (ChunkMessage[] cm : chunksReceived) {
                    System.arraycopy(cm, 0, cmArray, numberOfCMinArray, cm.length);
                    numberOfCMinArray += cm.length;
                }

                isCMFull = this.dm.addChunks(this.tmi.cmmi.Hash, cmArray);

                //UPDATE STATS
                if(isFirstCycle){
                    isFirstCycle = false;
                    long min = Long.MAX_VALUE;
                    long receiveTime;
                    for(FastUnicastListener ful : this.fastListeners){
                        receiveTime = ful.getFirstCMReceivedTimestamp();
                        if (min > receiveTime) {
                            min = receiveTime;
                        }
                    }
                    this.stats.setFirstChunkReceivedTime(min);
                    this.stats.setTransferCycleBeginning(min);


                    int avgRTT = this.stats.getAverageRTT();
                    int dps = this.stats.getDPS();
                    for(FastUnicastListener ful: this.fastListeners)
                        ful.changeByteArraysSize(avgRTT, dps);
                }

                this.TransferMultiReceiverInfoReceived = true;
                this.receivedDPDuringCycle += receivedChunkMessages;

                updateTimeoutStatus(true);

                if (isCMFull) {
                    this.FastReceiversSES.shutdownNow();
                    System.out.println("TRANSFER DONE");
                    updateCycleStats();
                    sendOver(false);
                    this.dm.changeIsFullEntry(this.tmi.cmmi.Hash, true);
                    this.kill();
                }
            }
            else {
                if(this.TransferMultiReceiverInfoReceived || tmri_Dropped > 10) {
                    //System.out.println("TIMEOUT TMRI_DROPPED => " + tmri_Dropped);
                    updateTimeoutStatus(false);
                }
                else {
                    tmri_Dropped++;
                    if(tmri_Dropped%5 == 0) {
                        sendTransferMultiReceiverInfo();
                        System.out.println("SENT TIMEOUT TMRI");
                    }
                }
            }

            cycleEnd = System.currentTimeMillis();

            int cycleExecTime = (int) (cycleEnd - cycleStart);
            int sleepTime = getSleepTime(cycleExecTime);
            if(this.run && sleepTime > 0) {
                try {
                    //System.out.println("SLEEP FOR " + sleepTime);
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
