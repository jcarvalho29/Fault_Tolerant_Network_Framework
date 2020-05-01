package Network;

import Data.ChunkManager;
import Data.ChunkManagerMetaInfo;
import Messages.MissingChunksID;
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

public class InfoMultiSender implements Runnable{

    private boolean run = true;

    private String MacAddress;
    private int ID;

    private InetAddress IP;
    private int destUnicastPort;
    private int MTU;

    private boolean receivedTransferMultiReceiverInfo;
    private ReentrantLock TMRI_Lock;

    private ChunkManager cm;
    private ChunkManagerMetaInfo cmmi;
    private String DocumentName;
    private boolean save;
    private boolean confirmation;

    private ScheduledExecutorService ses;
    private DatagramSocket unicastSocket;
    private int unicastPort;

    private ArrayList<FastUnicastSender> fastSenders;
    private ArrayList<Thread> fastSendersThreads;

    public InfoMultiSender(String MacAdress, InetAddress IP, int destUnicastPort, int unicastPort, int MTU, ChunkManager cm, ChunkManagerMetaInfo cmmi, boolean save, boolean confirmation){
        Random rand = new Random();

        this.MacAddress = MacAdress;
        this.ID = rand.nextInt();

        this.IP = IP;
        this.destUnicastPort = destUnicastPort;
        this.MTU = MTU;

        this.receivedTransferMultiReceiverInfo = false;
        this.TMRI_Lock = new ReentrantLock();

        this.cm = cm;
        this.cmmi = cmmi;
        this.DocumentName = null;
        this.save = save;
        this.confirmation = confirmation;

        this.ses = Executors.newSingleThreadScheduledExecutor();

        this.fastSenders = new ArrayList<FastUnicastSender>();
        this.fastSendersThreads = new ArrayList<Thread>();


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
            TransferMetaInfo tmi;

            if (this.DocumentName == null)
                tmi = new TransferMetaInfo(this.MacAddress, this.ID, this.cmmi, this.save, this.confirmation);
            else
                tmi = new TransferMetaInfo(this.MacAddress, this.ID, this.cmmi, this.DocumentName, this.save, this.confirmation);

            byte[] info = getBytesFromObject((Object) tmi);

            DatagramPacket dp = new DatagramPacket(info, info.length, this.IP, this.destUnicastPort);

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
            this.TMRI_Lock.unlock();
            this.ses.shutdown();
        }

    };

    private void processDatagramPacket(DatagramPacket dp) {

        Object obj = getObjectFromBytes(dp.getData());

        if(obj instanceof TransferMultiReceiverInfo){
            this.TMRI_Lock.lock();
            this.receivedTransferMultiReceiverInfo = true;
            this.TMRI_Lock.unlock();


            TransferMultiReceiverInfo tmi = (TransferMultiReceiverInfo) obj;

            ArrayList<Integer> mc;

            if(tmi.cmcID != null) {
                mc = this.cm.getIDsFromCompressedMissingChunksID(tmi.cmcID);
            }
            else{
                mc = new ArrayList<Integer>();
                for(int i = 0; i < cm.getNumberOfChunks(); i++){
                    mc.add(i);
                }
            }

            int numberOfReceivers = tmi.ports.length;
            int chunksPerSender = Math.floorDiv(mc.size(), numberOfReceivers);
            int split;
            FastUnicastSender fus;
            Thread t;



            for(int i = 0; i < numberOfReceivers; i++){
                if(i == numberOfReceivers-1)
                    split = mc.size();
                else
                    split = chunksPerSender*(i+1);

                fus = new FastUnicastSender(this.ID, this.IP, this.destUnicastPort, this.cm, (ArrayList<Integer>)mc.subList(chunksPerSender*i,split), tmi.datagramPacketsPerSecondPerReceiver);
                this.fastSenders.add(fus);

                t = new Thread(fus);
                fastSendersThreads.add(t);
            }

        }
        else{
            if(obj instanceof MissingChunksID){

                MissingChunksID mcid = (MissingChunksID) obj;

                ArrayList<Integer> mc;

                if(mcid.cmcID != null) {
                    mc = this.cm.getIDsFromCompressedMissingChunksID(mcid.cmcID);
                }
                else{
                    mc = new ArrayList<Integer>();
                    for(int i = 0; i < cm.getNumberOfChunks(); i++){
                        mc.add(i);
                    }
                }

                int numberOfReceivers = this.fastSenders.size();
                int chunksPerSender = Math.floorDiv(mc.size(), numberOfReceivers);
                int split;

                for(int i = 0; i < numberOfReceivers; i++){
                    if(i == numberOfReceivers-1)
                        split = mc.size();
                    else
                        split = chunksPerSender*(i+1);

                    this.fastSenders.get(i).addChunksToSend((ArrayList<Integer>) mc.subList(chunksPerSender*i,split));
                }
            }
        }
    }

    public void kill(){
        this.run = false;
        this.ses.shutdownNow();
        this.unicastSocket.close();
    }

    public void run (){
        try {
            this.ses.scheduleWithFixedDelay(sendTransferMetaInfo, 0, 10, TimeUnit.SECONDS);
            byte[] buf;
            DatagramPacket dp;

            while(this.run){
                buf = new byte[this.MTU];
                dp = new DatagramPacket(buf, buf.length);
                this.unicastSocket.receive(dp);

                processDatagramPacket(dp);

            }

        }catch (SocketException se){
            //System.out.println("\t=>NBRCONFIRMATIONHANDLER DATAGRAMSOCKET CLOSED");
        }catch (IOException e) {
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

