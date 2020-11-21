import Data.DataManager;
import Data.Document;
import Network.KnockManager;
import Network.ListenerMainUnicast;
import Network.NIC;
import Network.Scheduler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;

import java.io.File;


public class Main{


    public static void main(String args[]) throws InterruptedException {
        String ftnfpath = createFTNFFolderStructure();

        DataManager dm = new DataManager(ftnfpath, true);
       System.out.println("CREATED DATAMANAGER");

        Scheduler sc = new Scheduler(ftnfpath, true, dm);
        System.out.println("CREATED SCHEDULER");

        ArrayList<NIC> nics = new ArrayList<NIC>();
        getNICs(nics, sc);
        Thread.sleep(1000);
        for(NIC nic : nics)
            sc.registerNIC(nic);


        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder sb = new StringBuilder();

        KnockManager km = null;

        //MENU
        sb.append("=================================\n")
                .append("1 - LOAD DOCUMENT jar\n")
                .append("2 - LOAD DOCUMENT 100MB.zip\n")
                .append("3 - LOAD DOCUMENT 512MB.zip\n")
                .append("4 - LOAD MESSAGE\n")
                .append("5 - START LISTENER\n")
                .append("6 - SEND LOADED INFO\n")
                .append("7 - START KNOCK MANAGER\n")
                .append("8 - SEND KNOCK\n")
                .append("9 - MOUNT DOC\n")
                .append("0 - PRINT SCHEDULE\n")
                .append("=================================");

        while(true) {
            try {
                //PRINT MENU
                System.out.println(sb.toString());

                    String option = reader.readLine();


                switch (Integer.parseInt(option)) {
                    case 1: {
                        createDocument(dm, 1300, "Fault_Tolerant_Network_Framework/target/Fault_Tolerant_Network_Framework-1.0-SNAPSHOT.jar");
                        break;
                    }
                    case 2: {
                        createDocument(dm, 1300, "100MB.zip");
                        break;
                    }
                    case 3: {
                        createDocument(dm, 1300, "512MB.zip");
                        break;
                    }

                    case 4: {
                        createMessage(dm, 1300);
                        break;
                    }

                    case 5: {
                        startMainListener(dm, nics,3333);
                        break;
                    }

                    case 6: {
                        sendLoadedInfo(sc, dm, 3333);
                        break;
                    }

                    case 7:{
                        km = startKnockManager(nics);
                        break;
                    }

                    case 8:{
                        if(km != null) {
                            System.out.println("MAIN" + km.searchForTimePeriod(10));
                        }
                        break;
                    }

                    case 9:{
                        mountFile(dm);
                        break;
                    }

                    case 0:{
                        sc.printSchedule();
                        break;
                    }

                    default:
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void getNICs(ArrayList<NIC> nics, Scheduler sc){
        String nicPath = "/sys/class/net/";
        String wirelessPath = "/wireless";
        File nicFolder = new File(nicPath);
        File wirelessFolder;
        String[] nicNames;

        if(nicFolder.exists() && nicFolder.isDirectory()){
            nicNames = nicFolder.list();
            if(nicNames != null) {
                for (String nicName : nicNames)
                    if (!nicName.equals("lo") && !nicName.equals("enp2s0")) {
                        wirelessFolder = new File(nicPath + nicName + wirelessPath);
                        if (wirelessFolder.exists() && wirelessFolder.isDirectory())
                            nics.add(new NIC(nicName, true, sc));
                        else
                            nics.add(new NIC(nicName, false, sc));
                    }
            }
        }
    }

    private static void startMainListener(DataManager dm, ArrayList<NIC> nics, int unicastPort){

        NIC nic;

        System.out.println("CHOOSE A NETWORK INTERFACE ADDRESS");
        for(int i = 0 ; i < nics.size(); i++){
            nic = nics.get(i);
            System.out.println(i + ") " + nic.name);
            System.out.println("\tisWireless? " + nic.isWireless);
/*            System.out.println("\tSpeed " + nic.getSpeed()/1000 + " Mb/s");
            System.out.println("\tMTU " + nic.getMTU() + " Bytes");*/
            System.out.println("\tAddresses " + nic.addresses);
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

        System.out.println("   1) Local Link");
        System.out.println("   2) External Link");

        boolean isLocalLink = false;

        try {
            isLocalLink = (Integer.parseInt(reader.readLine())) == 1;
        } catch (IOException e) {
            e.printStackTrace();
        }

        ListenerMainUnicast mainListener = new ListenerMainUnicast(dm, nic, isLocalLink, unicastPort);
        nic.registerLMUListener(mainListener);
    }

    private static void sendLoadedInfo(Scheduler sc, DataManager dm, int destPort){

        StringBuilder sb = new StringBuilder();
        int i = 1;
        ArrayList <String> hashs = new ArrayList<String>();

        sb.append("\n=====================================================\n");
        if(!dm.documents.isEmpty()) {
            sb.append("DOCUMENTS:\n");
            for (Document d : dm.documents.values()) {
                sb.append("\t").append(i).append(") ").append(d.documentName).append(" ( ").append(d.cm.mi.Hash).append(" )\n");
                hashs.add(d.cm.mi.Hash);
                i++;
            }
            sb.append("\n");
        }

        if(!dm.messages.isEmpty()) {
            sb.append("MESSAGES:\n");
            for (String hash : dm.messages.keySet()) {
                sb.append("\t").append(i).append(") ").append(hash).append("\n");
                hashs.add(hash);
                i++;
            }
            sb.append("\n");
        }
        sb.append("0 OR ").append(i).append("+ TO CANCEL\n");
        sb.append("=====================================================\n");
        sb.append("CHOOSE INFO TO SEND:");
        System.out.print(sb.toString());

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String choiceString = "";
        int choice = -1;
        try {
            choiceString = reader.readLine();
            choice = Integer.parseInt(choiceString);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String infoHash = "";

        if(choice > 0 && choice <= hashs.size()){
            infoHash = hashs.get(choice-1);

            System.out.println("GIVE ME A IP TO SEND");

            reader = new BufferedReader(new InputStreamReader(System.in));
            String ip = "";
            InetAddress destIP = null;
            try {
                ip = reader.readLine();
                destIP = InetAddress.getByName(ip);
            } catch (IOException e) {
                e.printStackTrace();
            }

            sc.schedule(infoHash, destIP, destPort, true);
        }
    }


    private static KnockManager startKnockManager(ArrayList<NIC> nics) {

        NIC nic;

        System.out.println("CHOOSE A NETWORK INTERFACE ADDRESS");
        for(int i = 0 ; i < nics.size(); i++){
            nic = nics.get(i);
            System.out.println(i + ") " + nic.name);
            System.out.println("\tisWireless? " + nic.isWireless);
/*            System.out.println("\tSpeed " + nic.getSpeed()/1000 + " Mb/s");
            System.out.println("\tMTU " + nic.getMTU() + " Bytes");*/
            System.out.println("\tAddresses " + nic.addresses);
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

        KnockManager km = null;
        try {
            km = new KnockManager(nic, InetAddress.getByName("233.252.18.250"),6100,null);
        }
        catch (UnknownHostException e) {
            e.printStackTrace();
        }

        if(km != null)
            nic.registerKnockManager(km);

        return km;
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
        System.out.println(ftnfpath);
        File root = new File(ftnfpath);
        while(!root.exists() && !root.isDirectory() && !root.mkdir());

        System.out.println("CREATED FTNF");
        return ftnfpath;
    }

    private static void mountFile(DataManager dm){
        System.out.println(dm.documents.keySet());

        BufferedReader reader =
                new BufferedReader(new InputStreamReader(System.in));
        String choice = "";
        int i = 0;
        try {
            choice = reader.readLine();
            i = Integer.parseInt(choice) - 1;
        } catch (IOException e) {
            e.printStackTrace();
        }

        String docHash = new ArrayList<> (dm.documents.keySet()).get(i);
        System.out.println("MOUNTING ON " + System.getProperty("user.home") + "/Desktop/");
        dm.assembleDocument(docHash, System.getProperty("user.home") + "/Desktop/");
    }

    static void displayInterfaceInformation(NetworkInterface netint) throws SocketException {
        System.out.printf("Display name: %s\n", netint.getDisplayName());
        System.out.printf("Name: %s\n", netint.getName());
        Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();

        for (InetAddress inetAddress : Collections.list(inetAddresses)) {
            System.out.printf("InetAddress: %s\n", inetAddress);
            System.out.printf("isLocal?: %s\n", inetAddress.isLinkLocalAddress());
        }

        for (InterfaceAddress address : netint.getInterfaceAddresses()) {
            System.out.println("FULL ADDRESS => " + address);
        }

        System.out.printf("Up? %s\n", netint.isUp());
        System.out.printf("Loopback? %s\n", netint.isLoopback());
        System.out.printf("PointToPoint? %s\n", netint.isPointToPoint());
        System.out.printf("Supports multicast? %s\n", netint.supportsMulticast());
        System.out.printf("Virtual? %s\n", netint.isVirtual());
        System.out.printf("Hardware address: %s\n",
                Arrays.toString(netint.getHardwareAddress()));
        System.out.printf("MTU: %s\n", netint.getMTU());
        System.out.print("\n");
    }
}
