package Network;

import Data.CompressedMissingChunksID;
import Data.DataManager;
import Messages.*;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public class TransferReceiverManager implements Runnable{

    private ListenerMainUnicast mainListener;
    private DataManager dm;
    private TransferMetaInfo tmi;
    private ReceiverStats stats;

    public InetAddress destIP;
    private int destPort;
    private ReentrantLock destIPLock;

    private DatagramSocket unicastSocket;
    private InetAddress ownIP;
    private int ownPort;

    private boolean hasConnection;

    private NIC nic;

    private long receivedDPDuringCycle;
    private ArrayList<FastUnicastListener> fastListeners;
    private  ArrayList<Integer> fastListenersPorts;

    private int numberOfListeners;


    private ArrayList<MissingChunkIDs> missingChunksIDS;
    private TransferMultiReceiverInfo tmri;
    private boolean TransferMultiReceiverInfoReceived;

    //TIMEOUTS
    private int consecutiveTimeouts;
    private boolean hasUpdatedCycleStats;
    private boolean run = true;

    public TransferReceiverManager(ListenerMainUnicast ml, DataManager dm, InetAddress ownIP, InetAddress destIP, int destPort, NIC nic, TransferMetaInfo tmi){

        this.mainListener = ml;
        this.dm = dm;

        this.nic = nic;
        this.stats = new ReceiverStats(this.nic, tmi.firstLinkSpeed, dm.documents.get(tmi.cmmi.Hash).cm.getNumberOfMissingChunks());

        this.destIP = destIP;
        this.destPort = destPort;
        this.destIPLock = new ReentrantLock();

        this.tmi = tmi;

        this.numberOfListeners = this.stats.getNumberOfListeners();

        this.receivedDPDuringCycle = 0;
        this.fastListeners = new ArrayList<FastUnicastListener>();
        this.fastListenersPorts = new ArrayList<Integer>();

        this.ownIP = ownIP;
        Random rand = new Random();
        boolean done = false;

        while(!done){
            try {
                this.ownPort = rand.nextInt(999) + 5000;
                this.unicastSocket = new DatagramSocket(null);
                InetSocketAddress isa = new InetSocketAddress(this.ownIP, this.ownPort);
                this.unicastSocket.bind(isa);
                this.hasConnection = true;
                done = true;
            } catch (SocketException e) {
                e.printStackTrace();
                System.out.println("(TRM) PORT COLLISION");
            }
        }

        this.TransferMultiReceiverInfoReceived = false;
    }

    private void bindDatagramSocket(){
        boolean bound = false;

        while(!bound) {
            try {
                this.unicastSocket = new DatagramSocket(null);
                InetSocketAddress isa = new InetSocketAddress(this.ownIP, this.ownPort);
                this.unicastSocket.bind(isa);
                System.out.println("(TRANSFERRECEIVERMANAGER) BOUND TO " + this.ownIP + ":" + this.ownPort);
                this.hasConnection = true;
                bound = true;
            } catch (SocketException e) {
                System.out.println("(TRANSFERRECEIVERMANAGER) ERROR BINDING TO " + this.ownIP + ":" + this.ownPort);
                //e.printStackTrace();
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

    public void kill(){
        this.run = false;
        this.unicastSocket.close();

        for(FastUnicastListener fus : this.fastListeners)
            fus.kill();

        this.fastListeners.clear();
        this.fastListenersPorts.clear();
        this.mainListener.endTransfer(this.tmi.transferID);
    }

    public void updateHasConnection(boolean value){
        this.hasConnection = value;
    }

    public void sendNetworkStatusUpdate(InetAddress newIP, int dps, boolean calculateDPS){
        System.out.println("                        START SEND NETWORK STATUS");
        this.unicastSocket.close();
        this.ownIP = newIP;
        bindDatagramSocket();

        //enviar multiplos ipchanges ao transmissor para que este saiba que mudei de IP
        if(calculateDPS){
            int avgRTT = this.stats.getAverageRTT();
            dps = this.stats.getDPS();
            int newDPS = this.stats.calculateDPS();

            if(dps != newDPS) {
                for (FastUnicastListener ful : this.fastListeners)
                    ful.changeDatagramPacketsArraySize(avgRTT, newDPS);
                dps = newDPS;
            }
        }

        NetworkStatusUpdate nsu = new NetworkStatusUpdate(this.tmi.transferID, dps, newIP);
        byte[] serializedIPC = getBytesFromObject(nsu);

        this.destIPLock.lock();
        DatagramPacket packet = new DatagramPacket(serializedIPC, serializedIPC.length, this.destIP, this.destPort);
        this.destIPLock.unlock();

        for (int i = 0; i < 5; i++) {
            try {
                if(!this.unicastSocket.isClosed())
                    this.unicastSocket.send(packet);
                System.out.println("                            SENT NSU");
                Thread.sleep(5);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        FastUnicastListener ful;
        int numberOfFUL = this.fastListeners.size();
        for (int i = 0; i < numberOfFUL; i++) {
            ful = this.fastListeners.get(i);
            ful.changeIP(newIP);
            System.out.println("CHANGED FUL IP");
        }

        this.hasConnection = true;
    }

    public void changeDestIP(InetAddress newDestIP){
        this.destIPLock.lock();
        this.destIP = newDestIP;
        this.destIPLock.unlock();
    }

    public void changeConnectionSpeed(int senderConnectionSpeed){
        this.stats.updateSenderConnectionSpeed(senderConnectionSpeed);

        int avgRTT = this.stats.getAverageRTT();
        int dps = this.stats.getDPS();
        int newDPS = this.stats.calculateDPS();

        if(dps != newDPS) {
            for (FastUnicastListener ful : this.fastListeners)
                ful.changeDatagramPacketsArraySize(avgRTT, newDPS);
            dps = newDPS;
            sendNetworkStatusUpdate(this.ownIP, dps, false);
        }
    }

    private void createFastListeners(){
        FastUnicastListener ful;

        for(int i = 0; i < numberOfListeners; i++){

            ful = new FastUnicastListener(this.nic.getMTU(), this.stats.getDPS());
            //System.out.println("CREATED FASUNICAST WITH DPS AT " + this.stats.getDPS());
            Thread t = new Thread(ful);

            t.start();
            this.fastListeners.add(ful);
            this.fastListenersPorts.add(ful.port);
        }
    }

    public void sendTransferMultiReceiverInfo(){
        if(!this.TransferMultiReceiverInfoReceived){
            try {
                byte[] data = getBytesFromObject(this.tmri);

                this.destIPLock.lock();
                    DatagramPacket dp = new DatagramPacket(data, data.length, this.destIP, this.destPort);
                this.destIPLock.unlock();

                this.unicastSocket.send(dp);
                System.out.println("                    NEW TRMI SEND TIME");
                this.stats.markTrmiSendTime();
                Thread.sleep(50);

                for(MissingChunkIDs mcid : this.missingChunksIDS){
                    data = getBytesFromObject(mcid);

                    this.destIPLock.lock();
                        dp = new DatagramPacket(data, data.length, this.destIP, this.destPort);
                    this.destIPLock.unlock();

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

        ArrayList<CompressedMissingChunksID> cmcIDs = this.dm.getCompressedMissingChunkIDs(this.tmi.cmmi.Hash, (int)(this.nic.getMTU() * 0.7));

        if(cmcIDs == null) {
            System.out.println("Sending CMCID NULL WITH DPS " + this.stats.getDPS());
            this.tmri = new TransferMultiReceiverInfo(this.tmi.transferID, listenerPorts, this.stats.getDPS(), null);
        }
        else {
            System.out.println("SENDING CMCID WITH IDs WITH DPS " + this.stats.getDPS());
            this.tmri = new TransferMultiReceiverInfo(this.tmi.transferID, listenerPorts, this.stats.getDPS(), cmcIDs.get(0));
        }

        for (int i = 1; cmcIDs != null && i < cmcIDs.size(); i++){
            ////////!!!!!!!!!!!!! MUDIFICAR O DPS PARA EFIETO DE FEEDBACK NO CONTROLO DE FLUXO
            mcID = new MissingChunkIDs(this.tmi.transferID, cmcIDs.get(i), this.stats.getDPS());
            this.missingChunksIDS.add(mcID);
        }
    }


    private void sendMissingChunkIDs() {
        ArrayList<CompressedMissingChunksID> cmcIDs = this.dm.getCompressedMissingChunkIDs(this.tmi.cmmi.Hash, (int)(this.nic.getMTU() * 0.85));
        ArrayList <MissingChunkIDs> mcIDs = new ArrayList<MissingChunkIDs>();
        MissingChunkIDs mcID;

/*        byte [] teste;

        for(CompressedMissingChunksID cmcid : cmcIDs){
            teste = getBytesFromObject(cmcid);
            System.out.println("MAX => " + this.MTU*0.85 + " ACTUAL SIZE => " + teste.length);
        }*/

        ////////!!!!!!!!!!!!! MUDIFICAR O DPS PARA EFIETO DE FEEDBACK NO CONTROLO DE FLUXO
        //retirei a confirmação de null
        int dps = this.stats.getDPS();
        for (int i = 0; i < cmcIDs.size(); i++){
            mcID = new MissingChunkIDs(this.tmi.transferID, cmcIDs.get(i), dps);
            mcIDs.add(mcID);
        }
        System.out.println("MISSINGCHUNKIDS WITH DPS " + dps + " TO " + this.destIP + ":" + this.destPort);

        byte[] data;
        DatagramPacket dp;

        this.stats.markMCIDsSendTime();
        for(MissingChunkIDs mcid : mcIDs){
            if(this.hasConnection)
                try {
                    data = getBytesFromObject(mcid);

                    this.destIPLock.lock();
                        dp = new DatagramPacket(data, data.length, this.destIP, this.destPort);
                    this.destIPLock.unlock();

                    this.unicastSocket.send(dp);
                    Thread.sleep(5);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            finally {
                    this.destIPLock.lock();
                    this.destIPLock.unlock();
                }
        }
    }

    private void updateCycleStats(){
        this.stats.registerNewDPS();

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
            System.out.println("                NOT THE 1 CYCLE");

            this.stats.markFirstRetransmittedCMReceivedTime(min);

            int avgRTT = this.stats.getAverageRTT();
            int currentDPS = this.stats.getDPS();
            int newDPS = this.stats.calculateDPS();

            if(newDPS != currentDPS) {
                for (FastUnicastListener ful : this.fastListeners)
                    ful.changeDatagramPacketsArraySize(avgRTT, newDPS);
            }
        }
        else{
            for(FastUnicastListener ful : this.fastListeners)
                ful.resetFirstCMReceivedTimestamp();
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

        if(hasReceivedChunks) {
            this.consecutiveTimeouts = 0;
            this.hasUpdatedCycleStats = false;
        }
        else {
            this.consecutiveTimeouts++;
            //System.out.println("TIMEOUT " + this.consecutiveTimeouts);
        }
        if(this.tmi.Confirmation){
            if (this.hasConnection && this.consecutiveTimeouts > 0 && this.consecutiveTimeouts < 60) {
                if(this.consecutiveTimeouts == 3) {
                    this.hasUpdatedCycleStats = true;
                    updateCycleStats();
                    System.out.println("SENT MISSING CHUNKS DUE TO TIMEOUT!! == 3");
                    sendMissingChunkIDs();
                }
                if(this.consecutiveTimeouts%3 == 0 && this.consecutiveTimeouts/3 >= 4) {
                    if(!this.hasUpdatedCycleStats) {
                        this.hasUpdatedCycleStats = true;
                        updateCycleStats();
                    }
                    System.out.println("SENT MISSING CHUNKS DUE TO TIMEOUT!! %12");
                    sendMissingChunkIDs();
                }
            }
            else {
                if (this.consecutiveTimeouts > 60){
                    updateCycleStats();
                    sendOver(true);
                    this.kill();
                    System.out.println("KILLED! WAY TO MANY TIMEOUTS");
                }
            }
        }
        else{
            updateCycleStats();
            sendOver(false);
            this.kill();
            System.out.println("KILLED! DOESN'T NEED CONFIRMATION");
        }
    }

    private void sendOver(boolean wasInterrupted) {

        //CHANGE
        this.nic.registerTransferEnd();

        Over over = new Over(this.tmi.transferID, wasInterrupted);

        byte[] data = getBytesFromObject(over);

        this.destIPLock.lock();
            DatagramPacket dp = new DatagramPacket(data, data.length, this.destIP, this.destPort);
        this.destIPLock.unlock();

        int tries = 0;
        this.stats.markTransferEndTime();

        while(tries < 10) {
            if (this.hasConnection) {
                try {
                    this.unicastSocket.send(dp);
                    tries++;
                    Thread.sleep(300);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("OVER SENT TO " + this.destIP + ":" + this.destPort);
            }
        }
        this.stats.markProtocolEndTime();
        this.stats.printStats();

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

        return bos.toByteArray();
    }

    private int getSleepTime(int cycleExecTime){
        //CHANGE talvez mudificar este tempo tendo em consideração a % de perdas do CICLO
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


        //System.out.println("CYCLE EXEC TIME " + cycleExecTime);
        if(rtt == -1)
            sleepTime += 500;
        else {
            if(this.nic.isWireless)
                sleepTime += Math.max(rtt, 200);
            else
                sleepTime += Math.max(rtt, 100);

        }

        switch (this.consecutiveTimeouts) {
            case 0:{
                break;
            }
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
            default: {
                sleepTime *= (5 + this.consecutiveTimeouts%10);
                break;
            }
        }

/*        if(!this.hasConnection) {
            sleepTime *= 10;
            System.out.println("        NO CONNECTION sleeptime*10 " + sleepTime);
        }*/

        sleepTime = Math.min(sleepTime, 5000);

        return sleepTime;
    }

    @Override
    public void run() {

        long cycleStart;
        int cycleExecTime, sleepTime;

        int tmri_Dropped = 0;
        boolean isFirstCycle = true;
        boolean isCMFull;

        int receivedChunkMessages;

        while(this.run) {
            //System.out.println("                CYCLE BEGINNING");
            cycleStart = System.currentTimeMillis();

            //System.out.println("                    COLLECT CHUNK MESSAGES");
            ArrayList<ChunkMessage[]> chunksReceived = new ArrayList<ChunkMessage[]>();
            ChunkMessage[] chunkMessages;

            receivedChunkMessages = 0;
            for (FastUnicastListener ful : this.fastListeners) {
                //System.out.println("got ful");
                chunkMessages = ful.getChunkMessages();
                //System.out.println("got chunkMessages");
                receivedChunkMessages += chunkMessages.length;
                chunksReceived.add(chunkMessages);
                //System.out.println("added chunkMessages to arrayList");
            }

            if (receivedChunkMessages > 0) {
                //System.out.println("                    REGISTER CHUNKS RECEIVED");

                ChunkMessage[] cmArray = new ChunkMessage[receivedChunkMessages];
                int numberOfCMinArray = 0;
                for (ChunkMessage[] cm : chunksReceived) {
                    System.arraycopy(cm, 0, cmArray, numberOfCMinArray, cm.length);
                    numberOfCMinArray += cm.length;
                }

                chunksReceived.clear();
                isCMFull = this.dm.addChunks(this.tmi.cmmi.Hash, cmArray);


                //UPDATE STATS
                if(isFirstCycle){
                    //System.out.println("                    UPDATE FIRST CYCLE STATS");
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
                        ful.changeDatagramPacketsArraySize(avgRTT, dps);
                }

                this.TransferMultiReceiverInfoReceived = true;
                this.receivedDPDuringCycle += receivedChunkMessages;

                //System.out.println("                UPDATE TIMEOUT STATUS");
                updateTimeoutStatus(true);

                if (isCMFull) {
                    updateCycleStats();
                    System.out.println("TRANSFER DONE");
                    sendOver(false);
                    this.kill();
                    this.dm.changeIsFullEntry(this.tmi.cmmi.Hash, true);
                }
            }
            else {
                //System.out.println("                DIDN'T RECEIVE CHUNK MESSAGES");
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

            cycleExecTime = (int) (System.currentTimeMillis() - cycleStart);
             sleepTime = getSleepTime(cycleExecTime);
            if(this.run && sleepTime > 0) {
                try {
                    System.out.println("          SLEEP FOR " + sleepTime + " | exec time " + cycleExecTime + " | TIMEOUT " + this.consecutiveTimeouts);
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        System.out.println("TRANSFER RECEIVER MANAGER WHILE CYCLE DIED");
    }
}
