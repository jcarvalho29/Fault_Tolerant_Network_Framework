package Network;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class SchedulerIPListener implements Runnable{
    Scheduler sch;

    public NIC nic;
    public InetAddress ownIP;
    public int ownPort;
    private boolean lockPort;
    public boolean isLinkLocal;

    private DatagramSocket ds;

    private ReentrantLock datagramPackets_Lock;
    private ScheduledExecutorService lateDPScheduler;
    private ArrayList<DatagramPacket> datagramPackets;

    private boolean hasConnection;
    private boolean run;
    public boolean isRunning;
    private ReentrantLock connectionLock;


    public SchedulerIPListener(Scheduler sch, NIC nic, InetAddress ip, boolean isLinkLocal){
        System.out.println("===============================================================>NEW IP LISTENER " + ip);
        this.run = false;
        this.isRunning = false;
        this.connectionLock = new ReentrantLock();

        this.sch = sch;

        this.nic = nic;
        this.ownIP = ip;
        this.ownPort = -1;
        this.lockPort = false;

        this.datagramPackets_Lock = new ReentrantLock();
        this.datagramPackets = new ArrayList<DatagramPacket>();
        this.lateDPScheduler = Executors.newSingleThreadScheduledExecutor();
        this.isLinkLocal = isLinkLocal;

    }

    private void bindDatagramSocket() {
        boolean bound = false;
        Random rand = new Random();

        while (!bound) {
            //System.out.println("START BIND");
            try {
                if(!this.lockPort && this.ownPort == -1) {
                    this.ownPort = rand.nextInt(999) + 4000;
                }
                this.ds = new DatagramSocket(null);
                InetSocketAddress isa = new InetSocketAddress(this.ownIP, this.ownPort);
                this.ds.bind(isa);
                System.out.println("(SECHDULERIPLISTENER) BOUND TO " + this.ownIP + ":" + this.ownPort);
                this.hasConnection = true;
                bound = true;
            }
            catch (SocketException e) {
                System.out.println("(SCHEDULERIPLISTENER) ERROR BINDING TO " + this.ownIP + ":" + this.ownPort);
                if(!this.lockPort){
                    this.ownPort = -1;
                    this.lockPort = true;
                }
                e.printStackTrace();
            }

            if(!bound){
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        //System.out.println("            END BIND");
    }

    public void changeOwnIP(ArrayList<InetAddress> addresses){
        System.out.println("\t\t\t(SCHEDULERIPLISTENER) CHANGING IP!!");

        //if(!addresses.contains(this.ownIP)){
            InetAddress newIP = null;

            for (InetAddress address : addresses)
                if (address.isLinkLocalAddress() == this.isLinkLocal) {
                    newIP = address;
                    break;
                }

            if(newIP != null) {

                this.ownIP = newIP;

                System.out.println("(SCHEDULERIPLISTENER)hasConnection? " + this.hasConnection);

                System.out.println("(SCHEDULERIPLISTENER)GOT NEW IP =>" + this.ownIP + "\nhasConnection? true");


                if(this.ds != null && !this.ds.isClosed())
                    this.ds.close();

                //bindDatagramSocket();

                updateConnectionStatus(true);
            }
            else {
                System.out.println("(SCHEDULERIPLISTENER)NEW IP SET BUT NO CORRESPONDING IP (hasConnection => FALSE)");
                updateConnectionStatus(false);
            }
        //}
/*        else {
            System.out.println("SAME IP AS BEFORE");
            if (!this.hasConnection) {
                System.out.println("    BUT HAD NO CONNECTION (hasConnection => TRUE)");
                updateConnectionStatus(true);
            }
        }*/
    }

    public void updateConnectionStatus(boolean value){

        this.connectionLock.lock();
        this.hasConnection = value;
        this.connectionLock.unlock();

        if(!value && this.ds != null && !this.ds.isClosed()) {
            this.ownIP = null;
            this.ds.close();
            System.out.println("(SCHEDULERIPLISTENER) CLOSED DATAGRASOCKET ON UPDATE CONNECTION STATUS");
        }
    }

    public void kill(){
        this.connectionLock.lock();
        this.run = false;
        this.isRunning = false;
        if(this.ds != null && this.ds.isClosed())
            this.ds.close();
        this.connectionLock.unlock();
    }

    public void reactivate(){
        //System.out.println("(SCHEDULERIPLISTENER) REACTIVATE");
        this.run = true;
        if(this.ds == null || this.ds.isClosed())
            bindDatagramSocket();
        new Thread(this).start();
    }

    private final Runnable sendLateDPs = () -> {

        if(this.hasConnection) {
            this.datagramPackets_Lock.lock();
            try {
                int numberOfDatagrams = this.datagramPackets.size();
                DatagramPacket dp;
                for(int i = 0; i <  numberOfDatagrams && this.hasConnection; i++) {
                    if (this.ds != null && !this.ds.isClosed()) {
                        dp = this.datagramPackets.get(i);
                        this.ds.send(dp);
                        this.datagramPackets.remove(i);
                        i--;
                        numberOfDatagrams--;
                        System.out.println("(SCHEDULERIPLISTENER) ===============>SENT LATE DATAGRAMPACKET THROUGH " + this.ownIP + ":" + this.ownPort + " TO " + dp.getAddress() + ":" + dp.getPort());
                    } else
                        System.out.println("=====================================================>>>>> (SCHIPLISTENER) ERROR SENDING DP");
                }
            } catch (IOException e) {
                System.out.println("IP " + this.ownIP);
                e.printStackTrace();
            }
            this.datagramPackets_Lock.unlock();
        }
        else {
            this.datagramPackets_Lock.lock();
            this.lateDPScheduler.schedule(this.sendLateDPs, 1, TimeUnit.SECONDS);
            this.datagramPackets_Lock.lock();
        }
    };

        public void sendDP(DatagramPacket dp){
        if(this.hasConnection) {
            try {
                if (this.ds != null && !this.ds.isClosed()) {
                    this.ds.send(dp);
                    System.out.println("SENT DP THROUGH " + this.ownIP + ":" + this.ownPort);
                } else
                    System.out.println("=====================================================>>>>> (SCHIPLISTENER) ERROR SENDING DP");
            } catch (IOException e) {
                System.out.println("IP " + this.ownIP);
                e.printStackTrace();
            }
        }
        else{
            this.datagramPackets_Lock.lock();
            this.datagramPackets.add(dp);
            this.lateDPScheduler.schedule(this.sendLateDPs, 1, TimeUnit.SECONDS);
            this.datagramPackets_Lock.unlock();
        }
    }

    public void run() {
        System.out.println("NEW THREAD RUNNING SCHEDULERIPLISTENER");
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
                System.out.println("(SCHEDULERIPLISTENER) RECEIVED SOMETHING FROM " + dp.getAddress() + " " +dp.getData().length);
                sch.processReceivedDP(dp, this.nic, this.ownIP, this.ownPort);

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

        System.out.println("SCHEDULERIPLISTENER (" + this.ownIP + ":" + this.ownPort + ")DEAD");
    }
}
