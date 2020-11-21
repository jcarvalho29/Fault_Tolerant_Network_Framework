package Network;


import Data.Chunk;
import Data.ChunkManager;
import Messages.ChunkMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public class FastUnicastSender implements Runnable{

    private boolean run;
    private boolean hasConnection;

    private InetAddress destIP;
    private int destPort;
    private InetAddress ownIP;
    private int ownPort;

    private int dpPS;
    private int sleeptimeMilliSeconds;
    private int sleeptimeMicroSeconds;
    private int consecutiveSends;

    private ReentrantLock dpsLock;
    private DatagramSocket ds;
    private DatagramPacket packet;

    private ReentrantLock packetLock;

    private ChunkManager cm;
    private ArrayList<Chunk[]> chunkArrays;
    private int chunkPointer;

    private int ID;

    private boolean[] chunksIDToSend;
    private ArrayList<int[]> chunkIDsArray;
    private int[] preSelectedChunkIDs;
    private Chunk[] chunksToSend;


    private ReentrantLock chunkIDsLock;
    private ReentrantLock isRunning_lock;

    private boolean isRunning;

    public FastUnicastSender(int ID, InetAddress destIP, int destPort, InetAddress ownIP, ChunkManager cm, int[] chunkIDs, int dpPS){

        this.run = true;
        this.hasConnection = true;

        this.destIP = destIP;
        this.ownIP = ownIP;
        this.destPort = destPort;
        this.dpPS = dpPS;
        this.sleeptimeMilliSeconds = 1000 / this.dpPS;
        if(this.sleeptimeMilliSeconds < 10) {
            this.sleeptimeMicroSeconds = (1000000 / this.dpPS);
            this.consecutiveSends = (int) Math.ceil((double)10000 / this.sleeptimeMicroSeconds);
            this.sleeptimeMilliSeconds = (this.sleeptimeMicroSeconds * this.consecutiveSends) / 1000;
        }
        else{
            this.consecutiveSends = 1;
        }

        System.out.println("SleepTime " + this.sleeptimeMilliSeconds);
        System.out.println("Consecutive Sends " + this.consecutiveSends);
        this.dpsLock = new ReentrantLock();

        this.cm = cm;

        this.chunkArrays = new ArrayList<Chunk[]>();

        this.ID = ID;

        this.chunkIDsArray = new ArrayList<int[]>();
        this.chunkIDsArray.add(chunkIDs);
        this.preSelectedChunkIDs = null;
        this.chunksToSend = null;

        this.chunksIDToSend = new boolean[this.cm.mi.numberOfChunks];

        // Fill boolean[] with false
        this.chunksIDToSend[0] = false;

        for (int i = 1; i < this.chunksIDToSend.length; i += i) {
            System.arraycopy(this.chunksIDToSend, 0, this.chunksIDToSend, i, Math.min((this.chunksIDToSend.length - i), i));
        }

        for(int i = 0; i < chunkIDs.length; i++){
            this.chunksIDToSend[chunkIDs[i] - Integer.MIN_VALUE] = true;
        }

        this.chunkIDsLock = new ReentrantLock();
        this.isRunning_lock = new ReentrantLock();


        this.isRunning = false;

        this.packet = new DatagramPacket(new byte[1], 1, this.destIP, this.destPort);
        this.packetLock = new ReentrantLock();
        try {
            Random rand = new Random();
            this.ds = new DatagramSocket(null);
            this.ownPort = rand.nextInt(24999) + 35000;
            InetSocketAddress isa = new InetSocketAddress(this.ownIP, this.ownPort);
            this.ds.bind(isa);
            this.ds.setSendBufferSize(3000000);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public void changeDestIP(InetAddress newDestIP){
        System.out.println("NEW DEST IP | OLD => " + this.destIP + "vs NEW => " + newDestIP);

        this.destIP = newDestIP;

        this.packetLock.lock();

        this.packet.setAddress(this.destIP);

        this.packetLock.unlock();
    }

    public void changeOwnIP(InetAddress newOwnIP){
        System.out.println("\t\t\tCHANGE OWNIP CALLED IN FASTSENDER");
        boolean bound = false;

        this.ownIP = newOwnIP;

        this.packetLock.lock();

        this.ds.close();


        while (!bound) {
            try {
                this.ds = new DatagramSocket(null);
                InetSocketAddress isa = new InetSocketAddress(this.ownIP, this.ownPort);
                this.ds.bind(isa);
                bound = true;
                this.ds.setSendBufferSize(3000000);
                System.out.println("(FASTUNICASTSENDER) BOUND TO " + this.ownIP + ":" + this.ownPort);
                //changeHasConnection(true);
                this.packetLock.unlock();

            } catch (SocketException e) {
                System.out.println("(FASTUNICASTSENDER) ERROR BINDING TO " + this.ownIP + ":" + this.ownPort);
                e.printStackTrace();
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

    public void changeHasConnection(boolean value){
        if(!this.hasConnection && value && !this.isRunning) {
            new Thread(this).start();
        }
        this.hasConnection = value;
        //System.out.println("\t\t\tCHANGED FASTSENDER HASCONNECTION TO " + value);

    }

    public void run() {
        System.out.println("\tNEW FASTSENDER CREATED");
        this.isRunning_lock.lock();
        this.isRunning = true;
        this.isRunning_lock.unlock();

        Chunk[] chunks;
        long initialLoadStart = System.currentTimeMillis();

        this.dpsLock.lock();
        int chunksToLoad = this.consecutiveSends;
        this.dpsLock.unlock();

        this.chunkIDsLock.lock();

        /* System.out.println("\t( CHUNKARRAYS SIZE " + this.chunkArrays.size() + " )");
        System.out.println("\t( CHUNKIDSARRAY SIZE " + this.chunkIDsArray.size() + " )");
        System.out.println("\t( PRESELECTED NULL? " + (this.preSelectedChunkIDs == null) + " )");
        System.out.println("\t( CHUNKSTOSEND NULL? " + (this.chunksToSend == null) + " )");
*/
        System.out.println("CHUNKS ALREADY LOADED? ");
        if(this.chunksToSend == null) {
            System.out.println("NO");
            System.out.println("\tNEED TO LOAD CHUNKS?");
            if ((this.chunkArrays.size() == 0 && this.chunkIDsArray.size() > 0) || this.preSelectedChunkIDs != null) {
                System.out.println("\tYES");
                System.out.println("\t\tDO I HAVE PRESELECTED CHUNKS?");

                if (this.preSelectedChunkIDs == null) {
                    System.out.println("\t\tNO");
                    this.preSelectedChunkIDs = this.chunkIDsArray.get(0);
                    this.chunkIDsArray.remove(0);
                }
                else {
                    System.out.println("\t\tYES");
                }

                if (this.preSelectedChunkIDs.length < chunksToLoad) {
                    chunks = this.cm.getMissingChunks(this.preSelectedChunkIDs);
                    this.preSelectedChunkIDs = null;
                    System.out.println("PRESELECTED A NULL");
                }
                else {
                    chunks = this.cm.getMissingChunks(copyArrayListSection(this.preSelectedChunkIDs, chunksToLoad));
                    this.preSelectedChunkIDs = chopArray(this.preSelectedChunkIDs, chunksToLoad);
                }

                this.chunkArrays.add(chunks);
                this.chunkPointer = 0;
                chunks = null;
            }
            else
                System.out.println("\tNO");
        }
        else
            System.out.println("YES");
        this.chunkIDsLock.unlock();

        int accumulatedOverSleep = (int)(System.currentTimeMillis() - initialLoadStart);


        int cycleExecTime, sleeptimeMilliSeconds;
        long cycleStart = System.currentTimeMillis();

        Chunk chunk;

        this.isRunning_lock.lock();
        //System.out.println("\tDO I HAVE CHUNKS TO SEND?");
        while(this.run && this.hasConnection && (this.chunkArrays.size() > 0 || this.chunksToSend != null)) {
            //System.out.println("\tYES");
                this.isRunning_lock.unlock();
            if(this.chunksToSend == null) {
                    this.chunksToSend = this.chunkArrays.get(0); // !!! EXCEPTION chunk array vazio
                    this.chunkArrays.remove(0);
                }

            /*System.out.println("\t( CHUNKSTOSEND " + this.chunksToSend.length+ " )");
            System.out.println("\t( CHUNKPOINTER " + this.chunkPointer + " )");
            System.out.println("\t( HASCONNECTION " + this.hasConnection + " )");
            System.out.println("CONDITION " + (this.hasConnection && this.chunkPointer < this.chunksToSend.length));*/
                this.isRunning_lock.lock();
                while (this.run && this.hasConnection && this.chunkPointer < this.chunksToSend.length) {
                    this.isRunning_lock.unlock();
                    ChunkMessage cm;
                    byte[] serializedChunkHeader;

                    //Multiple Chunk Send PROVAVELMENTE TEM ERRO NA THREAD RESTART (PROVAVELMENTE LOCKS /Des    )
                    //System.out.println("\tConsecutive sends");
                    //System.out.print("\t\t");
                    for (int send = 0; this.run && this.hasConnection && send < this.consecutiveSends && this.chunkPointer < this.chunksToSend.length; send++) {
                        //System.out.print(send + " ");

                        //System.out.println("\t\tSTARTING TO LOAD CHUNK ( " + send + " )");
                        //Load chunk
                        chunk = this.chunksToSend[this.chunkPointer];
                        cm = new ChunkMessage(this.ID, chunk);
                        serializedChunkHeader = getBytesFromObject(cm);

                        //System.out.println("\t\tCHUNK LOADED ( " + send + " )");

                        //Send Chunk
                        try {
                            this.packetLock.lock();
                            //System.out.println("GOT PACKET LOCK ( " + send + " )");

                            this.packet.setData(serializedChunkHeader);
                            this.packet.setLength(serializedChunkHeader.length);

                            this.ds.send(this.packet);

                            this.chunksIDToSend[chunk.place - Integer.MIN_VALUE] = false;

                            this.chunksToSend[this.chunkPointer] = null;
                            this.chunkPointer++;

                        } catch (SocketException se) {
                            se.printStackTrace();
                            this.hasConnection = false;////!!!!!!!!!!????????
                            System.out.println("CHANGED hasConnection to false");
                        } catch (IOException e) {
                            //e.printStackTrace();
                            this.hasConnection = false;////!!!!!!!!!!????????
                            System.out.println("NO CONNECTION ( hasConnection " + this.hasConnection + " )");
                        }
                        finally {
                            this.packetLock.unlock();
                            //System.out.println("\t\t\t\tFINALLY FREED PACKET LOCK ( " + send + " )");
                        }
                    }

                    //Sleep
                    //System.out.println("\nSLEEP");
                    this.dpsLock.lock();
                    sleeptimeMilliSeconds = this.sleeptimeMilliSeconds;
                    this.dpsLock.unlock();
                    cycleExecTime = (int) (System.currentTimeMillis() - cycleStart);

                    if (cycleExecTime < sleeptimeMilliSeconds) {

                        sleeptimeMilliSeconds -= cycleExecTime;
                        if (accumulatedOverSleep < sleeptimeMilliSeconds) {

                            sleeptimeMilliSeconds -= accumulatedOverSleep;
                            accumulatedOverSleep = 0;
                            try {
                                Thread.sleep(sleeptimeMilliSeconds);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } else {
                            accumulatedOverSleep -= sleeptimeMilliSeconds;
                        }
                    } else {
                        accumulatedOverSleep += cycleExecTime - sleeptimeMilliSeconds;
                    }

                    cycleStart = System.currentTimeMillis();
                    this.isRunning_lock.lock();

                }
                this.isRunning_lock.unlock();

                //LOAD MULTIPLE CHUNKS
                if(this.chunkPointer == this.chunksToSend.length){
                    //System.out.println("\tAPAGUEI OS CHUNKS TO SEND");
                    this.chunksToSend = null;
                }

                if(this.run && this.hasConnection){

                    this.dpsLock.lock();
                    chunksToLoad = this.consecutiveSends; // número de chunks para carregar
                    this.dpsLock.unlock();

                    this.chunkIDsLock.lock();
                    if (chunkArrays.size() == 0 && (this.preSelectedChunkIDs != null || this.chunkIDsArray.size() > 0)) {
                        if (this.preSelectedChunkIDs == null) {
                            this.preSelectedChunkIDs = this.chunkIDsArray.get(0);
                            this.chunkIDsArray.remove(0);
                        }
                        if (this.preSelectedChunkIDs.length <= chunksToLoad) {
                            chunks = this.cm.getMissingChunks(this.preSelectedChunkIDs);
                            this.preSelectedChunkIDs = null;
                            //System.out.println("PRESELECTED A NULL MIDLOAD");
                        }
                        else {
                            chunks = this.cm.getMissingChunks(copyArrayListSection(this.preSelectedChunkIDs, chunksToLoad));
                            this.preSelectedChunkIDs = chopArray(this.preSelectedChunkIDs, chunksToLoad);
                        }

                        this.chunkArrays.add(chunks);
                        this.chunkPointer = 0;
                        chunks = null;
                    }
                    this.chunkIDsLock.unlock();

                }
                this.isRunning_lock.lock();
            }
        this.isRunning = false;
        this.isRunning_lock.unlock();
        System.out.println("FAST SENDER DEAD hasConnection? " + hasConnection + " run? " + run);

        System.out.println("\t( CHUNKARRAYS SIZE " + this.chunkArrays.size() + " )");
        System.out.println("\t( CHUNKIDSARRAY SIZE " + this.chunkIDsArray.size() + " )");
        System.out.println("\t( PRESELECTED NULL? " + (this.preSelectedChunkIDs == null) + " )");
        System.out.println("\t( CHUNKSTOSEND NULL? " + (this.chunksToSend == null) + " )");
    }

    public void kill(){
        System.out.println("KILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILLKILL");

        this.isRunning_lock.lock();
        this.run = false;
        this.ds.close();
        this.chunksToSend = null;
        this.preSelectedChunkIDs = null;
        this.chunkIDsArray.clear();
        this.chunkIDsArray = null;
        this.chunkArrays.clear();
        this.chunkArrays = null;
        this.chunksIDToSend = null;
        this.isRunning_lock.lock();
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

    public boolean addChunksToSend(int[] newChunksIDs) {
        boolean res;

        this.isRunning_lock.lock();
        res = this.isRunning;

        this.chunkIDsLock.lock();
        this.chunkIDsArray.add(newChunksIDs);
        for(int i = 0; i < newChunksIDs.length; i++){
            this.chunksIDToSend[newChunksIDs[i] - Integer.MIN_VALUE] = true;
        }
        this.chunkIDsLock.unlock();
        this.isRunning_lock.unlock();

        return res;
    }

    public boolean isRunning(){
        boolean res;
        this.isRunning_lock.lock();
        res = this.isRunning;
        this.isRunning_lock.unlock();

        return res;
    }

    public void changeDPS(int dps){
        this.dpsLock.lock();
        this.dpPS = dps;
        this.sleeptimeMilliSeconds = 1000 / this.dpPS;
        if(this.sleeptimeMilliSeconds < 10) {
            this.sleeptimeMicroSeconds = (1000000 / this.dpPS);
            this.consecutiveSends = (int) Math.ceil((double)10000 / this.sleeptimeMicroSeconds);
            this.sleeptimeMilliSeconds = (this.sleeptimeMicroSeconds * this.consecutiveSends) / 1000;
        }
        else{
            this.consecutiveSends = 1;
        }
        this.dpsLock.unlock();
    }

    private int[] copyArrayListSection(int[] original, int length){
        int[] res = new int[length];

        System.arraycopy(original, 0, res, 0, length);

        return res;
    }

    private int[] chopArray(int[] original, int length){
        int[] newOriginal = new int[original.length-length];
        System.arraycopy(original, length, newOriginal, 0, original.length-length);

        return newOriginal;
    }

    public boolean[] getChunksIDToSend(){
        this.packetLock.lock();
        boolean[] copy = new boolean[this.chunksIDToSend.length];

        System.arraycopy(this.chunksIDToSend, 0, copy, 0, this.chunksIDToSend.length);
        this.packetLock.unlock();

        return copy;
    }
}