import Data.ChunkManager;
import Data.ChunkManagerMetaInfo;
import Data.DataManager;
import Network.ListenerMainUnicast;
import Network.NIC;
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


    public static void main(String args[]){
        Random rand = new Random();
        int nodeIdentifier = rand.nextInt();

        String ftnfpath = createFTNFFolderStructure();

        //out.println(path);
        DataManager dm = new DataManager(ftnfpath, true);
        out.println("CREATED DATAMANAGER");
        Scheduler sc = new Scheduler(ftnfpath, true);
        out.println("SCHEDULER");

        ArrayList<NIC> nics = new ArrayList<NIC>();
        getNICs(nics);

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String docHash = "";
        String msgHash = "";

        while(true) {
            try {
                out.println("0 - LOAD DOCUMENT jar");
                out.println("1 - LOAD DOCUMENT 100MB.zip");
                out.println("2 - LOAD DOCUMENT 512MB.zip");
                out.println("3 - LOAD MESSAGE");
                out.println("4 - START LISTENER");
                out.println("5 - SEND DOCUMENT");
                out.println("6 - SEND MESSAGE");
                out.println("7 - MOUNT DOC");

                    String option = reader.readLine();


                switch (Integer.parseInt(option)) {
                    case 0: {
                        docHash = createDocument(dm, 1300, "Fault_Tolerant_Network_Framework/target/Fault_Tolerant_Network_Framework-1.0-SNAPSHOT.jar");
                        break;
                    }
                    case 1: {
                        docHash = createDocument(dm, 1300, "100MB.zip");
                        break;
                    }
                    case 2: {
                        docHash = createDocument(dm, 1300, "512MB.zip");
                        break;
                    }

                    case 3: {
                        msgHash = createMessage(dm, 1300);
                        break;
                    }

                    case 4: {
                        startMainListener(dm, nics,3333);
                        break;
                    }

                    case 5: {
                        ChunkManager cm = dm.documents.get(docHash).cm;
                        startSender(nodeIdentifier, 3333, 4444, 1500, nics, cm, cm.getCMMI(), dm.documents.get(docHash).documentName);
                        break;
                    }

                    case 6:{
                        ChunkManager cm = dm.messages.get(msgHash);
                        startSender(nodeIdentifier, 3333, 4444, 1500, nics, cm, cm.getCMMI(), null);
                        break;
                    }
                    case 7:{
                        mountFile(dm);
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void getNICs(ArrayList<NIC> nics){
        String nicPath = "/sys/class/net/";
        String wirelessPath = "/wireless";
        File nicFolder = new File(nicPath);
        File wirelessFolder;
        String[] nicNames;

        if(nicFolder.exists() && nicFolder.isDirectory()){
            nicNames = nicFolder.list();
            for(String nicName : nicNames)
                if(!nicName.equals("lo")) {
                    wirelessFolder = new File(nicPath + nicName + wirelessPath);
                    if(wirelessFolder.exists() && wirelessFolder.isDirectory())
                        nics.add(new NIC(nicName, true));
                    else
                        nics.add(new NIC(nicName, false));
                }
        }
    }

    private static void startMainListener(DataManager dm, ArrayList<NIC> nics, int unicastPort){

/*        Enumeration<NetworkInterface> nets = null;
        try {
            nets = NetworkInterface.getNetworkInterfaces();
            NetworkInterface nic = null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            byte[] mac;
            ArrayList<NetworkInterface> interfaces = new ArrayList<NetworkInterface>();

            for (NetworkInterface netInterface : Collections.list(nets)) {
                displayInterfaceInformation(netInterface);
                if(!netInterface.isLoopback()) {
                    interfaces.add(netInterface);
                }
            }

            if(interfaces.size() > 1) {
                for (NetworkInterface netInterface : interfaces) {

                    out.println("CHOOSE A NIC TO LISTEN");
                    mac = netInterface.getHardwareAddress();
                    StringBuilder sb = new StringBuilder();

                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
                    }

                    out.println("Index " + netInterface.getIndex() + ")  " + sb.toString());
                    Enumeration<InetAddress> inetAddresses = netInterface.getInetAddresses();
                    for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                        out.println("   LocalLink: " + inetAddress.isLinkLocalAddress() + " => " + inetAddress);
                    }
                    out.println("");
                }

                int nicIndex = -1;

                nicIndex = Integer.parseInt(reader.readLine());
                nic = NetworkInterface.getByIndex(nicIndex);
            }
            else
                if(interfaces.size() == 1)
                    nic = interfaces.get(0);

            if(nic != null){


                out.println("   1) Local Link");
                out.println("   2) External Link");

                boolean isLocalLink = (Integer.parseInt(reader.readLine())) == 1;

                ListenerMainUnicast mainListener = new ListenerMainUnicast(dm,  , isLocalLink, unicastPort, 50);
                Thread t = new Thread(mainListener);
                t.start();


            }
            else{
                out.println("ERROR RETRIEVING NETWORK INTERFACES");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }*/

        NIC nic;

        out.println("CHOOSE A NETWORK INTERFACE ADDRESS");
        for(int i = 0 ; i < nics.size(); i++){
            nic = nics.get(i);
            out.println(i + ") " + nic.name);
            out.println("\tisWireless? " + nic.isWireless);
            out.println("\tSpeed " + nic.getSpeed()/1000 + " Mb/s");
            out.println("\tMTU " + nic.getMTU() + " Bytes");
            out.println("\tAddresses " + nic.addresses);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        int choice = -1;
        while(choice == -1) {
            try {
                choice = Integer.parseInt(reader.readLine());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        nic = nics.get(choice);

        out.println("   1) Local Link");
        out.println("   2) External Link");

        boolean isLocalLink = false;

        try {
            isLocalLink = (Integer.parseInt(reader.readLine())) == 1;
        } catch (IOException e) {
            e.printStackTrace();
        }

        ListenerMainUnicast mainListener = new ListenerMainUnicast(dm, nic, isLocalLink, unicastPort, 50);
        nic.registerNewLMUListener(mainListener);
    }

    private static void startSender(int nodeIdentifier, int destPort, int unicastPort, int mtu, ArrayList<NIC> nics, ChunkManager cm, ChunkManagerMetaInfo cmmi, String docName){

        out.println("GIVE ME A IP TO SEND");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String ip = "";
        InetAddress destIP = null;
        try {
            ip = reader.readLine();
             destIP = InetAddress.getByName(ip);
        } catch (IOException e) {
            e.printStackTrace();
        }

        NIC nic;

        out.println("CHOOSE A NETWORK INTERFACE ADDRESS");
        for(int i = 0 ; i < nics.size(); i++){
            nic = nics.get(i);
            out.println(i + ") " + nic.name);
            out.println("\tisWireless? " + nic.isWireless);
            out.println("\tSpeed " + nic.getSpeed()/1000 + " Mb/s");
            out.println("\tMTU " + nic.getMTU() + " Bytes");
            out.println("\tAddresses " + nic.addresses);
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));

        int choice = -1;
        while(choice == -1) {
            try {
                choice = Integer.parseInt(r.readLine());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        nic = nics.get(choice);

        out.println("   1) Local Link");
        out.println("   2) External Link");

        boolean isLocalLink = false;

        try {
            isLocalLink = (Integer.parseInt(reader.readLine())) == 1;
        } catch (IOException e) {
            e.printStackTrace();
        }

        TransferMultiSender tms = new TransferMultiSender(nodeIdentifier,  destIP, destPort, nic, isLocalLink, unicastPort, mtu, cm, cmmi, docName, true);
        nic.registerNewTMSListener(tms);
    }

    private static String createMessage(DataManager dm, int maxPayloadSize) {
        String hi = "hello world";
        byte[] info = hi.getBytes();

        return dm.newMessage(info, maxPayloadSize);
    }

    private static String createDocument(DataManager dm, int maxPayloadSize, String docName) {
        //return dm.newDocument((System.getProperty("user.home") + "/Desktop/"), maxPayloadSize);
        return dm.newDocument((System.getProperty("user.home") + "/Desktop/" + docName), maxPayloadSize, 100000);
                //Fault Tolerant Network Framework/target/Fault_Tolerant_Network_Framework-1.0-SNAPSHOT.jar")" +

    }

    private static String createFTNFFolderStructure() {
        String ftnfpath = System.getProperty("user.home") + "/Desktop/FTNF/";
        out.println(ftnfpath);
        File root = new File(ftnfpath);
        while(!root.exists() && !root.isDirectory() && !root.mkdir());

        out.println("CREATED FTNF");
        return ftnfpath;
    }

    private static void mountFile(DataManager dm){
        out.println(dm.documents.keySet());

        BufferedReader reader =
                new BufferedReader(new InputStreamReader(System.in));
        String choice = "";
        int i = 0;
        InetAddress destIP = null;
        try {
            choice = reader.readLine();
            i = Integer.parseInt(choice) - 1;
        } catch (IOException e) {
            e.printStackTrace();
        }

        String docHash = new ArrayList<> (dm.documents.keySet()).get(i);
        out.println("MOUNTING ON " + System.getProperty("user.home") + "/Desktop/");
        dm.assembleDocument(docHash, System.getProperty("user.home") + "/Desktop/");
    }

    static void displayInterfaceInformation(NetworkInterface netint) throws SocketException {
        out.printf("Display name: %s\n", netint.getDisplayName());
        out.printf("Name: %s\n", netint.getName());
        Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();

        for (InetAddress inetAddress : Collections.list(inetAddresses)) {
            out.printf("InetAddress: %s\n", inetAddress);
            out.printf("isLocal?: %s\n", inetAddress.isLinkLocalAddress());
        }

        for (InterfaceAddress address : netint.getInterfaceAddresses()) {
            System.out.println("FULL ADDRESS => " + address);
        }

        out.printf("Up? %s\n", netint.isUp());
        out.printf("Loopback? %s\n", netint.isLoopback());
        out.printf("PointToPoint? %s\n", netint.isPointToPoint());
        out.printf("Supports multicast? %s\n", netint.supportsMulticast());
        out.printf("Virtual? %s\n", netint.isVirtual());
        out.printf("Hardware address: %s\n",
                Arrays.toString(netint.getHardwareAddress()));
        out.printf("MTU: %s\n", netint.getMTU());
        out.print("\n");
    }
}
