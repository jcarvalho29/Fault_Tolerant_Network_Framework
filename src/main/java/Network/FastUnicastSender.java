package Network;


import Data.Chunk;
import Data.ChunkManager;
import Messages.ChunkMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class FastUnicastSender implements Runnable{


    private InetAddress IP;
    private int destPort;

    private int dpPS;
    private int sleeptimeMilliSeconds;
    private int sleeptimeMicroSeconds;
    private int consecutiveSends;

    private ReentrantLock dpsLock;
    private DatagramSocket ds;

    private ChunkManager cm;
    private ArrayList<Chunk[]> chunkArrays;

    private int ID;

    private ArrayList<Integer> chunkIDs;

    private ReentrantLock chunkIDsLock;
    private ReentrantLock isRunning_lock;

    private boolean isRunning;

    public FastUnicastSender(int ID, InetAddress IP, int destPort, ChunkManager cm, ArrayList<Integer> chunkIDs, int dpPS){

        this.IP = IP;
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

        this.chunkIDs = chunkIDs;

        this.chunkIDsLock = new ReentrantLock();
        this.isRunning_lock = new ReentrantLock();


        this.isRunning = false;

        try {
            this.ds = new DatagramSocket();
            this.ds.setSendBufferSize(3000000);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private void sendFileChunk(Chunk chunk) {
        ChunkMessage ch = new ChunkMessage(this.ID, chunk);

        byte[] serializedChunkHeader = getBytesFromObject(ch);

        try{
            DatagramPacket packet = new DatagramPacket(serializedChunkHeader, serializedChunkHeader.length, this.IP, this.destPort);

            this.ds.send(packet);
            //System.out.println("SENT CHUNK " + this.IP + " " + this.destPort);
        }
        catch (IOException e) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                //ex.printStackTrace();
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public void run() {
        this.isRunning_lock.lock();
        this.isRunning = true;
        this.isRunning_lock.unlock();

        Chunk[] chunks;
        int chunkArraysSize = chunkArrays.size();

        long initialLoadStart = System.currentTimeMillis();

        this.chunkIDsLock.lock();
        if(chunkArraysSize == 0 && this.chunkIDs.size() > 0) {
            if (this.chunkIDs.size() < 200) {
                chunks = this.cm.getMissingChunks(this.chunkIDs);
                this.chunkIDs.clear();
            } else {
                chunks = this.cm.getMissingChunks(copyArrayListSection(this.chunkIDs, 0, 200));
                this.chunkIDs = copyArrayListSection(this.chunkIDs, 200, this.chunkIDs.size());
            }

            this.chunkArrays.add(chunks);
            chunks = null;
        }
        this.chunkIDsLock.unlock();

        int
                accumulatedOverSleep = (int)(System.currentTimeMillis() - initialLoadStart);


        int cycleExecTime, sleeptimeMilliSeconds;
        long cycleStart = System.currentTimeMillis();
        Chunk[] chunksToSend;
        int pointer;

        this.isRunning_lock.lock();
            while(this.chunkArrays.size() > 0){
                this.isRunning_lock.unlock();
                chunksToSend = this.chunkArrays.get(0); // !!! EXCEPTION chunk array vazio
                this.chunkArrays.remove(0);
                pointer = 0;

                this.isRunning_lock.lock();
                while (pointer < chunksToSend.length){
                    this.isRunning_lock.unlock();

                    //Multiple Send
                    for(int send = 0; send < this.consecutiveSends && pointer < chunksToSend.length; send++) {
                        sendFileChunk(chunksToSend[pointer]);
                        chunksToSend[pointer] = null;
                        pointer++;
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

                this.chunkIDsLock.lock();
                if(chunkArraysSize == 0 && this.chunkIDs.size() > 0) {
                    if (this.chunkIDs.size() < 200) {
                        chunks = this.cm.getMissingChunks(this.chunkIDs);
                        this.chunkIDs.clear();
                    } else {
                        chunks = this.cm.getMissingChunks(copyArrayListSection(this.chunkIDs, 0, 200));
                        this.chunkIDs = copyArrayListSection(this.chunkIDs, 200, this.chunkIDs.size());
                    }

                    this.chunkArrays.add(chunks);
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
        byte[] data = bos.toByteArray();

        return data;
    }

    public boolean addChunksToSend(ArrayList<Integer> newChunksIDs) {
        boolean res;
        this.isRunning_lock.lock();
        res = this.isRunning;

        this.chunkIDsLock.lock();
        //remove repetidos
        for(int id : newChunksIDs){
            if(!this.chunkIDs.contains(id))
                this.chunkIDs.add(id);
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

    private ArrayList<Integer> copyArrayListSection(ArrayList<Integer> original, int start, int end){
        ArrayList<Integer> res = new ArrayList<>();

        for(int i = start; i < end; i++)
            res.add(original.get(i));

        return res;
    }
}