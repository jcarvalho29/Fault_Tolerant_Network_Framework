package Network;


import Data.Chunk;
import Data.ChunkManager;
import Messages.ChunkHeader;

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

    private ChunkManager cm;
    private ArrayList<Chunk> cToSend;

    private int ID;
    private String hash;

    private int startID;
    private int len;
    private int pPT;

    private ArrayList<Integer> chunkIDs;

    private DatagramSocket ds;

    private ReentrantLock lock;

    public FastUnicastSender(int ID, InetAddress IP, int destPort, ChunkManager cm, ArrayList<Integer> chunkIDs, int dpPS){

        this.IP = IP;
        this.destPort = destPort;
        this.dpPS = dpPS;

        this.cm = cm;
        this.cToSend = new ArrayList<Chunk>();

        this.ID = ID;

        this.startID = startID;
        this.len = len;
        this.pPT = pPT;
        this.chunkIDs = chunkIDs;

        this.lock = new ReentrantLock();

        try {
            this.ds = new DatagramSocket();
            this.ds.setSendBufferSize(3000000);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private void sendFileChunk(Chunk chunk) {
        ChunkHeader ch = new ChunkHeader(this.ID, chunk);

        byte[] serializedChunkHeader = getBytesFromObject(ch);

        try{
            DatagramPacket packet = new DatagramPacket(serializedChunkHeader, serializedChunkHeader.length, this.IP, this.destPort);

            this.ds.send(packet);
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
        boolean flag = true;
        int counter = 0;
        int read = 0;

        ArrayList<Chunk> tmp;
        this.lock.lock();
        if(this.chunkIDs.size() < 200)
            this.cToSend = this.cm.getMissingChunks(this.chunkIDs);
        else {
            this.cToSend = this.cm.getMissingChunks((ArrayList<Integer>) this.chunkIDs.subList(0, 200));
            this.chunkIDs = (ArrayList<Integer>) this.chunkIDs.subList(200, this.chunkIDs.size());
        }
        this.lock.unlock();

        int tam = cToSend.size();

        while (this.cToSend.size() > 0){
            try {
                sendFileChunk(this.cToSend.get(0));
                counter++;
                Thread.sleep(1000 / this.dpPS);

                this.cToSend.remove(0);

                if (this.cToSend.size() == 0) {
                    this.lock.lock();
                    tmp = this.cm.getMissingChunks((ArrayList<Integer>) this.chunkIDs.subList(0, 200));
                    this.chunkIDs = (ArrayList<Integer>) this.chunkIDs.subList(200, this.chunkIDs.size());
                    this.lock.unlock();

                    this.cToSend.addAll(tmp);
                    tmp = null;
                }
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }
        //System.out.println("Enviei " + counter + " FILECHUNKS");
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

    public void addChunksToSend(ArrayList<Integer> newChunksIDs) {
        this.lock.lock();
        this.chunkIDs.addAll(newChunksIDs);
        this.lock.unlock();
    }
}