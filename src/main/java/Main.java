import Data.DataManager;
import Network.Scheduler;

import java.net.*;
import java.util.*;
import static java.lang.System.out;
import java.io.File;


public class Main{


    public static void main(String args[]) throws SocketException {
        /*Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();

        ArrayList<InetAddress> LocalLinks = new ArrayList<InetAddress>();
        MulticastSocket ms;
        DatagramSocket ds;

        for (NetworkInterface netint : Collections.list(nets)) {
            //displayInterfaceInformation(netint);

            Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
            out.printf("Hardware address: %s\n", Arrays.toString(netint.getHardwareAddress()));
            for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                if(inetAddress.isLinkLocalAddress()){
                    LocalLinks.add(inetAddress);
                    out.printf("InetAddress: %s\n", inetAddress);
                }
            }
        }

        Teste t = new Teste();
        Thread th = new Thread(t);
        th.start();
        int i = 0;
        try {
            ms = new MulticastSocket(3000);
            ms.joinGroup(InetAddress.getByName("FF02:0:0:0:0:0:0:175"));
            ds = new DatagramSocket();
            DatagramPacket dp;
            while(true) {
                dp = new DatagramPacket(new byte[2], 2);
                ms.receive(dp);
                if(!LocalLinks.contains(dp.getAddress())) {
                    ds.send(new DatagramPacket(new byte[2], 2, dp.getAddress(), 4000));
                    out.println("RECEBI de " + dp.getAddress().toString() + " " + (i++));
                }
                }
        } catch (IOException e) {
            e.printStackTrace();e.printStackTrace();
        }*/

        String rootPath = System.getProperty("user.home") + "/Desktop";
        String ftnfpath = rootPath + "/FTNF/";
        File root = new File(ftnfpath);
        while(!root.exists() && !root.isDirectory() && !root.mkdir());

        String path = ftnfpath + "Data/";
        root = new File(path);
        while(!root.exists() && !root.isDirectory() && !root.mkdir());

        path = ftnfpath + "Network/";
        root = new File(path);
        while(!root.exists() && !root.isDirectory() && !root.mkdir());
        //out.println(path);
        DataManager dm = new DataManager(ftnfpath, true);
        Scheduler sc = new Scheduler(ftnfpath, true);

        dm.newDocument("myMAC", (rootPath+"/teste.pdf"), "teste.pdf", 1000);
        dm.assembleDocument("myMAC", "e3f7436cfd373af2223a7ffadaafbdc259b1af1720bdac6a50ccbe4339efe178", "");
        dm.deleteDocument("myMAC", "e3f7436cfd373af2223a7ffadaafbdc259b1af1720bdac6a50ccbe4339efe178");

        String hi = "hello world";
        byte[] info = hi.getBytes();

        dm.newMessage("myMAC", info, 1000);
        info = dm.getInfoInByteArray("myMAC", "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");

        sc.schedule("127.0.0.1", 5, 3000, "e3f7436cfd373af2223a7ffadaafbdc259b1af1720bdac6a50ccbe4339efe178");
        sc.schedule("127.0.0.1", 3, 3000, "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");

        sc.editPriority("127.0.0.1", 3, 1, "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");

        sc.editIP("127.0.0.1", "127.0.0.2", 1, "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
        sc.editPort("127.0.0.2", 1, "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", 2000);
        out.println("READ FROM CHUNKS => " + new String(info));
    }

    static void displayInterfaceInformation(NetworkInterface netint) throws SocketException {
        out.printf("Display name: %s\n", netint.getDisplayName());
        out.printf("Name: %s\n", netint.getName());
        Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();

        for (InetAddress inetAddress : Collections.list(inetAddresses)) {
            out.printf("InetAddress: %s\n", inetAddress);
            out.printf("isLocal?: %s\n", inetAddress.isLinkLocalAddress());
        }

        out.printf("Up? %s\n", netint.isUp());
        out.printf("Loopback? %s\n", netint.isLoopback());
        out.printf("PointToPoint? %s\n", netint.isPointToPoint());
        out.printf("Supports multicast? %s\n", netint.supportsMulticast());
        out.printf("Virtual? %s\n", netint.isVirtual());
        out.printf("Hardware address: %s\n",
                Arrays.toString(netint.getHardwareAddress()));
        out.printf("MTU: %s\n", netint.getMTU());
        out.printf("\n");
    }
}
