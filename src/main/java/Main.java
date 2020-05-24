import Data.ChunkManager;
import Data.ChunkManagerMetaInfo;
import Data.DataManager;
import Network.ListenerMainUnicast;
import Network.Scheduler;
import Network.TransferMultiSender;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;
import static java.lang.System.out;

import java.io.File;


public class Main{


    public static void main(String args[]) throws SocketException {
        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
        String MacAddress = "EMPTY";
        ArrayList<InetAddress> LocalLinks = new ArrayList<InetAddress>();
        MulticastSocket ms;
        DatagramSocket ds;
        byte[] mac;
        for (NetworkInterface netint : Collections.list(nets)) {
            //displayInterfaceInformation(netint);

            Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
            for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                if(inetAddress.isLinkLocalAddress()){
                    LocalLinks.add(inetAddress);
                    StringBuilder sb = new StringBuilder();
                    mac = netint.getHardwareAddress();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
                    }
                    MacAddress = sb.toString();
                }
            }
        }
/*
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

        String ftnfpath = createFTNFFolderStructure(MacAddress);

        //out.println(path);
        DataManager dm = new DataManager(ftnfpath, true);
        out.println("CREATED DATAMANAGER");
        Scheduler sc = new Scheduler(ftnfpath, true);
        out.println("SCHEDULER");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String docHash = "";
        String msgHash = "";

        while(true) {
            try {
                out.println("1 - LOAD DOCUMENT");
                out.println("2 - LOAD MESSAGE");
                out.println("3 - START LISTENER");
                out.println("4 - SEND DOCUMENT");
                out.println("5 - SEND MESSAGE");

                    String option = reader.readLine();


                switch (Integer.parseInt(option)) {
                    case 1: {
                        docHash = createDocument(dm, MacAddress, 1300);
                        break;
                    }
                    case 2: {
                        msgHash = createMessage(dm, MacAddress, 1300);
                        break;
                    }

                    case 3: {
                        startMainListener(dm, 1500);
                        break;
                    }

                    case 4: {
                        ChunkManager cm = dm.documents.get(docHash).cm;
                        InetAddress destIP = InetAddress.getByName("2001:1::10");
                        startSender(MacAddress, destIP,3333, 1500, cm, cm.getCMMI(), dm.documents.get(docHash).getDocumentName());
                        break;
                    }

                    case 5:{
                        ChunkManager cm = dm.messages.get(msgHash);
                        InetAddress destIP = InetAddress.getByName("192.168.0.10");
                        startSender(MacAddress, destIP,3333, 1500, cm, cm.getCMMI(), null);
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

/*
        dm.assembleDocument(MacAddress, docHash, "");




        if(dm.isReadyToBeSent(docHash)) {
            sc.schedule("127.0.0.1", 5, 3000, docHash);
        }
        if(!sc.isScheduled(docHash))
            dm.deleteDocument("myMAC", docHash);

        if(dm.isReadyToBeSent(msgHash))
            sc.schedule("127.0.0.1", 3, 3000, msgHash);

        sc.editPriority("127.0.0.1", 3, 1, msgHash);

        sc.editIP("127.0.0.1", "127.0.0.2", 1, msgHash);
        sc.editPort("127.0.0.2", 1, msgHash, 2000);
  */
    }

    private static void startMainListener(DataManager dm, int mtu){
        ListenerMainUnicast mainListener = new ListenerMainUnicast(dm, 3333, mtu);
        Thread t = new Thread(mainListener);

        t.start();
    }

    private static void startSender(String MacAddress, InetAddress destIP, int destPort, int mtu, ChunkManager cm, ChunkManagerMetaInfo cmmi, String docName){
        TransferMultiSender tms = new TransferMultiSender(MacAddress, destIP, destPort, 4444, mtu, cm, cmmi, docName, true);
        Thread t = new Thread(tms);
        t.start();
        
    }

    private static String createMessage(DataManager dm, String macAddress, int maxPayloadSize) {
        String hi = "hello world";
        byte[] info = hi.getBytes();

        return dm.newMessage(macAddress, info, maxPayloadSize);
    }

    private static String createDocument(DataManager dm, String MacAddress, int maxPayloadSize) {
        return dm.newDocument(MacAddress, (System.getProperty("user.home") + "/Desktop/"+"teste.pdf"), maxPayloadSize);
    }

    private static String createFTNFFolderStructure(String MacAddress) {
        String rootPath = System.getProperty("user.home") + "/Desktop/" + MacAddress + "/";
        out.println(rootPath);
        File root = new File(rootPath);
        while(!root.exists() && !root.isDirectory() && !root.mkdir());
        String ftnfpath = rootPath + "/FTNF/";

        root = new File(ftnfpath);
        while(!root.exists() && !root.isDirectory() && !root.mkdir());

        out.println("CREATED FTNF");
        return ftnfpath;
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
