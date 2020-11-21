package Network;

import Messages.ChunkMessage;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public class FastUnicastListener implements Runnable {

    private DatagramSocket ds;
    private InetAddress ip;
    public int port;
    private int MTU;
    private int dps;

    //private ArrayList<DatagramPacket[]> Overflow;
    private ArrayList<byte[][]> Overflow;
    //private DatagramPacket[] datagramPacketsArray;
    private byte[][] datagramPacketsArray;
    private int datagramPacketsArraySize;
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

        //this.Overflow = new ArrayList<DatagramPacket[]>();
        this.Overflow = new ArrayList<byte[][]>();
        //0.55 = 1.1 (10% maior) * 0.5 (primeiro sleep é de 0.5 segundos)
        this.datagramPacketsArraySize = (int) (this.dps* 0.55);
        //this.datagramPacketsArray = new DatagramPacket[this.datagramPacketsArraySize];
        this.datagramPacketsArray = new byte[this.datagramPacketsArraySize][];
        this.pointer = 0;
        this.receivedCM = 0;

        Random rand = new Random();
        boolean b = true;
        while(b) {
            try {
                this.port = rand.nextInt(24999) + 10000;
                this.ds = new DatagramSocket(null);
                InetSocketAddress isa = new InetSocketAddress(this.ip, this.port);
                this.ds.bind(isa);
                this.ds.setReceiveBufferSize(3000000);
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

    public void changeIP(InetAddress newIP){
        if(newIP != null) {
            boolean bound = false;
            while(!bound) {
                try {
                    this.arrayLock.lock();

                    this.ds.close();
                    this.ds = new DatagramSocket(null);
                    InetSocketAddress isa = new InetSocketAddress(newIP, this.port);
                    this.ds.bind(isa); // !!!! ADDRESS ALREADY IN USE
                    System.out.println("(FASTUNICASTLISTENER) BOUND TO " + this.ip + ":" + this.port);
                    this.ds.setReceiveBufferSize(3000000);

                    this.ip = newIP;
                    bound = true;
                    this.arrayLock.unlock();
                } catch (SocketException e) {
                    //e.printStackTrace();
                    System.out.println("(FASTUNICASTLISTENER) ERROR BINDING TO " + this.ip + ":" + this.port);
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
    }

    public void run() {

        byte[] buffer = new byte[this.MTU];
        DatagramPacket dp = new DatagramPacket(buffer, this.MTU);
        while (this.run){
            try{
                buffer = new byte[this.MTU];
                //dp = new DatagramPacket(buffer, this.MTU);
                dp.setData(buffer);

                this.ds.receive(dp);

                if(!this.frcmFlag){
                    this.frcmFlag = true;
                    this.firstReceivedCM = System.currentTimeMillis();
                }

                this.arrayLock.lock();
                if(this.pointer == this.datagramPacketsArraySize){

                    /*DatagramPacket[] copy = new DatagramPacket[this.datagramPacketsArraySize];
                    System.arraycopy(this.datagramPacketsArray, 0, copy, 0, this.datagramPacketsArraySize);
                    this.Overflow.add(copy);
                    copy = null;*/


                    byte[][] copy = new byte[this.datagramPacketsArraySize][];
                    System.arraycopy(this.datagramPacketsArray, 0, copy, 0, this.datagramPacketsArraySize);
                    this.Overflow.add(copy);
                    copy = null;

                    this.pointer = 0;
                }

                //this.datagramPacketsArray[this.pointer] = dp;
                this.datagramPacketsArray[this.pointer] = dp.getData();
                this.pointer++;
                this.receivedCM++;

                this.arrayLock.unlock();
            }
            catch (SocketException se){
                System.out.println("                =>FUL EXCEPTION                         SOCKET CLOSED");
                this.arrayLock.lock();
                this.arrayLock.unlock();
                System.out.println("unlock arrayLock");
            }
            catch (Exception e){
                e.printStackTrace();
                this.arrayLock.lock();
                this.arrayLock.unlock();
                System.out.println("unlock arrayLock");
            }
        }
        System.out.println("FASTLISTENER DIED");

    }

    public ChunkMessage[] getChunkMessages() {
        //ArrayList<DatagramPacket[]> overflowCopy = null;
        ArrayList<byte[][]> overflowCopy = null;
        int receivedCMCopy;

        this.arrayLock.lock();
        receivedCMCopy = this.receivedCM;
        this.receivedCM = 0;

        //DatagramPacket[] objects = new DatagramPacket[receivedCMCopy];
        byte[][] objects = new byte[receivedCMCopy][];
        int cmInObjectsArrayCopy = 0;

        if(this.Overflow.size() > 0) {
            //overflowCopy = new ArrayList<DatagramPacket[]>(this.Overflow);
            overflowCopy = new ArrayList<byte[][]>(this.Overflow);
            this.Overflow.clear();
        }

        if(pointer != 0) {
            System.arraycopy(this.datagramPacketsArray, 0, objects, cmInObjectsArrayCopy, this.pointer);
            cmInObjectsArrayCopy += this.pointer;
            this.pointer = 0;

        }
        this.arrayLock.unlock();


        if(overflowCopy != null) {
            //for (DatagramPacket[] b : overflowCopy) {
            for (byte[][] b : overflowCopy) {
                System.arraycopy(b, 0, objects, cmInObjectsArrayCopy, b.length);
                cmInObjectsArrayCopy += b.length;
            }
        }

        ChunkMessage[] cms = new ChunkMessage[objects.length];

        for(int i = 0; i < objects.length; i++){
            //cms[i] = (ChunkMessage) getObjectFromBytes(objects[i].getData());
            cms[i] = (ChunkMessage) getObjectFromBytes(objects[i]);
        }
        return cms;
    }

    public void changeDatagramPacketsArraySize(int rtt, int dps){
        //500 = 2/1000
        rtt = Math.min(rtt, 5000);
        int newDatagramPacketsArraySize = ((dps * rtt) / 500); // EXCEPTION!!!!!!!
        byte[][] tmp = null;
        try {
            tmp = new byte[newDatagramPacketsArraySize][];
        }
        catch (Exception e){
            e.printStackTrace();
            System.out.println("(DPS * RTT) / 500 = " + dps + " * " + rtt + " / 500 = " + newDatagramPacketsArraySize);
        }
        this.arrayLock.lock();

        if (this.pointer != 0) {
            //DatagramPacket[] datagramPacketsArrayCopy = new DatagramPacket[this.pointer];
            byte[][] datagramPacketsArrayCopy = new byte[this.pointer][];

            System.arraycopy(this.datagramPacketsArray, 0, datagramPacketsArrayCopy, 0, this.pointer);

            this.datagramPacketsArray = tmp;
            this.datagramPacketsArraySize = newDatagramPacketsArraySize;
            this.pointer = 0;

            this.Overflow.add(datagramPacketsArrayCopy);
        }
        this.arrayLock.unlock();
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
