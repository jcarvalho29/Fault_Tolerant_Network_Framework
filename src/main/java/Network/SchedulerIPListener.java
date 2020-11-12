package Network;

import Messages.TransferMetaInfo;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.locks.ReentrantLock;

public class SchedulerIPListener implements Runnable{
    Scheduler sch;

    public NIC nic;
    public InetAddress ip;
    public int port;
    public boolean isLinkLocal;

    private DatagramSocket ds;

    private boolean hasConnection;
    private boolean run;
    public boolean isRunning;
    private ReentrantLock connectionLock;


    public SchedulerIPListener(Scheduler sch, NIC nic, InetAddress ip, int port){
        System.out.println("===============================================================>NEW IP LISTENER");
        this.run = true;
        this.isRunning = false;
        this.connectionLock = new ReentrantLock();

        this.sch = sch;

        this.nic = nic;
        this.ip = ip;
        this.port = port;
        this.isLinkLocal = this.ip.isLinkLocalAddress();

        bindDatagramSocket();
    }

    private void bindDatagramSocket() {
        try {

            this.ds = new DatagramSocket(null);
            InetSocketAddress isa = new InetSocketAddress(this.ip, this.port);
            this.ds.bind(isa);
            System.out.println("BOUND TO " + this.ip + " " + this.port);
            this.hasConnection = true;
        }
        catch (SocketException e) {
            System.out.println("(SCHEDULERIPLISTENER) ERROR BINDING TO " + this.ip + " " + this.port);
            e.printStackTrace();
        }
    }
/*
    public void updateConnectionStatus(boolean value){
        this.connectionLock.lock();
        this.hasConnection = value;
        this.connectionLock.unlock();

        if(value && !this.isRunning){
            bindDatagramSocket();
            new Thread(this).start();
        }
        else{
            if(!value) {
                this.ds.close();
            }
        }
    }*/

    public void kill(){
        this.connectionLock.lock();
        this.run = false;
        this.isRunning = false;
        this.ds.close();
        this.connectionLock.unlock();
    }

    public void sendDP(DatagramPacket dp){
        try {
            if(this.ds != null && !this.ds.isClosed()) {
                this.ds.send(dp);
                System.out.println("SENT DP THROUGH " + this.ip + ":" + this.port);
            }
            else
                System.out.println("=====================================================>>>>> (SCHIPLISTENER) ERROR SENDING DP");
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {

        this.isRunning = true;
        int MTU;
        int tries = 0;

        while((MTU = this.nic.getMTU()) == -1 && tries < 4) {
            try {
                tries++;
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (tries == 4)
            MTU = 1500;

        byte[] buf = new byte[MTU];

        DatagramPacket dp = new DatagramPacket(buf, buf.length);

        this.connectionLock.lock();
        while(this.run && this.hasConnection){
            this.connectionLock.unlock();
            try {
                this.ds.receive(dp);
                System.out.println("(SCHEDULERIPLISTENER) RECEIVED SOMETHING FROM " + dp.getAddress());
                sch.processReceivedDP(dp, this.nic, this.ip, this.port);

                buf = new byte[MTU];
                dp = new DatagramPacket(buf, buf.length);
            }
            catch (SocketException se){
                this.run = false;
                System.out.println("SCHEDULERIPLISTENER Socket Closed");
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            this.connectionLock.lock();
        }
        this.connectionLock.unlock();
        this.isRunning = false;

        System.out.println("SCHEDULERIPLISTENER (" + this.ip + ":" + this.port + ")DEAD");
    }
}
