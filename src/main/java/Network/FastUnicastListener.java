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

    private ArrayList<DatagramPacket[]> Overflow;
    private DatagramPacket[] datagramPacketsArray;
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

        this.Overflow = new ArrayList<DatagramPacket[]>();
        //0.55 = 1.1 (10% maior) * 0.5 (primeiro sleep é de 0.5 segundos)
        this.datagramPacketsArraySize = (int) (this.dps* 0.55);
        this.datagramPacketsArray = new DatagramPacket[this.datagramPacketsArraySize];
        this.pointer = 0;
        this.receivedCM = 0;

        Random rand = new Random();
        boolean b = true;
        while(b) {
            try {
                this.port = rand.nextInt(20000) + 30000;
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
            try {
                this.arrayLock.lock();

                this.ds.close();
                this.ds = new DatagramSocket(null);
                InetSocketAddress isa = new InetSocketAddress(newIP, this.port);
                this.ds.bind(isa); // !!!! ADDRESS ALREADY IN USE
                this.ds.setReceiveBufferSize(3000000);

                this.ip = newIP;
                this.arrayLock.unlock();
            } catch (SocketException e) {
                e.printStackTrace();
                System.out.println("OLD IP " + this.ip + " NEW IP " + newIP);
            }
        }
    }

    public void run() {

            byte[] buffer;
        DatagramPacket dp;
        while (this.run){
            try{
                buffer = new byte[this.MTU];
                dp = new DatagramPacket(buffer, this.MTU);

                this.ds.receive(dp);

                if(!this.frcmFlag){
                    this.frcmFlag = true;
                    this.firstReceivedCM = System.currentTimeMillis();
                }

                this.arrayLock.lock();
                if(this.pointer == this.datagramPacketsArraySize){

                    DatagramPacket[] copy = new DatagramPacket[this.datagramPacketsArraySize];
                    System.arraycopy(this.datagramPacketsArray, 0, copy, 0, this.datagramPacketsArraySize);
                    this.Overflow.add(copy);
                    copy = null;

                    this.pointer = 0;
                }

                this.datagramPacketsArray[this.pointer] = dp;
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
        ArrayList<DatagramPacket[]> overflowCopy = null;
        int receivedCMCopy;

        this.arrayLock.lock();
        receivedCMCopy = this.receivedCM;
        this.receivedCM = 0;

        DatagramPacket[] objects = new DatagramPacket[receivedCMCopy];
        int cmInObjectsArrayCopy = 0;

        if(this.Overflow.size() > 0) {
            overflowCopy = new ArrayList<DatagramPacket[]>(this.Overflow);
            this.Overflow.clear();
        }

        if(pointer != 0) {
            System.arraycopy(this.datagramPacketsArray, 0, objects, cmInObjectsArrayCopy, this.pointer);
            cmInObjectsArrayCopy += this.pointer;
            this.pointer = 0;

        }
        this.arrayLock.unlock();


        if(overflowCopy != null) {
            for (DatagramPacket[] b : overflowCopy) {
                System.arraycopy(b, 0, objects, cmInObjectsArrayCopy, b.length);
                cmInObjectsArrayCopy += b.length;
            }
        }

        ChunkMessage[] cms = new ChunkMessage[objects.length];

/*        InetAddress[] addresses = new InetAddress[Math.min(10,objects.length)];
        int addressesPointer = 0;

        for (int i = Math.min(0, objects.length - 10); i < objects.length; i++){
            addresses[addressesPointer] = objects[i].getAddress();
            addressesPointer++;
        }*/

        for(int i = 0; i < objects.length; i++){
            cms[i] = (ChunkMessage) getObjectFromBytes(objects[i].getData());
        }

/*        ArrayList<InetAddress> addressesIndex = new ArrayList<InetAddress>();
        int[] count = new int[10];

        for(addressesPointer = 0; addressesPointer < addresses.length; addressesPointer++){
            count[addressesPointer] = 0;

            if(!addressesIndex.contains(addresses[addressesPointer]))
                addressesIndex.add(addresses[addressesPointer]);

            count[addressesIndex.indexOf(addresses[addressesPointer])]++;
        }

        int max = 0;
        int index = 0;

        for(int i = 0; i < count.length; i++){
            if(count[i] > max){
                max = count[i];
                index = i;
            }
        }

        this.perceivedTransmitterIP = addressesIndex.get(index);
*/
        return cms;
    }

    public void changeDatagramPacketsArraySize(int rtt, int dps){
        //500 = 2/1000
        rtt = Math.min(rtt, 5000);
        int newDatagramPacketsArraySize = ((dps * rtt) / 500); // EXCEPTION!!!!!!!
        DatagramPacket[] tmp = null;
        try {
            tmp = new DatagramPacket[newDatagramPacketsArraySize];
        }
        catch (Exception e){
            e.printStackTrace();
            System.out.println("(DPS * RTT) / 500 = " + dps + " * " + rtt + " / 500 = " + newDatagramPacketsArraySize);
        }
        this.arrayLock.lock();

        if (this.pointer != 0) {
            DatagramPacket[] datagramPacketsArrayCopy = new DatagramPacket[this.pointer];

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
