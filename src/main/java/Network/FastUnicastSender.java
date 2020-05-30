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
import java.util.Date;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public class FastUnicastSender implements Runnable{


    private InetAddress IP;
    private int destPort;
    private int dpPS;
    private ReentrantLock dpsLock;
    private DatagramSocket ds;

    private ChunkManager cm;
    private ArrayList<Chunk> cToSend;

    private int ID;

    private ArrayList<Integer> chunkIDs;

    private ReentrantLock ChunkArrays_lock;
    private ReentrantLock isRunning_lock;

    private boolean isRunning;

    public FastUnicastSender(int ID, InetAddress IP, int destPort, ChunkManager cm, ArrayList<Integer> chunkIDs, int dpPS){

        this.IP = IP;
        this.destPort = destPort;
        this.dpPS = dpPS;
        this.dpsLock = new ReentrantLock();

        this.cm = cm;
        this.cToSend = new ArrayList<Chunk>();

        this.ID = ID;

        this.chunkIDs = chunkIDs;

        this.ChunkArrays_lock = new ReentrantLock();
        this.isRunning_lock = new ReentrantLock();

        this.isRunning = false;

        try {
            this.ds = new DatagramSocket();
            int oldSpace = this.ds.getSendBufferSize();
            this.ds.setSendBufferSize(3000000);
            System.out.println("HAD THIS SPACE => " + oldSpace + "\nGOT THIS SPACE => " + this.ds.getSendBufferSize());
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
        //System.out.println("    FAST SENDER IS RUNNING");
        this.isRunning_lock.lock();
        this.isRunning = true;
        this.isRunning_lock.unlock();

        ArrayList<Chunk> tmp = new ArrayList<>();
        this.ChunkArrays_lock.lock();

        if(this.chunkIDs.size() > 0 && this.chunkIDs.size() < 200) {
            //System.out.println(" INICIAL LOAD <200\n" + "    GETTING FROM " + chunkIDs.get(0) + " TO " + chunkIDs.get(chunkIDs.size()-1));
            this.cToSend = this.cm.getMissingChunks(this.chunkIDs);
            this.chunkIDs.clear();
        }
        else {
            if (this.chunkIDs.size() > 200) {
                //System.out.println(" INICIAL LOAD >200\n" + "    GETTING FROM " + chunkIDs.get(0) + " TO " + chunkIDs.get(199));
                this.cToSend = this.cm.getMissingChunks(copyArrayListSection(this.chunkIDs, 0, 200));
                this.chunkIDs = copyArrayListSection(this.chunkIDs, 200, this.chunkIDs.size());
            }
            //else
                //System.out.println("ERROR EMPTY CHUNKID " + this.destPort);
        }
        this.ChunkArrays_lock.unlock();

        int cycleExecTime;
        Long cycleStart = System.currentTimeMillis(), cycleEnd;
        int burst = 0;
        Random rand = new Random();
        this.isRunning_lock.lock();

        while (this.cToSend.size() > 0 || chunkIDs.size() > 0){
            this.isRunning_lock.unlock();
            try {
                if(this.cToSend.size() > 0){

                    sendFileChunk(this.cToSend.get(0));

                    cycleEnd = System.currentTimeMillis();
                    cycleExecTime = (int) (cycleEnd - cycleStart);

                    this.dpsLock.lock();
                    int dps = this.dpPS;
                    this.dpsLock.unlock();
                    //burst++;
                    if (cycleExecTime < 1000 / dps) {
                        //System.out.println("SLEEP FOR " + ((1000 / this.dpPS) - cycleExecTime) + " (" + cycleExecTime + ")" );
                        //Para fazer um efeito de Burst
                        /*if(burst == dps){
                            Thread.sleep(rand.nextInt(200)+100);
                            burst = 0;
                        }
                        else*/
                            Thread.sleep((1000 / dps) - cycleExecTime);
                    }

                    cycleStart = System.currentTimeMillis();
                    this.cToSend.remove(0);
                }

                if (this.cToSend.size() == 0) {
                    this.ChunkArrays_lock.lock();
                    if(chunkIDs.size() > 200) {
                        //System.out.println(" MID LOAD >200\n" + "    GETTING FROM " + chunkIDs.get(0) + " TO " + chunkIDs.get(199));
                        tmp = this.cm.getMissingChunks(copyArrayListSection(this.chunkIDs, 0, 200));
                        this.chunkIDs = copyArrayListSection(this.chunkIDs, 200, this.chunkIDs.size());
                    }
                    else {
                        if(chunkIDs.size() > 0) {
                            //System.out.println(" MID LOAD <200\n" + "    GETTING FROM " + chunkIDs.get(0) + " TO " + chunkIDs.get(chunkIDs.size()-1));
                            tmp = this.cm.getMissingChunks(chunkIDs);
                            this.chunkIDs.clear();
                        }
                    }

                    this.ChunkArrays_lock.unlock();

                    if(tmp.size() > 0) {
                        this.cToSend.addAll(tmp);
                        tmp.clear();
                    }
                }
            }
            catch (Exception e){
                e.printStackTrace();
            }
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
        boolean res = false;
        this.isRunning_lock.lock();
        if(this.isRunning) {
            res = true;
        }
        this.ChunkArrays_lock.lock();
        //remove repetidos
        for(int id : newChunksIDs){
            if(!this.chunkIDs.contains(id))
                this.chunkIDs.add(id);
        }
        this.ChunkArrays_lock.unlock();
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
        this.dpsLock.unlock();
    }

    private ArrayList<Integer> copyArrayListSection(ArrayList<Integer> original, int start, int end){
        ArrayList<Integer> res = new ArrayList<>();

        for(int i = start; i < end; i++)
            res.add(original.get(i));

        return res;
    }
}