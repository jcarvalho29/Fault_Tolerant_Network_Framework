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
    private int sleeptimeMS;
    private int sleeptimeNS;
    private ReentrantLock dpsLock;
    private DatagramSocket ds;

    private ChunkManager cm;
    private ArrayList<Chunk[]> chunkArrays;

    private int ID;

    private ArrayList<Integer> chunkIDs;

    private ReentrantLock chunkIDsLock;
    private ReentrantLock chunkArraysLock;
    private ReentrantLock isRunning_lock;

    private boolean isRunning;

    public FastUnicastSender(int ID, InetAddress IP, int destPort, ChunkManager cm, ArrayList<Integer> chunkIDs, int dpPS){

        this.IP = IP;
        this.destPort = destPort;
        this.dpPS = dpPS;
        this.sleeptimeMS = 1000 / this.dpPS;
        this.sleeptimeNS = (1000000000/this.dpPS) % 1000000;
        this.dpsLock = new ReentrantLock();

        this.cm = cm;

        this.chunkArrays = new ArrayList<Chunk[]>();

        this.ID = ID;

        this.chunkIDs = chunkIDs;

        this.chunkIDsLock = new ReentrantLock();
        //this.chunkArraysLock = new ReentrantLock();
        this.isRunning_lock = new ReentrantLock();

        //chunkLoader();

        this.isRunning = false;

        try {
            this.ds = new DatagramSocket();
            this.ds.setSendBufferSize(3000000);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    /*private void chunkLoader(){

        new Thread(() ->{
            Chunk[] chunks;
            this.chunkIDsLock.lock();
            while(this.chunkIDs.size() > 0){
                if (this.chunkIDs.size() < 200) {
                    chunks = this.cm.getMissingChunks(this.chunkIDs);
                    this.chunkIDs.clear();
                }
                else {
                    chunks = this.cm.getMissingChunks(copyArrayListSection(this.chunkIDs, 0, 200));
                    this.chunkIDs = copyArrayListSection(this.chunkIDs, 200, this.chunkIDs.size());
                }
                this.chunkIDsLock.unlock();

                this.chunkArraysLock.lock();
                this.chunkArrays.add(chunks);
                this.chunkArraysLock.unlock();

                this.chunkIDsLock.lock();
            }
            this.chunkIDsLock.unlock();
        }).start();
    }*/

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
        //this.chunkArraysLock.lock();
        int chunkArraysSize = chunkArrays.size();
        //this.chunkArraysLock.unlock();

        this.chunkIDsLock.lock();
        if(chunkArraysSize == 0 && this.chunkIDs.size() > 0) {
            //start = System.currentTimeMillis();
            if (this.chunkIDs.size() < 200) {
                chunks = this.cm.getMissingChunks(this.chunkIDs);
                this.chunkIDs.clear();
            } else {
                chunks = this.cm.getMissingChunks(copyArrayListSection(this.chunkIDs, 0, 200));
                this.chunkIDs = copyArrayListSection(this.chunkIDs, 200, this.chunkIDs.size());
            }

            //this.chunkArraysLock.lock();
            this.chunkArrays.add(chunks);
            //this.chunkArraysLock.unlock();
            chunks = null;
            //end = System.currentTimeMillis();
            //System.out.println("        WASTED " + (end - start) + " ms LOADING INITIAL CHUNKS");
        }
        this.chunkIDsLock.unlock();



        int cycleExecTime, sleepTimeMS;
        long cycleStart = System.currentTimeMillis(), cycleEnd, sleepTimeNS, start;
        Chunk[] chunksToSend;
        int pointer;

        this.isRunning_lock.lock();
        //this.chunkArraysLock.lock();
            while(this.chunkArrays.size() > 0){
                this.isRunning_lock.unlock();
                chunksToSend = this.chunkArrays.get(0);
                this.chunkArrays.remove(0);
                //this.chunkArraysLock.unlock();
                pointer = 0;

                this.isRunning_lock.lock();
                while (pointer < chunksToSend.length){
                    this.isRunning_lock.unlock();

                        try {
                            sendFileChunk(chunksToSend[pointer]);
                            chunksToSend[pointer] = null;
                            pointer++;

                            //CODE THAT DETERMINES THE AMOUNT OF TIME TO SLEEP
                            this.dpsLock.lock();
                            if(this.sleeptimeMS != 0) {
                                this.dpsLock.unlock();
                                cycleEnd = System.currentTimeMillis();
                                cycleExecTime = (int) (cycleEnd - cycleStart);

                                this.dpsLock.lock();
                                sleepTimeMS = this.sleeptimeMS;
                                this.dpsLock.unlock();
                                if (cycleExecTime < sleepTimeMS) {
                                    Thread.sleep(sleepTimeMS - cycleExecTime);
                                }

                                cycleStart = System.currentTimeMillis();
                            }
                            else{
                                sleepTimeNS = this.sleeptimeNS;
                                this.dpsLock.unlock();
                                start = System.nanoTime();
                                while(System.nanoTime() - start < sleepTimeNS);
                            }

                        }
                        catch (Exception e){
                            e.printStackTrace();
                        }

                    this.isRunning_lock.lock();
                }
                this.isRunning_lock.unlock();

                //this.chunkArraysLock.lock();
                chunkArraysSize = chunkArrays.size();
                //this.chunkArraysLock.unlock();

                this.chunkIDsLock.lock();
                if(chunkArraysSize == 0 && this.chunkIDs.size() > 0) {
                    //start = System.currentTimeMillis();
                    if (this.chunkIDs.size() < 200) {
                        chunks = this.cm.getMissingChunks(this.chunkIDs);
                        this.chunkIDs.clear();
                    } else {
                        chunks = this.cm.getMissingChunks(copyArrayListSection(this.chunkIDs, 0, 200));
                        this.chunkIDs = copyArrayListSection(this.chunkIDs, 200, this.chunkIDs.size());
                    }

                    //this.chunkArraysLock.lock();
                    this.chunkArrays.add(chunks);
                    //this.chunkArraysLock.unlock();
                    chunks = null;
                    //end = System.currentTimeMillis();
                    //System.out.println("        WASTED " + (end - start) + " ms LOADING INITIAL CHUNKS");
                }
                this.chunkIDsLock.unlock();

                //this.chunkArraysLock.lock();
                this.isRunning_lock.lock();
            }
        this.isRunning = false;
        this.isRunning_lock.unlock();
        //this.chunkArraysLock.unlock();
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
        this.sleeptimeMS = 1000 / dps;
        if(this.sleeptimeMS == 0)
            this.sleeptimeNS = (1000000000/dps) % 1000000;
        this.dpsLock.unlock();
    }

    private ArrayList<Integer> copyArrayListSection(ArrayList<Integer> original, int start, int end){
        ArrayList<Integer> res = new ArrayList<>();

        for(int i = start; i < end; i++)
            res.add(original.get(i));

        return res;
    }
}