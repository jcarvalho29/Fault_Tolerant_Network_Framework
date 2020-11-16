package Network;

import Messages.Knock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public class KnockManager implements Runnable{
    private boolean run;

    private final NIC nic;
    private boolean hasConnection;

    private boolean isIPv6;
    private MulticastSocket mcs;
    private InetAddress mcGroupIP;
    private int mcGroupPort;
    private ReentrantLock dpRecivedMC_Lock;
    private ArrayList<DatagramPacket> dpReceivedMC;

    private DatagramSocket ds;
    private InetAddress ownIP;
    private int ownPort;
    private boolean isLinkLocal;

    private ReentrantLock knockInfo_Lock;
    private byte[] knockInfo;

    public KnockManager(NIC nic, InetAddress mcGroupIP, int mcGroupPort, byte[] knockInfo){
        this.run = true;

        this.nic = nic;

        this.mcGroupIP = mcGroupIP;

        this.isIPv6 = this.mcGroupIP instanceof Inet6Address;

        this.isLinkLocal = this.mcGroupIP.isMCLinkLocal();

        this.mcGroupPort = mcGroupPort;
        this.dpRecivedMC_Lock = new ReentrantLock();
        this.dpReceivedMC = new ArrayList<DatagramPacket>();

        this.ownPort = -1;

        this.knockInfo_Lock = new ReentrantLock();
        this.knockInfo = knockInfo;

        if(this.isLinkLocal)
            changeIP(nic.getLinkLocalAddresses());
        else
            changeIP(nic.getNONLinkLocalAddresses());
    }

    private void bindMulticastGroupSocket(){
        boolean bound = false;

        while(!bound) {
            try {
                this.mcs = new MulticastSocket(null);
                this.mcs.setInterface(this.ownIP);
                this.mcs.joinGroup(this.mcGroupIP);
                System.out.println("(KNOCKMANAGER) BOUND MULTICASTSOCKET TO " + this.mcGroupIP + ":" + this.mcGroupPort);
                bound = true;
                this.hasConnection = true;
            } catch (IOException e) {
                System.out.println("(KNOCKMANAGER) ERROR BINDING MULTICASTSOCKET TO " + this.mcGroupIP + ":" + this.mcGroupPort);
                e.printStackTrace();
            }
        }
    }

    private void bindUnicastDatagramSocket() {
        Random rand = new Random();
        boolean bound = false;

        while (!bound) {
            try {
                if(this.ownPort < 0)
                    this.ownPort = rand.nextInt(99) + 6000;

                this.ds = new DatagramSocket(null);
                InetSocketAddress isa = new InetSocketAddress(this.ownIP, this.ownPort);
                this.ds.bind(isa);
                System.out.println("(KNOCKMANAGER) BOUND DATAGRAMSOCKET TO " + this.ownIP + ":" + this.ownPort);

                bound = true;
                this.hasConnection = true;
            } catch (IOException e) {
                System.out.println("(KNOCKMANAGER) ERROR BINDING DATAGRAMSOCKET TO " + this.ownIP + ":" + this.ownPort);
                e.printStackTrace();

                try {
                    this.ownPort = -1;
                    Thread.sleep(500);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
            }
        }
    }

    public void changeIP(ArrayList<InetAddress> addresses){


        if(!addresses.contains(this.ownIP)) {
            InetAddress ip = null;

            for (InetAddress addr : addresses) {
                if (addr.isLinkLocalAddress() == this.isLinkLocal && (this.isIPv6 == addr instanceof Inet6Address))
                    ip = addr;
            }

            if (ip != null) {
                this.ownIP = ip;

                if(this.ds != null && !this.ds.isClosed()) {
                    System.out.println("UNICASTSOCKET WASN'T CLOSED BEFORE THE CHANGEIP BIND");
                    this.ds.close();
                }

                bindUnicastDatagramSocket();
                bindMulticastGroupSocket();

                System.out.println("hasConnection? " + this.hasConnection + " => TRUE");

                this.hasConnection = true;
                Thread t = new Thread(this);
                t.start();
                System.out.println("CHANGED IP AND CREATED NEW THREAD (changeIP)");

                System.out.println("Listening to NEW IP =>" + this.ownIP + " PORT =>" + this.ownPort + "\nhasConnection? " + this.hasConnection);
            }
            else {
                System.out.println("NEW ADDRESSES SET BUT NO CORRESPONDING IP (hasConnection => FALSE)");
                this.ownIP = null;
                updateConnectionStatus(false);
            }
        }
        else {
            System.out.println("SAME IP AS BEFORE");
            if (!this.hasConnection) {
                System.out.println("    BUT HAD NO CONNECTION (hasConnection => TRUE)");
                updateConnectionStatus(true);
            }
        }
    }

    public void updateConnectionStatus(boolean value){

        if(value && !this.hasConnection){
            bindUnicastDatagramSocket();
            bindMulticastGroupSocket();

            new Thread(this).start();
        }
        else{
            if(!value){
                if(this.ds != null)
                    this.ds.close();
                if(this.mcs != null)
                    this.mcs.close();
            }
        }
    }

    public void changeKnockInfo(byte[] newKnockInfo){
        this.knockInfo_Lock.lock();
        this.knockInfo = newKnockInfo;
        this.knockInfo_Lock.unlock();
    }

    public ArrayList<DatagramPacket> searchForTimePeriod(int sec){

        if(sec > 0 && this.hasConnection) {
            ArrayList<DatagramPacket> receivedDP = new ArrayList<DatagramPacket>();

            int MTU;
            int tries = 0;

            while ((MTU = this.nic.getMTU()) == -1 && tries < 4) {
                try {
                    tries++;
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (tries == 4)
                MTU = 1500;

            byte[] buf;
            DatagramPacket dp;

            long start = System.currentTimeMillis();
            int secInMilli = sec * 1000;


            MulticastSocket mcs = null;
            boolean dpSent = false;

            while (!dpSent){
                try {
                    mcs = new MulticastSocket();
                    mcs.setInterface(this.ownIP);
                    this.knockInfo_Lock.lock();
                    Knock k = new Knock(this.knockInfo);
                    byte[] bytes = getBytesFromObject(k);
                    DatagramPacket mcDP = new DatagramPacket(bytes, bytes.length, this.mcGroupIP, this.mcGroupPort);
                    System.out.println("MULTICAST SENT TO " + this.mcGroupIP + ":" + this.mcGroupPort);
                    this.knockInfo_Lock.unlock();
                    mcs.send(mcDP);
                    mcs.close();
                    dpSent = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            while (this.hasConnection && (System.currentTimeMillis() - start < secInMilli)){
                try {
                    this.ds.setSoTimeout(1000);

                    buf = new byte[MTU];
                    dp = new DatagramPacket(buf, MTU);

                    this.ds.receive(dp);

                    receivedDP.add(dp);
                    System.out.println("RECEIVED UNICAST");

                } catch (SocketException e) {
                    //e.printStackTrace();
                } catch (IOException e) {
                    //e.printStackTrace();
                    break;
                }
            }
            System.out.println("(KNOCKMANAGER) UNICAST DIED hasConnection? " + this.hasConnection);
            return receivedDP;
        }

        return null;
    }

    public ArrayList<DatagramPacket> getMCReceivedDP(){

        this.dpRecivedMC_Lock.lock();
        ArrayList<DatagramPacket> copy = this.dpReceivedMC;
        this.dpReceivedMC = new ArrayList<DatagramPacket>();
        this.dpRecivedMC_Lock.unlock();

        return copy;
    }

    private void sendKnockResponse(DatagramPacket dp) {
        this.knockInfo_Lock.lock();
        Knock k = new Knock(this.knockInfo);
        byte[] bytes = getBytesFromObject(k);
        DatagramPacket ResponseDP = new DatagramPacket(bytes, bytes.length, dp.getAddress(), dp.getPort());
        this.knockInfo_Lock.unlock();

        if(!this.ds.isClosed() && this.hasConnection){
            try {
                this.ds.send(ResponseDP);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("(KNOCKMANAGER) ERROR SENDING RESPONSE KNOCK hasConnection " + this.hasConnection + " ds.isClosed() "+ this.ds.isClosed());
            }
        }
        else
            System.out.println("(KNOCKMANAGER) COULD NOT RESPOND TO MULTICAST KNOCK hasConnection " + this.hasConnection + " ds.isClosed() "+ this.ds.isClosed());
    }

    public void run(){

        int MTU;
        int tries = 0;

        while ((MTU = this.nic.getMTU()) == -1 && tries < 4) {
            try {
                tries++;
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (tries == 4)
            MTU = 1500;

        byte[] buf;
        DatagramPacket dp;


        while (this.run && !this.mcs.isClosed() && this.hasConnection) {
            try {
                buf = new byte[MTU];
                dp = new DatagramPacket(buf, MTU);

                this.mcs.receive(dp);

                this.dpRecivedMC_Lock.lock();
                this.dpReceivedMC.add(dp);
                this.dpRecivedMC_Lock.unlock();

                System.out.println("received MULTICAST");
                sendKnockResponse(dp);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.out.println("(KNOCKMANAGER) MULTICAST LISTENER DIED run? " + this.run + " mcs.isCLosed()? " + this.mcs.isClosed() + " hasConnection? " + this.hasConnection);
    }

    public void kill(){
        this.run = false;

        this.ds.close();
        this.mcs.close();
        this.nic.removeKnockManager(this);
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

        return bos.toByteArray();
    }
}
