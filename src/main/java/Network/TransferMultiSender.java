package Network;

import Data.ChunkManager;
import Messages.*;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class TransferMultiSender{

    private Scheduler sch;

    public boolean run = true;
    public boolean wasInterrupted = false;
    private boolean hasConnection;

    private int nodeIdentifier;
    private int transferID;

    private String nicName;
    private InetAddress ownIP;
    private int ownPort;

    private InetAddress destIP;
    private int destUnicastPort;

    private boolean receivedTransferMultiReceiverInfo;
    private ReentrantLock TMRI_Lock;

    private ChunkManager cm;
    private boolean confirmation;

    private ScheduledExecutorService timeoutSES;


    private ArrayList<FastUnicastSender> fastSenders;
    private int[] transmittedMissingChunkIDs;

    private ReentrantLock timeout_Lock;
    private int consecutiveTimeouts;
    private boolean receivedMissingChunkIDs;

    private TransmitterStats stats;

    public TransferMultiSender(Scheduler sch, int nodeIdentifier, int transferID, String nicName, InetAddress ownIP, int ownPort, InetAddress destIP, int destUnicastPort, ChunkManager cm, boolean confirmation){
        this.sch = sch;

        this.hasConnection = true;

        this.nodeIdentifier = nodeIdentifier;
        this.transferID = transferID;

        this.nicName = nicName;
        this.ownIP = ownIP;
        this.ownPort = ownPort;
        this.destIP = destIP;
        this.destUnicastPort = destUnicastPort;

        this.receivedTransferMultiReceiverInfo = false;
        this.TMRI_Lock = new ReentrantLock();

        this.cm = cm;
        this.confirmation = confirmation;

        this.timeoutSES = Executors.newSingleThreadScheduledExecutor();
        this.timeoutSES.scheduleWithFixedDelay(updateTimeoutStatus, 0, 2, TimeUnit.SECONDS);

        this.fastSenders = new ArrayList<FastUnicastSender>();
        this.transmittedMissingChunkIDs = null;

        this.timeout_Lock = new ReentrantLock();
        this.consecutiveTimeouts = 0;
        this.receivedMissingChunkIDs = false;

        this.stats = new TransmitterStats();

        //this.ownUnicastPort = ownUnicastPort;

        //changeOwnIP(nic.addresses, false);

    }

/*    private final Runnable sendTransferMetaInfo = () -> {
        this.TMRI_Lock.lock();
        if(!this.receivedTransferMultiReceiverInfo) {
            this.timeout_Lock.lock();
            this.consecutiveTimeouts++;
            this.timeout_Lock.unlock();

            if (this.consecutiveTimeouts < 13) {
                TransferMetaInfo tmi;
                ChunkManagerMetaInfo cmmi = new ChunkManagerMetaInfo(this.cm.mi);
                cmmi.missingChunks = null;
                cmmi.numberOfChunksInArray = 0;
                cmmi.full = false;

                if (this.DocumentName == null)
                    tmi = new TransferMetaInfo(this.nodeIdentifier, this.transferID, this.nic.getSpeed()/1000, this.nic.isWireless, this. cmmi, this.confirmation);
                else
                    tmi = new TransferMetaInfo(this.nodeIdentifier, this.transferID, this.nic.getSpeed()/1000, this.nic.isWireless, cmmi, this.DocumentName, this.confirmation);

                byte[] info = getBytesFromObject((Object) tmi);
                System.out.println("TRANSFERMETAIFO SIZE " + info.length);

                DatagramPacket dp = new DatagramPacket(info, info.length, this.destIP, this.destUnicastPort);

                int tries = 0;
                boolean sent = false;
                while (this.hasConnection && !sent && tries < 3) {
                    try {
                        tries++;
                        this.unicastSocket.send(dp);//!!!!!!!!!!!!!!!!!!!!!!!EXCEPTION!!
                        sent = true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    this.TMRI_Lock.unlock();
                }

                if(sent) {
                    System.out.println("SENT TRANSFERMETAINFO TO " + this.destIP + " " + this.destUnicastPort);
                    this.stats.markTmiSendTime();
                }
                else{
                    System.out.println("NO CONNECTION WHILE TRYING TO SEND TRANSFERMETAINFO");
                }
            }
        }
        else{
            this.timeout_Lock.lock();
            this.consecutiveTimeouts = 0;
            this.timeout_Lock.unlock();
            this.TMRI_Lock.unlock();
            this.transferMetaInfoSES.shutdown();
        }

    };*/

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

            if(this.consecutiveTimeouts >= 30) {
                kill();
                this.sch.markAsInterrupted(this.nicName, this.transferID);
                System.out.println("KILLED DUE TO TIMEOUT");
            }
            this.timeout_Lock.unlock();
        }
    };

/*    private void processDatagramPacket(DatagramPacket dp) {

        Object obj = getObjectFromBytes(dp.getData());
        InetAddress dpAddress;

        if(obj instanceof TransferMultiReceiverInfo){

            TransferMultiReceiverInfo tmri = (TransferMultiReceiverInfo) obj;

            processTransferMultiReceiverInfo(tmri);
        }
        else{
            if(obj instanceof MissingChunkIDs) {

                    processMissingChunkIDs(mcid);
                }
            }
            else {
                if(obj instanceof IPChange){
                    IPChange ipc = (IPChange) obj;


                }
                if(obj instanceof Over){
                    Over over = (Over) obj;


                }
            }
        }
    }*/

    public void processTransferMultiReceiverInfo(TransferMultiReceiverInfo tmri, long receiveTime){

        this.TMRI_Lock.lock();
        this.receivedTransferMultiReceiverInfo = true;
        this.TMRI_Lock.unlock();

        this.stats.setTrmiReceiveTime(receiveTime);
        System.out.println("RTT => " + this.stats.handshakeRTT);
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

            fus = new FastUnicastSender(this.transferID, this.destIP, tmri.ports[i], this.ownIP, this.cm, chunkIDS, tmri.datagramPacketsPerSecondPerReceiver);
            System.out.println("SENDING TO " + tmri.ports[i] + " | " + chunksPerSender*i + " -> " + (split-1) + " | ( 0 -> " + this.cm.mi.numberOfChunks + " )");
            this.fastSenders.add(fus);

            t = new Thread(fus);
            t.start();
        }
    }

    public void processMissingChunkIDs(MissingChunkIDs mcids){

        this.timeout_Lock.lock();
        this.receivedMissingChunkIDs = true;
        this.timeout_Lock.unlock();

/*        if(this.transferID == mcid.transferID) {
            dpAddress = dp.getAddress();
            if(!dpAddress.equals(this.destIP)){
                changeFastUnicastSendersDestIP(dpAddress);
                this.destIP = dpAddress;
            }*/

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

/*                for(int a : this.transmittedMissingChunkIDs)
                    System.out.print(a + " ");*/
                holder = null;
            }
        }
        else {
            FastUnicastSender fus;
            //inicializar estrutura
            boolean[] chunksStillNotSent = new boolean[this.cm.mi.numberOfChunks];
            boolean[] fusChunksStillNotSent;
            chunksStillNotSent[0] = false;
            for (int i = 1; i < chunksStillNotSent.length; i += i) {
                System.arraycopy(chunksStillNotSent, 0, chunksStillNotSent, i, Math.min((chunksStillNotSent.length - i), i));
            }

            for(int i = 0; i < numberOfReceivers; i++){
                fus = this.fastSenders.get(i);
                fusChunksStillNotSent = fus.getChunksIDToSend();

                for(int j = 0; j < this.cm.mi.numberOfChunks; j++){
                    chunksStillNotSent[j] = fusChunksStillNotSent[j] || chunksStillNotSent[j];
                }
            }

            int[] processedMC = new int[mc.length];
            int processedMCPointer = 0;
            int id;

            for(int i = 0; i < mc.length; i++){
                id = mc[i] - Integer.MIN_VALUE;
                if(!chunksStillNotSent[id]){
                    processedMC[processedMCPointer] = mc[i];
                    processedMCPointer++;
                }
                else
                    System.out.println("REMOVED A REPETITIVE EMISSION ( " + id + " )");
            }

            int[] aux = new int[processedMCPointer];
            System.arraycopy(processedMC, 0, aux, 0, processedMCPointer);
            processedMC = aux;

            int chunksPerSender = Math.floorDiv(processedMCPointer, numberOfReceivers);
            int split, initialMCSize = processedMCPointer;
            boolean isRunning;
            Thread t;

            //System.out.println("    CHUNKS PER SENDER " + chunksPerSender);
            for (int i = 0; i < numberOfReceivers; i++) {
                if (i == numberOfReceivers - 1)
                    split = initialMCSize;
                else
                    split = chunksPerSender * (i + 1);
                fus = this.fastSenders.get(i);
                fus.changeDPS(mcids.DatagramsPerSecondPerSender);
                isRunning = fus.addChunksToSend(copyArraySection(processedMC, split - chunksPerSender*i));
                processedMC = chopArray(processedMC, split - chunksPerSender*i);
                if (!isRunning) {
                    t = new Thread(fus);
                    t.start();
                }
            }
        }
    }

    public void processIPChange(IPChange ipc, InetAddress newIP){
        if(this.transferID == ipc.transferID && !this.destIP.equals(newIP)) {
            this.destIP = newIP;
            changeFastUnicastSendersDestIP(this.destIP);
        }
    }

    public void processOver(Over over){
        if(over.transferID == this.transferID) {
            if (over.isInterrupt) {
                this.wasInterrupted = true;
                this.sch.markAsInterrupted(nicName, transferID);
                System.out.println("=>>> INTERRUPTED");
            } else {
                this.sch.markAsFinished(nicName, transferID);
                System.out.println("=>>> TRANSFER ENDED");
            }
            this.kill();
        }
    }

    public void kill(){
        this.run = false;
        this.timeoutSES.shutdownNow();

        for(FastUnicastSender fus : this.fastSenders)
            fus.kill();

        this.fastSenders.clear();

    }

    public void changeFastUnicastSendersDestIP(InetAddress newDestIP){
        FastUnicastSender fus;
        int numberOfFUS = this.fastSenders.size();

        for(int i = 0; i < numberOfFUS; i++){
            fus = this.fastSenders.get(i);
            fus.changeDestIP(newDestIP);
        }
    }

    public void changeOwnIP(ArrayList<InetAddress> addresses, boolean isLinkLocal){
        System.out.println("\t\t\tCHANGING IP!!");
        if(!addresses.contains(this.ownIP)){
            InetAddress newIP = null;

            for (InetAddress address : addresses)
                if (address.isLinkLocalAddress() == isLinkLocal) {
                    newIP = address;
                    break;
                }

            if(newIP != null) {

                this.ownIP = newIP;

/*                    if(this.unicastSocket != null) {
                    this.unicastSocket.close();
                    System.out.println("CLOSED UNICASTSOCKET!!");
                }

                this.unicastSocket = new DatagramSocket(null);
                InetSocketAddress isa = new InetSocketAddress(newIP, this.ownUnicastPort);
                this.unicastSocket.bind(isa); // EXCEPTION already bound!!
*/
                changeIP(this.ownIP);

                System.out.println("hasConnection? " + this.hasConnection);

                this.hasConnection = true;
                /*Thread t = new Thread(this);
                t.start();
                System.out.println("CHANGED IP AND CREATED NEW THREAD");*/

                System.out.println("GOT NEW IP =>" + this.ownIP + "\nhasConnection? " + this.hasConnection);


                for(FastUnicastSender fus : this.fastSenders)
                    fus.changeOwnIP(this.ownIP);

            }
            else {
                System.out.println("NEW IP SET BUT NO CORRESPONDING IP (hasConnection => FALSE)");
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

        this.hasConnection = value;

        for(FastUnicastSender fus : this.fastSenders)
            fus.changeHasConnection(value);
    }

    public void changeIP(InetAddress newIP){
        //enviar multiplos ipchanges ao receptor para que este saiba que mudei de IP
        IPChange ipc = new IPChange(this.transferID, newIP);
        byte[] serializedIPC = getBytesFromObject(ipc);

        DatagramPacket packet = new DatagramPacket(serializedIPC, serializedIPC.length, this.destIP, this.destUnicastPort);

        for (int i = 0; i < 5; i++) {
            try {
                this.sch.sendDP(this.nicName, this.transferID, this.ownIP, this.ownPort, packet);
                Thread.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

/*    public void run(){
        System.out.println("NEW (TRANSFERMULTISENDER)");

*//*        if(this.firstStart) {
            this.transferMetaInfoSES.scheduleWithFixedDelay(sendTransferMetaInfo, 0, 5, TimeUnit.SECONDS); PASSA A SER FEITO PELO SCHEDULER
            this.timeoutSES.scheduleWithFixedDelay(updateTimeoutStatus, 0, 2, TimeUnit.SECONDS);
            this.firstStart = false;
        }*//*

        byte[] buf = new byte[this.MTU];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);

        while(this.hasConnection && this.run){
            try {
                this.unicastSocket.receive(dp);

                System.out.println("RECEIVED SOMETHING FROM " + dp.getAddress());
                processDatagramPacket(dp, System.currentTimeMillis());

                buf = new byte[this.MTU];
                dp = new DatagramPacket(buf, buf.length);
            }
            catch (SocketException se){
                System.out.println("TransferMultiSender Socket Closed");
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.out.println("DIED hasConnection? " + this.hasConnection + " run? " + this.run);
    }*/

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

