package Network;

import Messages.ChunkMessage;

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
    private int dps;

    private ArrayList<byte[][]> Overflow;
    private byte[][] byteArrays;
    private int byteArraysSize;
    private int pointer;
    private int receivedCM;
    private ReentrantLock arrayLock;

    private boolean run;

    private long firstReceivedCM;
    private boolean frcmFlag;

    public FastUnicastListener(int MTU, int dps){

        this.run = true;

        this.firstReceivedCM = Long.MAX_VALUE;
        this.frcmFlag = false;

        this.port = -1;
        this.MTU = MTU;
        this.dps = dps;

        this.arrayLock = new ReentrantLock();

        this.Overflow = new ArrayList<byte[][]>();
        //0.55 = 1.1 (10% maior) * 0.5 (primeiro sleep é de 0.5 segundos)
        this.byteArraysSize = (int) (this.dps* 0.55);
        this.byteArrays = new byte[this.byteArraysSize][];
        this.pointer = 0;
        this.receivedCM = 0;

        Random rand = new Random();
        boolean b = true;
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
        //System.out.println("FAST LISTENER KILLED");
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
                if(!this.frcmFlag){
                    this.frcmFlag = true;
                    this.firstReceivedCM = System.currentTimeMillis();
                }

                this.arrayLock.lock();
                if(this.pointer == this.byteArraysSize){

                    byte[][] copy = new byte[this.byteArraysSize][];
                    System.arraycopy(this.byteArrays, 0, copy, 0, this.byteArraysSize);
                    this.Overflow.add(copy);
                    copy = null;
/*
                    this.byteArrays[0] = null;
                    for (int i = 1; i < this.byteArraysSize; i += i)
                        System.arraycopy(this.byteArrays, 0, this.byteArrays, i, Math.min((this.byteArraysSize - i), i));*/

                    this.pointer = 0;
                }
                this.byteArrays[this.pointer] = dp.getData();
                this.pointer++;
                this.receivedCM++;

                this.arrayLock.unlock();

            }
        }
        catch (SocketException se){
            //System.out.println("\t=>FILERECEIVER DATAGRAMSOCKET CLOSED");
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public ChunkMessage[] getChunkHeaders() {
        ArrayList<byte[][]> overflowCopy = null;
        int pointerCopy = 0;
        int receivedCMCopy = 0;

        this.arrayLock.lock();
        receivedCMCopy = this.receivedCM;
        this.receivedCM = 0;

        byte[][] objects = new byte[receivedCMCopy][];
        int cmInObjectsArrayCopy = 0;

        if(this.Overflow.size() > 0) {
            overflowCopy = new ArrayList<byte[][]>(this.Overflow);
            this.Overflow.clear();
        }

        if(pointer != 0) {
            pointerCopy = this.pointer;
            System.arraycopy(this.byteArrays, 0, objects, cmInObjectsArrayCopy, this.pointer);
            cmInObjectsArrayCopy += this.pointer;
            this.pointer = 0;

        }
        this.arrayLock.unlock();

        if(overflowCopy != null) {
            for (byte[][] b : overflowCopy) {
                System.arraycopy(b, 0, objects, cmInObjectsArrayCopy, b.length);
                cmInObjectsArrayCopy += b.length;
            }
        }

        ChunkMessage[] cms = new ChunkMessage[objects.length];

        for(int i = 0; i < objects.length; i++){
            cms[i] = (ChunkMessage) getObjectFromBytes(objects[i]);
        }

        return cms;

        /*Object o;
        if(overflowCopy != null) {
            if(pointerCopy != 0)
                overflowCopy.add(byteArraysCopy);
            for (byte[][] byteArrays : overflowCopy)
                for(int i = 0; i < pointerCopy; i++){
                    o = getObjectFromBytes(byteArrays[i]);
                    if(o instanceof ChunkMessage)
                        cPointer.add((ChunkMessage) o);
                }
        }
        else{
            for (int i = 0; i < pointerCopy; i++) {
                o = getObjectFromBytes(byteArraysCopy[i]);
                if (o instanceof ChunkMessage)
                    cPointer.add((ChunkMessage) o);
            }
        }

        //System.out.println("GOT " + cPointer.size() + " CHUNKS");
        return cPointer;*/
    }

    public void changeByteArraysSize(int rtt, int dps){
        //500 = 2/1000
        int newByteArraysSize = ((dps * rtt)/500); // EXCEPTION!!!!!!!! NUMERO NEGATIVO
        if(newByteArraysSize > 0) {
            byte[][] tmp = new byte[newByteArraysSize][];
            this.arrayLock.lock();

            if (this.pointer != 0) {
                byte[][] byteArraysCopy = new byte[this.pointer][];

                System.arraycopy(this.byteArrays, 0, byteArraysCopy, 0, this.pointer);

                this.byteArrays = tmp;
                this.byteArraysSize = newByteArraysSize;
                this.pointer = 0;

                this.Overflow.add(byteArraysCopy);
            }
            this.arrayLock.unlock();
        }
        else
            System.out.println("                CHANGEBYTEARRAYSSIZE NEGATIVO DPS " + dps + " RTT" + rtt);
    }

    public long getFirstCMReceivedTimestamp(){
        if(this.frcmFlag) {
            frcmFlag = false;
            return this.firstReceivedCM;
        }
        else
            return Long.MAX_VALUE;
    }

    public void resetFirstCMReceivedTimestamp(){
        this.frcmFlag = false;
        this.firstReceivedCM = Long.MAX_VALUE;
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
