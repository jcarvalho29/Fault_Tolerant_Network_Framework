package Network;

import Data.Chunk;
import Messages.ChunkHeader;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public class FastUnicastListener implements Runnable {

    private DatagramSocket ds;
    public int port;
    private int MTU;

    private ArrayList<Chunk> fc;
    private ArrayList<byte[]> bytes;

    private ReentrantLock lock;

    private boolean run = true;

    public FastUnicastListener(int MTU){
        this.port = -1;
        this.MTU = MTU;
        boolean b = true;

        this.lock = new ReentrantLock();
        this.fc = new ArrayList<Chunk>();
        this.bytes = new ArrayList<byte[]>();

        Random rand = new Random();
        while(b) {
            try {
                this.port = rand.nextInt(60000) + 5000;
                ds = new DatagramSocket(this.port);
                ds.setReceiveBufferSize(3000000);
                b = false;
            }
            catch (Exception e) {
                System.out.println("ESCOLHI UMA PORTA JÁ EM USO => " + this.port);
            }
        }
    }

    public void kill(){
        this.run = false;
        this.ds.close();
    }

    public void run() {

        try{
            byte[] buffer;
            DatagramPacket dp;
            while (this.run){
                buffer = new byte[this.MTU];
                dp = new DatagramPacket(buffer, this.MTU);

                this.ds.receive(dp);
                this.lock.lock();
                this.bytes.add(dp.getData());
                this.lock.unlock();
            }
        }
        catch (SocketException se){
            //System.out.println("\t=>FILERECEIVER DATAGRAMSOCKET CLOSED");
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public ArrayList<ChunkHeader> getChunkHeaders() {
        this.lock.lock();
        ArrayList<byte[]> b = new ArrayList<byte[]>(this.bytes);
        this.bytes.clear();
        this.lock.unlock();

        ArrayList<ChunkHeader> cPointer = new ArrayList<ChunkHeader>();

        Object o;
        for(byte[] dpBytes : b){
            o = getObjectFromBytes(dpBytes);
            if(o instanceof ChunkHeader){
                cPointer.add((ChunkHeader) o);
            }
        }

        return cPointer;
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