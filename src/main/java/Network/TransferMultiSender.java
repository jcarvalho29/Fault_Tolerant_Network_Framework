package Network;

import Data.ChunkManager;
import Data.ChunkManagerMetaInfo;
import Messages.MissingChunksID;
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

    private boolean run = true;
    private boolean receivedOver = false;

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

    private ReentrantLock timeout_Lock;
    private int consecutiveTimeouts;
    private boolean receivedMissingChunkIDs;

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

        this.timeout_Lock = new ReentrantLock();
        this.consecutiveTimeouts = 0;
        this.receivedMissingChunkIDs = false;

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
            this.consecutiveTimeouts ++;
            this.timeout_Lock.unlock();

            TransferMetaInfo tmi;

            if (this.DocumentName == null)
                tmi = new TransferMetaInfo(this.MacAddress, this.ID, this.cmmi, this.confirmation);
            else
                tmi = new TransferMetaInfo(this.MacAddress, this.ID, this.cmmi, this.DocumentName, this.confirmation);

            byte[] info = getBytesFromObject((Object) tmi);
            System.out.println("TRANSFERMETAIFO SIZE " + info.length);

            DatagramPacket dp = new DatagramPacket(info, info.length, this.destIP, this.destUnicastPort);

            int tries = 0;
            boolean sent = false;
            while(!sent && tries < 3) {
                try {
                    tries++;
                    this.unicastSocket.send(dp);
                    sent = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                this.TMRI_Lock.unlock();
            }
            System.out.println("SENT TRANSFERMETAINFO");
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
            if(this.consecutiveTimeouts >=  3){
                this.kill();
                System.out.println("KILLED DUE TO TIMEOUT");
            }
            this.timeout_Lock.unlock();
        }
        else{
            boolean sending = false;

            for(int i = 0; !sending && i < this.fastSenders.size(); i++)
                sending = (sending || this.fastSenders.get(i).isRunning());

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
    private void processDatagramPacket(DatagramPacket dp) {

        Object obj = getObjectFromBytes(dp.getData());

        if(obj instanceof TransferMultiReceiverInfo){
            this.TMRI_Lock.lock();
            this.receivedTransferMultiReceiverInfo = true;
            this.TMRI_Lock.unlock();


            TransferMultiReceiverInfo tmri = (TransferMultiReceiverInfo) obj;

            ArrayList<Integer> mc;

            if(tmri.cmcID != null) {
                mc = this.cm.getIDsFromCompressedMissingChunksID(tmri.cmcID);
            }
            else{
                mc = new ArrayList<Integer>();
                for(int i = 0; i < cm.getNumberOfChunks(); i++){
                    mc.add(i);
                }
            }

            int numberOfReceivers = tmri.ports.length;
            int chunksPerSender = Math.floorDiv(mc.size(), numberOfReceivers);
            int split;
            FastUnicastSender fus;
            Thread t;

            ArrayList<Integer> chunkIDS = new ArrayList<Integer>();

            for(int i = 0; i < numberOfReceivers; i++){
                if(i == numberOfReceivers-1)
                    split = mc.size();
                else
                    split = chunksPerSender*(i+1);

                chunkIDS.clear();
                for(int j = chunksPerSender*i; j < split; j++)
                    chunkIDS.add(j + Integer.MIN_VALUE);

                fus = new FastUnicastSender(this.ID, this.destIP, tmri.ports[i], this.cm, new ArrayList<>(chunkIDS), tmri.datagramPacketsPerSecondPerReceiver);
                System.out.println("SENDING TO " + tmri.ports[i] + " | " + chunksPerSender*i + " -> " + (split-1) + " | ( 0 -> " + this.cmmi.numberOfChunks + " )");
                this.fastSenders.add(fus);

                t = new Thread(fus);
                t.start();
            }

        }
        else{
            if(obj instanceof MissingChunksID){
                //System.out.println("ITS A MISSINGCHUNKIDS");
                this.timeout_Lock.lock();
                this.receivedMissingChunkIDs = true;
                this.timeout_Lock.unlock();
                MissingChunksID mcid = (MissingChunksID) obj;

                ArrayList<Integer> mc;

                if(mcid.cmcID != null) {
                    mc = this.cm.getIDsFromCompressedMissingChunksID(mcid.cmcID);
                    //System.out.println("MCID NOT NULL " + mc.size());
                }
                else{
                    //System.out.println("MCID NULL");
                    mc = new ArrayList<Integer>();
                    for(int i = 0; i < cm.getNumberOfChunks(); i++){
                        mc.add(i);
                    }
                }

                int numberOfReceivers = this.fastSenders.size();
                int chunksPerSender = Math.floorDiv(mc.size(), numberOfReceivers);
                int split;
                boolean isRunning;
                FastUnicastSender fus;
                Thread t;

                //System.out.println("    CHUNKS PER SENDER " + chunksPerSender);
                for(int i = 0; i < numberOfReceivers; i++){
                    if(i == numberOfReceivers-1)
                        split = mc.size();
                    else
                        split = chunksPerSender*(i+1);

                    fus = this.fastSenders.get(i);
                    isRunning = fus.addChunksToSend(copyArrayListSection(mc, chunksPerSender*i,split));
                    //System.out.println("ADDED MISSING CHUNKS TO SEND LIST");
                    if(!isRunning) {
                        t = new Thread(fus);
                        t.start();
                        //System.out.println("WASN'T RUNNING");
                    }
                }
            }
            else {
                if(obj instanceof Over){
                    Over over = (Over) obj;

                    if(over.ID == this.ID)
                        this.receivedOver = true;

                    ///////////////!!!!!!!!!ATUALIZAR PARA ACEITAR INTERRUPCOES
                    if(over.isInterrupt) {
                        this.kill();
                        System.out.println("KILLED DUE TO RECEIVED OVER");
                    }
                    else{
                        this.kill();
                        System.out.println("INTERRUPTED");
                    }
                }
            }
        }
    }

    public void kill(){
        this.run = false;
        this.timeoutSES.shutdownNow();
        this.transferMetaInfoSES.shutdownNow();
        this.unicastSocket.close();
    }

    public void run (){
        try {
            this.transferMetaInfoSES.scheduleWithFixedDelay(sendTransferMetaInfo, 0, 10, TimeUnit.SECONDS);
            this.timeoutSES.scheduleWithFixedDelay(updateTimeoutStatus, 0, 2, TimeUnit.SECONDS);
            byte[] buf;
            DatagramPacket dp;

            while(this.run){
                buf = new byte[this.MTU];
                dp = new DatagramPacket(buf, buf.length);
                this.unicastSocket.receive(dp);

                System.out.println("RECEIVED SOMETHING");
                processDatagramPacket(dp);
            }

        }
        catch (SocketException se){
            //System.out.println("\t=>NBRCONFIRMATIONHANDLER DATAGRAMSOCKET CLOSED");
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ArrayList<Integer> copyArrayListSection(ArrayList<Integer> original, int start, int end){
        ArrayList<Integer> res = new ArrayList<>();

        for(int i = start; i < end; i++)
            res.add(original.get(i));

        return res;
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

