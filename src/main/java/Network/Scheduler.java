package Network;

import Data.ChunkManager;
import Data.ChunkManagerMetaInfo;
import Data.DataManager;
import Data.Document;
import Messages.*;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Scheduler {
    private final int nodeIdentifier;
    private DataManager dm;

    private SchedulerMetaInfo smi;

    private ArrayList<NIC> nics;

    private HashMap<String, ArrayList<SchedulerIPListener>> ipListeners_byNIC;

    private HashMap<Integer, TransferMultiSender> tms_byTransferID;
    private HashMap<String, ArrayList<Integer>> transferID_byNIC;

    private HashMap<Integer, DatagramPacket> transferMetaInfos;
    private HashMap<Integer, DatagramPacket> transferMetaInfos_LINKLOCAL;

    private ScheduledExecutorService tmiScheduler;

    private Random rand;

    public Scheduler(String Root, boolean fetch, DataManager dm) {
        SchedulerMetaInfo smi = null;
        Root = folderPathNormalizer(Root + "Network/");

        if (fetch) {
            smi = fetchSMI(Root);
        }

        if (smi == null) {
            createNetworkFolder(Root);
            this.smi = new SchedulerMetaInfo(Root);

            writeSchedulerMetaInfoFile();
        } else {
            this.smi = smi;
        }

        this.dm = dm;

        this.nics = new ArrayList<NIC>();
        this.ipListeners_byNIC = new HashMap<String, ArrayList<SchedulerIPListener>>();

        this.smi.scheduledTransmissions.putAll(this.smi.onGoingTransmissions);
        this.smi.onGoingTransmissions.clear();
        updateSchedulerMetaInfoFile();

        this.tms_byTransferID = new HashMap<Integer, TransferMultiSender>();
        this.transferID_byNIC = new HashMap<String, ArrayList<Integer>>();

        this.transferMetaInfos = new HashMap<Integer, DatagramPacket>();
        this.transferMetaInfos_LINKLOCAL = new HashMap<Integer, DatagramPacket>();

        this.tmiScheduler = Executors.newSingleThreadScheduledExecutor();
        this.tmiScheduler.scheduleWithFixedDelay(sendTMIs, 0, 3, TimeUnit.SECONDS);
        this.rand = new Random();
        this.nodeIdentifier = this.rand.nextInt();

        ChunkManagerMetaInfo cmmi;
        String docName = null;

        for (Transmission t : this.smi.scheduledTransmissions.values()) {
           createTMIDP(t);
        }
    }

    private void refreshAllNICs() {
        for (NIC nic : this.nics)
            refreshNICDS(nic);
    }

    private void refreshNICDS(NIC nic) {
        updateNICListeners(nic, nic.getAddresses());
    }

    public void registerNIC(NIC nic) {

        //CHANGE LOCKS

        this.nics.add(nic);
        this.transferID_byNIC.put(nic.name, new ArrayList<Integer>());
        refreshNICDS(nic);
    }

/*    public void changeNICAddresses(NIC nic, ArrayList<InetAddress> ips) {

        stopNICListeners(nic.name);

        for(InetAddress ip : ips){
            SchedulerIPListener schl = new SchedulerIPListener(this,nic,ip,5000); //CHANGE PORT
        }

        //CHANGE LOCKS

        ArrayList<Integer> transferIDs = this.tms_byNIC.get(nic.name);

        int numberOfTMS = transferIDs.size();
        int transferid;
        boolean isLINKLOCAL;
        for (int i = 0; i < numberOfTMS; i++) {
            transferid = transferIDs.get(i);
            isLINKLOCAL = this.smi.onGoingTransmissions.get(transferid).destIP.isLinkLocalAddress();
            this.tms_byTransferID.get(transferid).changeOwnIP(ips, isLINKLOCAL);
        }
    }*/

    public void changeNICConnectionStatus(String nicName, boolean hasConnection) {

        for(SchedulerIPListener schl : this.ipListeners_byNIC.get(nicName))
            schl.updateHasConnectionStatus(hasConnection);

        for(int transferID : this.transferID_byNIC.get(nicName))
            this.tms_byTransferID.get(transferID).updateConnectionStatus(hasConnection);
    }

    private void createNetworkFolder(String Root) {
        String path = folderPathNormalizer(Root);
        File root = new File(path);
        while (!root.exists() && !root.isDirectory() && !root.mkdir()) ;
    }

    private void createTMIDP(Transmission t){
        ChunkManagerMetaInfo cmmi;
        String docName;

        if (this.dm.messages.containsKey(t.infoHash)) {
            cmmi = this.dm.messages.get(t.infoHash).mi;
            docName = null;
        } else {
            Document d = this.dm.documents.get(t.infoHash);
            cmmi = d.cm.mi;
            docName = d.documentName;
        }

        TransferMetaInfo tmi = new TransferMetaInfo(this.nodeIdentifier, this.rand.nextInt(), cmmi, docName, t.confirmation);

        byte[] serializedData = getBytesFromObject(tmi);
        DatagramPacket dp = new DatagramPacket(serializedData, serializedData.length, t.destIP, t.destPort);

        if (t.destIP.isLinkLocalAddress())
            this.transferMetaInfos_LINKLOCAL.put(tmi.transferID, dp);
        else
            this.transferMetaInfos.put(tmi.transferID, dp);
    }

    private void removeTMIDP(Transmission t){
        if(t.destIP.isLinkLocalAddress())
            this.transferMetaInfos_LINKLOCAL.remove(t.transferID);
        else
            this.transferMetaInfos.remove(t.transferID);
    }

    private final Runnable sendTMIs = () -> {
        System.out.println("TRYING TO SEND TMIS");
        for(ArrayList<SchedulerIPListener> schls : this.ipListeners_byNIC.values()) {
            //System.out.println("AAA");
            for (SchedulerIPListener schl : schls) {
                //System.out.println("BBB");

                if (schl.isLinkLocal) {
                    //System.out.println("CCC111");
                    for (int transferID : this.transferMetaInfos_LINKLOCAL.keySet()) {
                        //System.out.println("DDD");
                        schl.sendDP(this.transferMetaInfos_LINKLOCAL.get(transferID));
                    }
                }
                else {
                    //System.out.println("CCC222");
                    for (int transferID : this.transferMetaInfos.keySet()) {
                        //System.out.println("DDD");
                        schl.sendDP(this.transferMetaInfos.get(transferID));
                    }
                }
            }
        }
    };

    /*
     * Schedules the transmission of the information based on its destination IP and priority
     * */
    public void schedule(String infoHash, InetAddress destIP, int destPort, boolean confirmation) {

        Transmission t = new Transmission(this.rand.nextInt(), infoHash, destIP, destPort, confirmation);

        this.smi.scheduledTransmissions.put(t.transferID, t);

        createTMIDP(t);

        updateSchedulerMetaInfoFile();
    }

    private void moveToOngoing(Transmission transmission) {
        Transmission t = this.smi.scheduledTransmissions.get(transmission.transferID);

        if (transmission.transferID == t.transferID && transmission.destIP.equals(t.destIP) && transmission.destPort == t.destPort) {
            this.smi.scheduledTransmissions.remove(transmission.transferID);
            this.smi.onGoingTransmissions.put(transmission.transferID, t);
            removeTMIDP(t);
        }

        updateSchedulerMetaInfoFile();
    }

    public void markAsInterrupted(int transferID) {
        Transmission t = this.smi.onGoingTransmissions.get(transferID);

        this.smi.onGoingTransmissions.remove(transferID);
        this.smi.scheduledTransmissions.put(transferID, t);
        createTMIDP(t);

        //CHANGE LOCKS

        ArrayList<Integer> ids;

        for (String nm : this.transferID_byNIC.keySet()) {
            ids = this.transferID_byNIC.get(nm);

            if (ids.contains(transferID)) {
                ids.remove(transferID);
                this.transferID_byNIC.remove(nm);
                this.transferID_byNIC.put(nm, ids);
                break;
            }
        }

        updateSchedulerMetaInfoFile();
    }

    public void markAsFinished(int transferID) {
        Transmission t = this.smi.onGoingTransmissions.get(transferID);

        this.smi.onGoingTransmissions.remove(transferID);
        this.smi.finishedTransmissions.put(transferID, t);

        updateSchedulerMetaInfoFile();
    }

    public void cancelTransmission(Transmission transmission) {
        Transmission t = this.smi.scheduledTransmissions.get(transmission.transferID);

        if (transmission.transferID == t.transferID && transmission.destIP.equals(t.destIP) && transmission.destPort == t.destPort) {
            this.smi.scheduledTransmissions.remove(transmission.transferID);
            removeTMIDP(t);
        }

        updateSchedulerMetaInfoFile();
    }

    public boolean isScheduled(int transferID) {
        return (this.smi.scheduledTransmissions.containsKey(transferID) && (this.transferMetaInfos.containsKey(transferID) || this.transferMetaInfos_LINKLOCAL.containsKey(transferID)));
    }

    public void updateNICListeners(NIC nic, ArrayList<InetAddress> ips){

        ArrayList<SchedulerIPListener> schl = new ArrayList<SchedulerIPListener>();

        SchedulerIPListener listener;
        Thread t;

        if(this.ipListeners_byNIC.containsKey(nic.name))
            stopNICListeners(nic.name);

        for(InetAddress ip : ips){
            listener = new SchedulerIPListener(this, nic, ip, 5000); //CHANGE PORT
            schl.add(listener);
            t = new Thread(listener);
            t.start();
        }

        this.ipListeners_byNIC.put(nic.name, schl);

        //UPDATE TRANSFERMULTISENDERs IP

        Transmission transm;
        for(int transferID : this.transferID_byNIC.get(nic.name)){
            transm = this.smi.onGoingTransmissions.get(transferID);
            this.tms_byTransferID.get(transferID).changeOwnIP(ips, transm.destIP.isLinkLocalAddress());
        }
    }

    public void stopNICListeners(String nicName){
        for(SchedulerIPListener schl : this.ipListeners_byNIC.get(nicName))
            schl.kill();

        this.ipListeners_byNIC.remove(nicName);
        this.ipListeners_byNIC.put(nicName, null);
    }

    /*private void startTMISENDERS(NIC nic){

        new Thread(() ->{
                //CHANGE LOCKS
                ArrayList<DatagramSocket> ds = this.nicSockets.get(nic.name);
                ArrayList<DatagramSocket> ds_LINKLOCAL = this.nicSockets_LINKLOCAL.get(nic.name);

                Transmission t;
                DatagramPacket dp;

                while((!this.transferMetaInfos.isEmpty() || !this.transferMetaInfos_LINKLOCAL.isEmpty()) && (!ds.isEmpty() || !ds_LINKLOCAL.isEmpty())) {

                    //SEND EXTERNAL IP
                    for (DatagramSocket d : ds) {
                        for (TransferMetaInfo tmi : this.transferMetaInfos.values()) {
                            tmi.firstLinkSpeed = nic.getSpeed();
                            tmi.isWireless = nic.isWireless;
                            //CHANGE LOCKS
                            t = this.smi.scheduledTransmissions.get(tmi.transferID);
                            byte[] data = getBytesFromObject(tmi);
                            dp = new DatagramPacket(data, data.length, t.destIP, t.destPort);

                            if (!d.isClosed()) {
                                try {
                                    d.send(dp);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                    //SEND INTERNAL IP
                    for (DatagramSocket d : ds_LINKLOCAL) {
                        for (TransferMetaInfo tmi : this.transferMetaInfos_LINKLOCAL.values()) {
                            tmi.firstLinkSpeed = nic.getSpeed();
                            tmi.isWireless = nic.isWireless;
                            //CHANGE LOCKS
                            t = this.smi.scheduledTransmissions.get(tmi.transferID);
                            byte[] data = getBytesFromObject(tmi);
                            dp = new DatagramPacket(data, data.length, t.destIP, t.destPort);

                            if (!d.isClosed()) {
                                try {
                                    d.send(dp);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
            ds = this.nicSockets.get(nic.name);
            ds_LINKLOCAL = this.nicSockets_LINKLOCAL.get(nic.name);
        });
    }*/

    public void processReceivedDP(DatagramPacket dp, NIC nic, InetAddress ip, int port){

        Object obj = getObjectFromBytes(dp.getData());

        InetAddress destIP = dp.getAddress();
        int destPort = dp.getPort();

        if(obj instanceof TransferMultiReceiverInfo){
            long receivingTime = System.currentTimeMillis();
            TransferMultiReceiverInfo tmri = (TransferMultiReceiverInfo) obj;
            ChunkManager cm;

            Transmission t = this.smi.scheduledTransmissions.get(tmri.transferID);
            moveToOngoing(t);

            if(this.dm.documents.containsKey(t.infoHash)){
                Document d = this.dm.documents.get(t.infoHash);
                cm = d.cm;
            }
            else {
                cm = this.dm.messages.get(t.infoHash);
            }

            TransferMultiSender tms = new TransferMultiSender(this, this.nodeIdentifier, tmri.transferID, nic.name, ip, port, destIP , destPort, cm, true);
            /*nic.registerNewTMSListener(tms);
             */
            //CHANGE LOCKS
            ArrayList<Integer> tmsID = this.transferID_byNIC.get(nic.name);

            tmsID.add(tmri.transferID);
            this.transferID_byNIC.remove(nic.name);
            this.transferID_byNIC.put(nic.name, tmsID);

            this.tms_byTransferID.put(tmri.transferID, tms);

            tms.processTransferMultiReceiverInfo(tmri, receivingTime);

        }
        else{
            if(obj instanceof MissingChunkIDs){
                MissingChunkIDs mcid = (MissingChunkIDs) obj;

                TransferMultiSender tms = this.tms_byTransferID.get(mcid.transferID);

                tms.processMissingChunkIDs(mcid);
            }
            else{
                if(obj instanceof IPChange){
                    IPChange ipc = (IPChange) obj;

                    TransferMultiSender tms = this.tms_byTransferID.get(ipc.transferID);

                    tms.processIPChange(ipc, dp.getAddress());
                }
                else{
                    if(obj instanceof Over){
                        Over over = (Over) obj;

                        TransferMultiSender tms = this.tms_byTransferID.get(over.transferID);

                        tms.processOver(over);

                        Transmission t = this.smi.onGoingTransmissions.get(over.transferID);
                        markAsFinished(t.transferID);

                        //CHANGE LOCKS
                        ArrayList<Integer> tmsID = this.transferID_byNIC.get(nic.name);

                        tmsID.remove(over.transferID);
                        this.transferID_byNIC.remove(nic.name);
                        this.transferID_byNIC.put(nic.name, tmsID);

                        this.tms_byTransferID.remove(over.transferID);
                    }
                }
            }
        }
    }

    public void sendDP(String nicName, int transferID, InetAddress myIP, int myPort, DatagramPacket dp){
        if(this.transferID_byNIC.get(nicName).contains(transferID)){
            for(SchedulerIPListener schl : this.ipListeners_byNIC.get(nicName)){
                if(schl.ip.equals(myIP) && schl.port == myPort){
                    schl.sendDP(dp);
                    break;
                }
            }
        }
    }
    /*
     * Writes the SchedulerMetaInfo to a Folder
     * */
    private void writeSchedulerMetaInfoFile(){
        String schedulerMetaInfoFilePath = this.smi.Root + "SchedulerMeta.info";

        File smiInfo = new File(schedulerMetaInfoFilePath);


        FileOutputStream fileOut = null;
        try {
            fileOut = new FileOutputStream(smiInfo);
            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
            objectOut.writeObject(this.smi);
            objectOut.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * Reads and retrieves an Object from the root folder that corresponds to a SchedulerMetaInfo
     * */
    private Object readSchedulerMetaInfoFile(String Root){
        String schedulerMetaInfoFilePath = Root + "SchedulerMeta.info";

        File FileInfo = new File(schedulerMetaInfoFilePath);
        Object obj = null;

        if(FileInfo.exists()) {
            FileInputStream fileIn = null;

            try {
                fileIn = new FileInputStream(schedulerMetaInfoFilePath);
                ObjectInputStream objectIn = new ObjectInputStream(fileIn);

                obj = objectIn.readObject();

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return obj;
    }

    /*
     * Deletes the current SchedulerMetaInfo File in root/ and writes the new SchedulerMetaInfo to the same path
     */
    private void updateSchedulerMetaInfoFile(){
        String schedulerMetaInfoFilePath = this.smi.Root + "SchedulerMeta.info";

        File FileInfo = new File(this.smi.Root);

        if(FileInfo.exists()) {
            File schedulerMetaInfo = new File(schedulerMetaInfoFilePath);
            while(!schedulerMetaInfo.delete());

            writeSchedulerMetaInfoFile();
        }
    }

    /*
     * Fetches and retrieves a SchedulerMetaInfo object from the root/ if present
     * */
    private SchedulerMetaInfo fetchSMI(String Root){
        Object obj = null;

        String schedulerInfoFilePath = Root + "SchedulerMeta.info";

        File schedulerMetaInfoFile = new File(schedulerInfoFilePath);

        if (schedulerMetaInfoFile.exists()) {
            obj = readSchedulerMetaInfoFile(Root);
        }

        return  (SchedulerMetaInfo) obj;
    }

    /*
     * Normalizes the given path
     * */
    private String folderPathNormalizer(String path){

        path = path + '/';
        int charIndex;

        while((charIndex = path.indexOf("//")) != -1)
            path = path.substring(0, charIndex) + path.substring(charIndex+1);


        return path;
    }

    public boolean hasTMIToSend(){
        boolean res;

        //CHANGE LOCKS
        res = !this.transferMetaInfos.isEmpty() || !this.transferMetaInfos_LINKLOCAL.isEmpty();

        return res;
    }

    public void printSchedule(){
        System.out.println("SCHEDULED TRANSMISSIONS:");
        for(Transmission t : this.smi.scheduledTransmissions.values()){
            System.out.println("\tTRANSFERID => " + t.transferID);
            System.out.println("\tDestIP => " + t.destIP);
            System.out.println("\tDestPort => " + t.destPort);
        }

        System.out.println("ON GOING TRANSMISSIONS:");
        for(Transmission t : this.smi.onGoingTransmissions.values()){
            System.out.println("\tTRANSFERID => " + t.transferID);
            System.out.println("\tDestIP => " + t.destIP);
            System.out.println("\tDestPort => " + t.destPort);
        }

        System.out.println("FINISHED TRANSMISSIONS:");
        for(Transmission t : this.smi.finishedTransmissions.values()){
            System.out.println("\tTRANSFERID => " + t.transferID);
            System.out.println("\tDestIP => " + t.destIP);
            System.out.println("\tDestPort => " + t.destPort);
        }
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