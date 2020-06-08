package Network;

import Data.ChunkManager;
import Data.ChunkManagerMetaInfo;
import Messages.MissingChunkIDs;
import Messages.Over;
import Messages.TransferMetaInfo;
import Messages.TransferMultiReceiverInfo;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class TransferMultiSender implements Runnable{

    public boolean run = true;
    private boolean receivedOver = false;
    public boolean wasInterrupted = false;

    private String MacAddress;
    private int ID;

    private InetAddress destIP;
    private int destUnicastPort;
    private int MTU;

    private boolean receivedTransferMultiReceiverInfo;
    private ReentrantLock TMRI_Lock;

    private ChunkManager cm;
    private ChunkManagerMetaInfo cmmi;
    private String DocumentName;
    private boolean confirmation;

    private ScheduledExecutorService transferMetaInfoSES;
    private ScheduledExecutorService timeoutSES;

    private DatagramSocket unicastSocket;
    private int unicastPort;

    private ArrayList<FastUnicastSender> fastSenders;
    private int[] transmittedMissingChunkIDs;

    private ReentrantLock timeout_Lock;
    private int consecutiveTimeouts;
    private boolean receivedMissingChunkIDs;

    private TransmitterStats stats;

    public TransferMultiSender(String MacAdress, InetAddress destIP, int destUnicastPort, int unicastPort, int MTU, ChunkManager cm, ChunkManagerMetaInfo cmmi, String docName, boolean confirmation){
        Random rand = new Random();

        this.MacAddress = MacAdress;
        this.ID = rand.nextInt();

        this.destIP = destIP;
        this.destUnicastPort = destUnicastPort;
        this.MTU = MTU;

        this.receivedTransferMultiReceiverInfo = false;
        this.TMRI_Lock = new ReentrantLock();

        this.cm = cm;
        this.cmmi = cmmi;
        this.DocumentName = docName;
        this.confirmation = confirmation;

        this.transferMetaInfoSES = Executors.newSingleThreadScheduledExecutor();
        this.timeoutSES = Executors.newSingleThreadScheduledExecutor();

        this.fastSenders = new ArrayList<FastUnicastSender>();
        this.transmittedMissingChunkIDs = null;

        this.timeout_Lock = new ReentrantLock();
        this.consecutiveTimeouts = 0;
        this.receivedMissingChunkIDs = false;

        this.stats = new TransmitterStats();
        try {
            this.unicastPort = unicastPort;
            this.unicastSocket = new DatagramSocket(this.unicastPort);
        } catch (SocketException e) {
            e.printStackTrace();
        }

    }
    private final Runnable sendTransferMetaInfo = () -> {
        this.TMRI_Lock.lock();
        if(!this.receivedTransferMultiReceiverInfo) {
            this.timeout_Lock.lock();
            this.consecutiveTimeouts++;
            this.timeout_Lock.unlock();

            if (this.consecutiveTimeouts < 13) {
                TransferMetaInfo tmi;
                ChunkManagerMetaInfo cmmi = new ChunkManagerMetaInfo(this.cmmi);
                cmmi.missingChunks = null;
                cmmi.numberOfChunksInArray = 0;
                cmmi.full = false;

                if (this.DocumentName == null)
                    tmi = new TransferMetaInfo(this.MacAddress, this.ID, cmmi, this.confirmation);
                else
                    tmi = new TransferMetaInfo(this.MacAddress, this.ID, cmmi, this.DocumentName, this.confirmation);

                byte[] info = getBytesFromObject((Object) tmi);
                System.out.println("TRANSFERMETAIFO SIZE " + info.length);

                DatagramPacket dp = new DatagramPacket(info, info.length, this.destIP, this.destUnicastPort);

                int tries = 0;
                boolean sent = false;
                while (!sent && tries < 3) {
                    try {
                        tries++;
                        this.unicastSocket.send(dp);//!!!!!!!!!!!!!!!!!!!!!!!EXCEPTION!!
                        sent = true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    this.TMRI_Lock.unlock();
                }
                System.out.println("SENT TRANSFERMETAINFO TO " + this.destIP + " " + this.destUnicastPort);
                this.stats.markTmiSendTime();
            }
        }
        else{
            this.timeout_Lock.lock();
            this.consecutiveTimeouts = 0;
            this.timeout_Lock.unlock();
            this.TMRI_Lock.unlock();
            this.transferMetaInfoSES.shutdown();
        }

    };

    private final Runnable updateTimeoutStatus = () ->{

        if(!this.receivedTransferMultiReceiverInfo){
            this.timeout_Lock.lock();
            if(this.consecutiveTimeouts >= 12){
                this.kill();
                System.out.println("KILLED DUE TO TIMEOUT");
            }
            this.timeout_Lock.unlock();
        }
        else{
            boolean sending = false;

            for (FastUnicastSender fastSender : this.fastSenders)
                sending = (sending || fastSender.isRunning());

            System.out.println("SENDING " + sending);

            this.timeout_Lock.lock();
            if(sending) {
                this.consecutiveTimeouts = 0;
            }
            else {
                if(this.receivedMissingChunkIDs) {
                    this.consecutiveTimeouts = 0;
                    this.receivedMissingChunkIDs = false;
                }
                else
                    this.consecutiveTimeouts++;
            }

            if(this.consecutiveTimeouts >= 10) {
                kill();
                System.out.println("KILLED DUE TO TIMEOUT");
            }
            this.timeout_Lock.unlock();
        }
    };

    private void processDatagramPacket(DatagramPacket dp, long receiveTime) {

        Object obj = getObjectFromBytes(dp.getData());

        if(obj instanceof TransferMultiReceiverInfo){
            this.TMRI_Lock.lock();
            this.receivedTransferMultiReceiverInfo = true;
            this.TMRI_Lock.unlock();

            this.stats.setTrmiReceiveTime(receiveTime);
            System.out.println("RTT => " + this.stats.handshakeRTT);
            TransferMultiReceiverInfo tmri = (TransferMultiReceiverInfo) obj;

            processTransferMultiReceiverInfo(tmri);
        }
        else{
            if(obj instanceof MissingChunkIDs) {
                this.timeout_Lock.lock();
                this.receivedMissingChunkIDs = true;
                this.timeout_Lock.unlock();
                MissingChunkIDs mcid = (MissingChunkIDs) obj;

                processMissingChunkIDs(mcid);
                }
            else {
                if(obj instanceof Over){
                    Over over = (Over) obj;

                    processOver(over);
                }
            }
        }
    }
    private void processTransferMultiReceiverInfo(TransferMultiReceiverInfo tmri){

        int[] mc;
        int[] mcholder;

        if(tmri.cmcID != null) {
            mcholder = this.cm.getIDsFromCompressedMissingChunksID(tmri.cmcID);
            if(this.transmittedMissingChunkIDs != null) {
                mc = new int[mcholder.length + this.transmittedMissingChunkIDs.length];
                System.arraycopy(mcholder, 0 , mc, 0, mcholder.length);
                System.arraycopy(this.transmittedMissingChunkIDs, 0 , mc, mcholder.length, this.transmittedMissingChunkIDs.length);
            }
            else {
                mc = mcholder;
            }


            mcholder = null;
            this.transmittedMissingChunkIDs = null;
        }
        else{
            mc = new int[this.cm.mi.numberOfChunks];
            for(int i = 0; i < cm.mi.numberOfChunks; i++){
                mc[i] = i + Integer.MIN_VALUE;
            }
        }

        int numberOfReceivers = tmri.ports.length;
        int chunksPerSender = Math.floorDiv(mc.length, numberOfReceivers);
        int split;
        FastUnicastSender fus;
        Thread t;

        int[] chunkIDS;

        for(int i = 0; i < numberOfReceivers; i++){
            if(i == numberOfReceivers-1) {
                split = mc.length;
                chunkIDS = new int[mc.length - chunksPerSender*i];
            } else {
                split = chunksPerSender*(i+1);
                chunkIDS = new int[chunksPerSender];
            }
            System.out.println("FROM " + chunksPerSender*i + " TO " + split + " ( " + (split - chunksPerSender*i) + " ) ARRAY SIZE OF " + chunkIDS.length);
            System.arraycopy(mc, chunksPerSender*i, chunkIDS, 0, split - chunksPerSender*i);

            fus = new FastUnicastSender(this.ID, this.destIP, tmri.ports[i], this.cm, chunkIDS, tmri.datagramPacketsPerSecondPerReceiver);
            System.out.println("SENDING TO " + tmri.ports[i] + " | " + chunksPerSender*i + " -> " + (split-1) + " | ( 0 -> " + this.cmmi.numberOfChunks + " )");
            this.fastSenders.add(fus);

            t = new Thread(fus);
            t.start();
        }
    }

    private void processMissingChunkIDs(MissingChunkIDs mcids){

        int[] mc;

        if (mcids.cmcID != null) {
            mc = this.cm.getIDsFromCompressedMissingChunksID(mcids.cmcID);
        } else {
            //System.out.println("MCID NULL");
            mc = new int[this.cm.mi.numberOfChunks];
            for (int i = 0; i < cm.mi.numberOfChunks; i++) {
                mc[i] = i;
            }
        }

        int numberOfReceivers = this.fastSenders.size();

        //ESTE IF É PORQUE PODE CHEGAR UM MCIDS ANTES DE UM TMRI E É NECESSÁRIO GUARDAR OS IDS
        if (numberOfReceivers == 0) {
            if(this.transmittedMissingChunkIDs == null)
                this.transmittedMissingChunkIDs = mc;
            else{
                int currentSize = this.transmittedMissingChunkIDs.length;
                int[] holder = new int[currentSize + mc.length];
                System.arraycopy(this.transmittedMissingChunkIDs, 0, holder, 0, currentSize);
                System.arraycopy(mc, 0, holder, currentSize, mc.length);
                this.transmittedMissingChunkIDs = holder;

                for(int a : this.transmittedMissingChunkIDs)
                    System.out.print(a + " ");
                holder = null;
            }
        }
        else {
            int chunksPerSender = Math.floorDiv(mc.length, numberOfReceivers);
            int split, initialMCSize = mc.length;
            boolean isRunning;
            FastUnicastSender fus;
            Thread t;

            //System.out.println("    CHUNKS PER SENDER " + chunksPerSender);
            for (int i = 0; i < numberOfReceivers; i++) {
                if (i == numberOfReceivers - 1)
                    split = initialMCSize;
                else
                    split = chunksPerSender * (i + 1);
                fus = this.fastSenders.get(i);
                fus.changeDPS(mcids.DatagramsPerSecondPerSender);
                isRunning = fus.addChunksToSend(copyArraySection(mc, split - chunksPerSender*i));
                mc = chopArray(mc, split - chunksPerSender*i);
                if (!isRunning) {
                    t = new Thread(fus);
                    t.start();
                }
            }
        }
    }

    private void processOver(Over over){
        if(over.ID == this.ID)
            this.receivedOver = true;

        if(over.isInterrupt) {
            this.wasInterrupted = true;
            System.out.println("=>>> INTERRUPTED");
        }
        else
            System.out.println("=>>> TRANSFER ENDED");

        this.kill();
    }

    public void kill(){
        this.run = false;
        this.timeoutSES.shutdownNow();
        this.transferMetaInfoSES.shutdownNow();
        this.unicastSocket.close();
    }

    public void run (){
        try {
            this.transferMetaInfoSES.scheduleWithFixedDelay(sendTransferMetaInfo, 0, 5, TimeUnit.SECONDS);
            this.timeoutSES.scheduleWithFixedDelay(updateTimeoutStatus, 0, 2, TimeUnit.SECONDS);
            byte[] buf;
            DatagramPacket dp;

            while(this.run){
                buf = new byte[this.MTU];
                dp = new DatagramPacket(buf, buf.length);
                this.unicastSocket.receive(dp);

                System.out.println("RECEIVED SOMETHING FROM " + dp.getAddress());
                processDatagramPacket(dp, System.currentTimeMillis());
            }

        }
        catch (SocketException se){
            //System.out.println("\t=>NBRCONFIRMATIONHANDLER DATAGRAMSOCKET CLOSED");
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int[] copyArraySection(int[] original, int length){
        //System.out.println("    COPY ARRAY " + original.length + " " + length);
        int[] res = new int[length];

        System.arraycopy(original, 0, res, 0, length);


        return res;
    }

    private int[] chopArray(int[] original, int length){
        //System.out.println("    CHOP ARRAY " +original.length + " " + length);
        int[] newOriginal = new int[original.length-length];

        System.arraycopy(original, length, newOriginal, 0, original.length-length);

        return newOriginal;
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
        if(data == null || data.length == 0) {
            System.out.println("                MENSAGEM VAZIA WHATTTTTTTTTTTTTT");
            try {
                Thread.sleep(100000000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInput in = null;
        Object o = null;

        try {
            in = new ObjectInputStream(bis);
            o = in.readObject(); //EXCEPTION!!!! java.io.EOFException
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

