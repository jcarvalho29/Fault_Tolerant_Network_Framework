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

    private ReentrantLock chunkIDsLock;
    private ReentrantLock isRunning_lock;

    private boolean isRunning;

    public FastUnicastSender(int ID, InetAddress destIP, int destPort, InetAddress ownIP, ChunkManager cm, int[] chunkIDs, int dpPS){

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
            this.ownPort = rand.nextInt(20000) + 30000;
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
        System.out.println("NEW OWN IP | OLD => " + this.ownIP + "vs NEW => " + newOwnIP);
        try {

            this.ownIP = newOwnIP;

            this.packetLock.lock();

            this.ds.close();

            this.ds = new DatagramSocket(null);
            InetSocketAddress isa = new InetSocketAddress(this.ownIP, this.ownPort);
            this.ds.bind(isa);

            this.ds.setSendBufferSize(3000000);

            this.packetLock.unlock();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public void changeHasConnection(boolean value){
        this.hasConnection = value;
    }

    public void run() {
        this.isRunning_lock.lock();
        this.isRunning = true;
        this.isRunning_lock.unlock();

        Chunk[] chunks;
        int chunkArraysSize;
        int chunkIDsArraySize = this.chunkIDsArray.size();
        int[] chunkIDs = null;
        long initialLoadStart = System.currentTimeMillis();

        this.dpsLock.lock();
        int chunksToLoad = this.consecutiveSends;
        this.dpsLock.unlock();

        this.chunkIDsLock.lock();
        //Change amount of chunks to be loaded to consecutiveSends
        //take into consideration that there might be some loaded chunks
        if(this.chunkArrays.size() == 0 && chunkIDsArraySize > 0) {
            chunkIDs = this.chunkIDsArray.get(0);
            this.chunkIDsArray.remove(0);
            this.chunkPointer = 0;
            if (chunkIDs.length < chunksToLoad) {
                chunks = this.cm.getMissingChunks(chunkIDs);
                chunkIDs = null;
            }
            else {
                chunks = this.cm.getMissingChunks(copyArrayListSection(chunkIDs,chunksToLoad));
                chunkIDs = chopArray(chunkIDs, chunksToLoad);
            }

            this.chunkArrays.add(chunks);
            chunks = null;
        }
        this.chunkIDsLock.unlock();

        int accumulatedOverSleep = (int)(System.currentTimeMillis() - initialLoadStart);


        int cycleExecTime, sleeptimeMilliSeconds;
        long cycleStart = System.currentTimeMillis();
        Chunk[] chunksToSend;
        Chunk chunk;

        this.isRunning_lock.lock();
            while(this.hasConnection && this.chunkArrays.size() > 0){
                this.isRunning_lock.unlock();
                chunksToSend = this.chunkArrays.get(0); // !!! EXCEPTION chunk array vazio
                this.chunkArrays.remove(0);

                this.isRunning_lock.lock();
                while (this.hasConnection && this.chunkPointer < chunksToSend.length){
                    this.isRunning_lock.unlock();

                    ChunkMessage ch;
                    byte[] serializedChunkHeader;

                    //Multiple Chunk Send
                    for(int send = 0; this.hasConnection && send < this.consecutiveSends && this.chunkPointer < chunksToSend.length; send++) {


                        //Load chunk
                        chunk = chunksToSend[this.chunkPointer];
                        ch = new ChunkMessage(this.ID, chunk);
                        serializedChunkHeader = getBytesFromObject(ch);


                        //Send Chunk
                        try{
                            this.packetLock.lock();

                            this.packet.setData(serializedChunkHeader);
                            this.packet.setLength(serializedChunkHeader.length);

                            this.ds.send(this.packet);

                            this.chunksIDToSend[chunk.place - Integer.MIN_VALUE] = false;
                            this.packetLock.unlock();

                            chunksToSend[this.chunkPointer] = null;
                            this.chunkPointer++;

                        }
                        catch (SocketException se){
                            se.printStackTrace();
                            this.hasConnection = false;////!!!!!!!!!!????????
                            System.out.println("CHANGED hasConnection to false");
                        }
                        catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    //Sleep
                    this.dpsLock.lock();
                    sleeptimeMilliSeconds = this.sleeptimeMilliSeconds;
                    this.dpsLock.unlock();
                    cycleExecTime = (int) (System.currentTimeMillis() - cycleStart);

                    if(cycleExecTime < sleeptimeMilliSeconds) {

                        sleeptimeMilliSeconds -= cycleExecTime;
                        if(accumulatedOverSleep < sleeptimeMilliSeconds) {

                            sleeptimeMilliSeconds -= accumulatedOverSleep;
                            accumulatedOverSleep = 0;
                            try {
                                Thread.sleep(sleeptimeMilliSeconds);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        else{
                            accumulatedOverSleep -= sleeptimeMilliSeconds;
                        }
                    }
                    else {
                        accumulatedOverSleep += cycleExecTime - sleeptimeMilliSeconds;
                    }

                    cycleStart = System.currentTimeMillis();
                    this.isRunning_lock.lock();
                }
                this.isRunning_lock.unlock();

                chunkArraysSize = chunkArrays.size();

                this.dpsLock.lock();
                chunksToLoad = this.consecutiveSends;
                this.dpsLock.unlock();

                this.chunkIDsLock.lock();
                if(chunkArraysSize == 0 && (chunkIDs != null || this.chunkIDsArray.size() > 0)) {
                    if(chunkIDs == null){
                        chunkIDs = this.chunkIDsArray.get(0);
                        this.chunkIDsArray.remove(0);
                    }
                    if (chunkIDs.length < chunksToLoad) {
                        chunks = this.cm.getMissingChunks(chunkIDs);
                        chunkIDs = null;
                    }
                    else {
                        chunks = this.cm.getMissingChunks(copyArrayListSection(chunkIDs,chunksToLoad));
                        chunkIDs = chopArray(chunkIDs, chunksToLoad);
                    }

                    this.chunkArrays.add(chunks);
                    this.chunkPointer = 0;
                    chunks = null;
                }
                this.chunkIDsLock.unlock();

                this.isRunning_lock.lock();
            }
        this.isRunning = false;
        this.isRunning_lock.unlock();
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