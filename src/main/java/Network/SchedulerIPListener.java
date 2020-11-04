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

    private boolean run;
    private boolean hasConnection;
    private ReentrantLock connectionLock;


    public SchedulerIPListener(Scheduler sch, NIC nic, InetAddress ip, int port){
        this.run = true;
        this.hasConnection = true;
        this.connectionLock = new ReentrantLock();

        this.sch = sch;

        this.nic = nic;
        this.ip = ip;
        this.port = port;
        this.isLinkLocal = this.ip.isLinkLocalAddress();

        try {
            this.ds = new DatagramSocket(null);
            InetSocketAddress isa = new InetSocketAddress(this.ip, this.port);
            this.ds.bind(isa);
            System.out.println("BOUND TO " + this.ip + " " + this.port);
        }
        catch (SocketException e) {
            System.out.println("ERROR BINDING TO " + this.ip + " " + this.port);
            e.printStackTrace();
        }
    }

/*    public void changeIPPort(InetAddress ip, int port){
        if(this.ds != null)
            this.ds.close();

        this.ip = ip;
        this.port = port;
        this.isLinkLocal = this.ip.isLinkLocalAddress();

        try {
            this.ds = new DatagramSocket();
            InetSocketAddress isa = new InetSocketAddress(this.ip, this.port);
            this.ds.bind(isa);
        }
        catch (SocketException e) {
            e.printStackTrace();
        }
    }*/

    public void updateHasConnectionStatus(boolean value){
        this.connectionLock.lock();
        this.hasConnection = value;
        this.connectionLock.unlock();
    }

/*    public void restart(){
        this.run = true;

        Thread t = new Thread(this);
        t.start();

    }*/

    public void kill(){
        this.connectionLock.lock();
        this.run = false;
        this.ds.close();
        this.connectionLock.unlock();
    }

    public void sendDP(DatagramPacket dp){
        try {
            this.ds.send(dp);
            System.out.println("SENT TMI");
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {

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
        //System.out.println("GOT MTU = " + MTU);

        byte[] buf = new byte[MTU];

        DatagramPacket dp = new DatagramPacket(buf, buf.length);

        this.connectionLock.lock();
        while(this.hasConnection && this.run){
            this.connectionLock.unlock();
            try {
                ds.receive(dp);

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
        System.out.println("SCHEDULERIPLISTENER DEAD");
    }
}
