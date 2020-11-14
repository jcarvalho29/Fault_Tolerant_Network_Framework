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
import java.util.concurrent.locks.ReentrantLock;

public class Scheduler {
    private final int nodeIdentifier;
    private DataManager dm;

    private ReentrantLock smi_Lock;
    private SchedulerMetaInfo smi;

    private ReentrantLock nics_Lock;
    private ArrayList<NIC> nics;

    private ReentrantLock ipListeners_Lock;
    private HashMap<String, ArrayList<SchedulerIPListener>> ipListeners_byNIC;


    private ReentrantLock tms_Lock;
    private HashMap<String, ArrayList<Integer>> transferID_byNIC;
    private HashMap<Integer, TransferMultiSender> tms_byTransferID;

    private ReentrantLock datagramPackets_Lock;
    private ReentrantLock datagramPackets_LINKLOCAL_Lock;
    private HashMap<Integer, DatagramPacket> transferMetaInfos;
    private HashMap<Integer, DatagramPacket> transferMetaInfos_LINKLOCAL;

    private ScheduledExecutorService tmiScheduler;

    private Random rand;

    public Scheduler(String Root, boolean fetch, DataManager dm) {
        SchedulerMetaInfo smi = null;
        Root = folderPathNormalizer(Root + "Network/");

        this.smi_Lock = new ReentrantLock();

        if (fetch) {
            smi = fetchSMI(Root);
        }

        if (smi == null) {
            createNetworkFolder(Root);
            this.smi_Lock.lock();
            this.smi = new SchedulerMetaInfo(Root);
            this.smi_Lock.unlock();

            writeSchedulerMetaInfoFile();
        }
        else {
            this.smi_Lock.lock();
            this.smi = smi;
            this.smi_Lock.unlock();
        }

        this.dm = dm;

        this.nics_Lock = new ReentrantLock();
        this.nics = new ArrayList<NIC>();

        this.ipListeners_Lock = new ReentrantLock();
        this.ipListeners_byNIC = new HashMap<String, ArrayList<SchedulerIPListener>>();
        //this.ipListeners_LINKLOCAL_byNIC = new HashMap<String, ArrayList<SchedulerIPListener>>();


        this.smi_Lock.lock();
        this.smi.scheduledTransmissions.putAll(this.smi.onGoingTransmissions);
        this.smi.onGoingTransmissions.clear();
        this.smi_Lock.unlock();
        updateSchedulerMetaInfoFile();

        this.tms_Lock = new ReentrantLock();
        this.transferID_byNIC = new HashMap<String, ArrayList<Integer>>();
        this.tms_byTransferID = new HashMap<Integer, TransferMultiSender>();

        this.datagramPackets_Lock = new ReentrantLock();
        this.datagramPackets_LINKLOCAL_Lock = new ReentrantLock();
        this.transferMetaInfos = new HashMap<Integer, DatagramPacket>();
        this.transferMetaInfos_LINKLOCAL = new HashMap<Integer, DatagramPacket>();

        this.tmiScheduler = Executors.newSingleThreadScheduledExecutor();
        this.tmiScheduler.scheduleWithFixedDelay(sendTMIs, 0, 3, TimeUnit.SECONDS);
        this.rand = new Random();
        this.nodeIdentifier = this.rand.nextInt();

        ChunkManagerMetaInfo cmmi;
        String docName = null;

        this.smi_Lock.lock();
        for (Transmission t : this.smi.scheduledTransmissions.values())
           createTMIDP(t);
        this.smi_Lock.unlock();
        System.out.println("=====================================> CREATED ALL TMI");
    }

/*    private void refreshAllNICs() {
        this.nics_Lock.lock();

        int numberOfNICS = this.nics.size();
        NIC nic;

        for(int i = 0; i < numberOfNICS; i++){
            nic = this.nics.get(i);
            this.nics_Lock.unlock();
            refreshNICDS(nic);
            this.nics_Lock.lock();
        }
        this.nics_Lock.unlock();

        *//*for (NIC nic : this.nics)
            refreshNICDS(nic);*//*
    }*/
/*
    ???
    private void refreshNICDS(NIC nic) {
        changeNICListenersIP(nic, nic.getAddresses());
    }*/

    public void registerNIC(NIC nic) {

        this.nics_Lock.lock();
        this.nics.add(nic);
        this.nics_Lock.unlock();

        this.tms_Lock.lock();
        this.transferID_byNIC.put(nic.name, new ArrayList<Integer>());
        this.tms_Lock.unlock();

        this.smi_Lock.lock();
        if(this.smi.scheduledTransmissions.size() != 0)
            startupNICIPListeners(nic);

        this.smi_Lock.unlock();

        System.out.println("===============================> REGISTERED NIC");
    }

/*    public void changeNICAddresses(NIC nic, ArrayList<InetAddress> ips) {

        stopNICListeners(nic.name);

        for(InetAddress ip : ips){
            SchedulerIPListener schl = new SchedulerIPListener(this,nic,ip);
        }

        //CHANGE LOCKS??

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

        TransferMetaInfo tmi = new TransferMetaInfo(this.nodeIdentifier, t.transferID , cmmi, docName, t.confirmation);

        byte[] serializedData = getBytesFromObject(tmi);
        DatagramPacket dp = new DatagramPacket(serializedData, serializedData.length, t.destIP, t.destPort);

        if (t.destIP.isLinkLocalAddress()) {
            this.datagramPackets_LINKLOCAL_Lock.lock();
            this.transferMetaInfos_LINKLOCAL.put(tmi.transferID, dp);
            this.datagramPackets_LINKLOCAL_Lock.unlock();
        }
        else {
            this.datagramPackets_Lock.lock();
            this.transferMetaInfos.put(tmi.transferID, dp);
            this.datagramPackets_Lock.unlock();
        }
    }

    private void removeTMIDP(Transmission t){
        if(t.destIP.isLinkLocalAddress())
            this.transferMetaInfos_LINKLOCAL.remove(t.transferID);
        else
            this.transferMetaInfos.remove(t.transferID);
    }

    public void processReceivedDP(DatagramPacket dp, NIC nic, InetAddress ip, int port){

        Object obj = getObjectFromBytes(dp.getData());

        InetAddress destIP = dp.getAddress();
        int destPort = dp.getPort();

        if(obj instanceof TransferMultiReceiverInfo) {
            long receivingTime = System.currentTimeMillis();
            TransferMultiReceiverInfo tmri = (TransferMultiReceiverInfo) obj;

            //POR CAUSA DE RESPOSTAS CONCORRENTES DE 2 OU MAIS NICS
            this.tms_Lock.lock();
            if (this.tms_byTransferID.containsKey(tmri.transferID)){

                this.smi_Lock.lock();
                Transmission t = this.smi.scheduledTransmissions.get(tmri.transferID);
                this.smi_Lock.unlock();

                ChunkManager cm;
                if (this.dm.documents.containsKey(t.infoHash)) {
                    Document d = this.dm.documents.get(t.infoHash);
                    cm = d.cm;
                }
                else {
                    cm = this.dm.messages.get(t.infoHash);
                }

                TransferMultiSender tms = new TransferMultiSender(this, this.nodeIdentifier, tmri.transferID, nic.name, ip, port, destIP, destPort, cm, true);

                ArrayList<Integer> tmsID = this.transferID_byNIC.get(nic.name);

                tmsID.add(tmri.transferID);
                this.transferID_byNIC.remove(nic.name);
                this.transferID_byNIC.put(nic.name, tmsID);

                this.tms_byTransferID.put(tmri.transferID, tms);
                this.tms_Lock.unlock();

                moveToOngoing(t);
                tms.processTransferMultiReceiverInfo(tmri, receivingTime);
            }
            this.tms_Lock.unlock();

        }
        else{
            if(obj instanceof MissingChunkIDs){
                MissingChunkIDs mcid = (MissingChunkIDs) obj;

                this.tms_Lock.lock();
                TransferMultiSender tms = this.tms_byTransferID.get(mcid.transferID);
                this.tms_Lock.unlock();

                tms.processMissingChunkIDs(mcid);
            }
            else{
                if(obj instanceof IPChange){
                    IPChange ipc = (IPChange) obj;

                    this.tms_Lock.lock();
                    TransferMultiSender tms = this.tms_byTransferID.get(ipc.transferID);
                    this.tms_Lock.unlock();

                    tms.processIPChange(ipc, dp.getAddress());
                }
                else{
                    if(obj instanceof Over){
                        Over over = (Over) obj;

                        this.tms_Lock.lock();
                        TransferMultiSender tms = this.tms_byTransferID.get(over.transferID);
                        this.tms_Lock.unlock();

                        if(tms != null)
                            tms.processOver(over);
                        else{
                            processEarlyOver(over, ip.isLinkLocalAddress());
                        }
                    }
                }
            }
        }
    }

    private void processEarlyOver(Over over, boolean isLinkLocal){
        Transmission t;
        boolean schdT = false;

        this.smi_Lock.lock();
        if(this.smi.scheduledTransmissions.containsKey(over.transferID)) {
            t = this.smi.scheduledTransmissions.get(over.transferID);
            this.smi.scheduledTransmissions.remove(over.transferID);
            this.smi.finishedTransmissions.put(over.transferID, t);

            schdT = this.smi.scheduledTransmissions.isEmpty();
        }
        this.smi_Lock.unlock();

        if(isLinkLocal){
            this.datagramPackets_LINKLOCAL_Lock.lock();
            this.transferMetaInfos_LINKLOCAL.remove(over.transferID);
            this.datagramPackets_LINKLOCAL_Lock.unlock();
        }
        else{
            this.datagramPackets_Lock.lock();
            this.transferMetaInfos.remove(over.transferID);
            this.datagramPackets_Lock.unlock();
        }

        if(schdT)
            checkIPListeners();

        printSchedule();
        updateSchedulerMetaInfoFile();
    }

    public void sendDP(String nicName, int transferID, InetAddress myIP, int myPort, DatagramPacket dp){
        this.tms_Lock.lock();
        boolean res = this.transferID_byNIC.get(nicName).contains(transferID);
        this.tms_Lock.unlock();

        if(res){
            for (SchedulerIPListener schl : this.ipListeners_byNIC.get(nicName)) {
                if (schl.ip.equals(myIP) && schl.port == myPort) {
                    schl.sendDP(dp);
                    break;
                }
            }
        }
    }

    private final Runnable sendTMIs = () -> {

        this.datagramPackets_Lock.lock();
        this.datagramPackets_LINKLOCAL_Lock.lock();

        if(!this.transferMetaInfos.values().isEmpty() || !this.transferMetaInfos_LINKLOCAL.values().isEmpty()) {
            System.out.println("TRYING TO SEND TMIS");
            //NON LINKLOCAL
            for (ArrayList<SchedulerIPListener> schls : this.ipListeners_byNIC.values()) {
                //System.out.println("AAA");
                for (SchedulerIPListener schl : schls) {
                    //System.out.println("BBB");

                    if(schl.isLinkLocal) {
                        for (int transferID : this.transferMetaInfos_LINKLOCAL.keySet()) {
                            //System.out.println("CCC111");
                            schl.sendDP(this.transferMetaInfos_LINKLOCAL.get(transferID));
                        }
                    }
                    else {
                        for (int transferID : this.transferMetaInfos.keySet()) {
                            //System.out.println("CCC222");
                            schl.sendDP(this.transferMetaInfos.get(transferID));
                        }
                    }
                }
            }
        }

        this.datagramPackets_Lock.unlock();
        this.datagramPackets_LINKLOCAL_Lock.unlock();
    };

    public void changeNICListenersIP(NIC nic, ArrayList<InetAddress> ips){
        deleteNICListeners(nic.name);

        this.smi_Lock.lock();
        this.tms_Lock.lock();
        if(this.smi.scheduledTransmissions.size() != 0 || this.transferID_byNIC.get(nic.name).size() != 0)
            startupNICIPListeners(nic);
        else
            nic.markSchedulerAsInactive();

        this.smi_Lock.unlock();
        this.tms_Lock.unlock();

        //UPDATE TRANSFERMULTISENDERs IP

        Transmission transm;
        TransferMultiSender tms;

        this.tms_Lock.lock();
        ArrayList <Integer> transferIDs = this.transferID_byNIC.get(nic.name);
        this.tms_Lock.unlock();

        int numberOfTransferIDs = transferIDs.size();
        int transferID;

        for(int i = 0; i < numberOfTransferIDs; i++){
            transferID = transferIDs.get(i);
            this.smi_Lock.lock();
            transm = this.smi.onGoingTransmissions.get(transferID);
            this.smi_Lock.unlock();

            this.tms_Lock.lock();
            tms = this.tms_byTransferID.get(transferID);
            this.tms_Lock.unlock();

            tms.changeOwnIP(ips, transm.destIP.isLinkLocalAddress());
        }
    }

    public void changeNICConnectionStatus(NIC nic, boolean hasConnection) {

        this.smi_Lock.lock();
        this.tms_Lock.lock();
        boolean isScheduled = this.smi.scheduledTransmissions.size() != 0 || this.transferID_byNIC.get(nic.name).size() != 0;
        this.smi_Lock.unlock();
        this.tms_Lock.unlock();

        if(hasConnection && isScheduled)
            startupNICIPListeners(nic);
        else
            if(!hasConnection)
                deleteNICListeners(nic.name);

        TransferMultiSender tms;

        this.tms_Lock.lock();
        for(int transferID : this.transferID_byNIC.get(nic.name)) {
            tms = this.tms_byTransferID.get(transferID);
            this.tms_Lock.unlock();
            tms.updateConnectionStatus(hasConnection);
            this.tms_Lock.lock();
        }
        this.tms_Lock.unlock();
    }

    private void createNICIPListeners(NIC nic){
        ArrayList<SchedulerIPListener> schl = new ArrayList<SchedulerIPListener>();
        ArrayList<InetAddress> ips = nic.getAddresses();
        System.out.println(ips);
        for(InetAddress ip : ips) {
            schl.add(new SchedulerIPListener(this, nic, ip));
        }
        this.ipListeners_byNIC.put(nic.name, schl);
    }

    private void createNICIPListeners_LINKLOCAL(NIC nic){
        ArrayList<SchedulerIPListener> schl = new ArrayList<SchedulerIPListener>();
        ArrayList<InetAddress> ips = nic.getLinkLocalAddresses();

        for(InetAddress ip : ips)
            schl.add(new SchedulerIPListener(this, nic, ip));

        this.ipListeners_byNIC.put(nic.name, schl);
        //this.ipListeners_LINKLOCAL_byNIC.put(nic.name, schl);
    }

    private void startupNICIPListeners(NIC nic){
        System.out.println("                STARTUPNICIPLISTENERS (" + nic.name + ")");
        nic.markSchedulerAsActive();
        ArrayList<InetAddress> ips = new ArrayList<InetAddress>();

        //CAHNGE LOCKS
        if(nic.checkConnectionStatus()) {
            //NON LINKLOCAL
                if (!this.ipListeners_byNIC.containsKey(nic.name))
                    createNICIPListeners(nic);

                for (SchedulerIPListener schl : this.ipListeners_byNIC.get(nic.name)) {
                    if (!schl.isRunning)
                        new Thread(schl).start();
                }

/*            //LINKLOCAL
            if (this.transferMetaInfos_LINKLOCAL.size() != 0) {
                if (!this.ipListeners_LINKLOCAL_byNIC.containsKey(nic.name))
                    createNICIPListeners_LINKLOCAL(nic);

                for (SchedulerIPListener schl : this.ipListeners_LINKLOCAL_byNIC.get(nic.name)) {
                    if (!schl.isRunning)
                        new Thread(schl).start();
                }
            }*/
        }
    }

    private void deleteNICListeners(String nicName){

        //CAHNGE LOCKS
        System.out.println("DELETE ALL " + nicName + " SCHEDULERIPLISTENERS");
        if(this.ipListeners_byNIC.containsKey(nicName)) {
            for (SchedulerIPListener schl : this.ipListeners_byNIC.get(nicName))
                schl.kill();
            this.ipListeners_byNIC.remove(nicName);
        }

/*        if(this.ipListeners_LINKLOCAL_byNIC.containsKey(nicName)) {
            for (SchedulerIPListener schl : this.ipListeners_LINKLOCAL_byNIC.get(nicName))
                schl.kill();
            this.ipListeners_LINKLOCAL_byNIC.remove(nicName);
        }*/

    }
    private void checkIPListeners(){

        for(NIC nic : this.nics){
            this.tms_Lock.lock();
            if(this.transferID_byNIC.get(nic.name).size() == 0) {
                this.tms_Lock.unlock();
                nic.markSchedulerAsInactive();
                deleteNICListeners(nic.name);
            }
            else
                this.tms_Lock.unlock();
        }
    }

    /*
     * Schedules the transmission of the information based on its destination IP and priority
     * */
    public void schedule(String infoHash, InetAddress destIP, int destPort, boolean confirmation) {

        Transmission t = new Transmission(this.rand.nextInt(), infoHash, destIP, destPort, confirmation);

        this.smi_Lock.lock();
        this.smi.scheduledTransmissions.put(t.transferID, t);
        this.smi_Lock.unlock();

        createTMIDP(t);

        for(NIC nic : this.nics)
            startupNICIPListeners(nic);

        printSchedule();

        updateSchedulerMetaInfoFile();
    }

    private void moveToOngoing(Transmission transmission) {

        this.smi_Lock.lock();
        this.smi.scheduledTransmissions.remove(transmission.transferID);
        this.smi.onGoingTransmissions.put(transmission.transferID, transmission);
        int numberOfSchdT = this.smi.scheduledTransmissions.size();
        this.smi_Lock.unlock();

        if(numberOfSchdT == 0) {   //inactive
            System.out.println("FOI O MOVE TO ON GOING");
            checkIPListeners();
        }

        removeTMIDP(transmission);

        printSchedule();
        updateSchedulerMetaInfoFile();
    }

    public void markAsInterrupted(String nicName, int transferID) {

        this.smi_Lock.lock();
        Transmission t = this.smi.onGoingTransmissions.get(transferID);
        this.smi.onGoingTransmissions.remove(transferID);
        this.smi.scheduledTransmissions.put(transferID, t);
        this.smi_Lock.unlock();

        createTMIDP(t);

        for(NIC nic : this.nics)
            startupNICIPListeners(nic);

        this.tms_Lock.lock();
        ArrayList<Integer> tmsIDs = this.transferID_byNIC.get(nicName);

        if(tmsIDs.contains(transferID)) {
            tmsIDs.remove(transferID);
            this.transferID_byNIC.remove(nicName);
            this.transferID_byNIC.put(nicName, tmsIDs);

            this.tms_byTransferID.remove(transferID);
        }

        this.tms_Lock.unlock();


        printSchedule();
        updateSchedulerMetaInfoFile();
    }

    public void markAsFinished(String nicName, int transferID) {
        this.smi_Lock.lock();

        Transmission t = this.smi.onGoingTransmissions.get(transferID);
        this.smi.onGoingTransmissions.remove(transferID);
        this.smi.finishedTransmissions.put(transferID, t);

        int numberofSchT = this.smi.scheduledTransmissions.size();

        this.smi_Lock.unlock();

        this.tms_Lock.lock();
        ArrayList<Integer> tmsIDs = this.transferID_byNIC.get(nicName);

        if(tmsIDs.contains(transferID)) {
            tmsIDs.remove((Object)transferID);
            this.transferID_byNIC.remove(nicName);
            this.transferID_byNIC.put(nicName, tmsIDs);

            if(numberofSchT == 0)
                checkIPListeners();

            this.tms_byTransferID.remove(transferID);
        }

        this.tms_Lock.unlock();

        printSchedule();
        updateSchedulerMetaInfoFile();
    }

    public void cancelTransmission(Transmission transmission) {

        this.smi_Lock.lock();
        this.smi.scheduledTransmissions.remove(transmission.transferID);
        int numberOfSchldT = this.smi.scheduledTransmissions.size();
        this.smi_Lock.unlock();

        if(numberOfSchldT == 0)
            checkIPListeners();

        removeTMIDP(transmission);

        printSchedule();
        updateSchedulerMetaInfoFile();
    }

    public boolean isScheduled(int transferID) {

        this.smi_Lock.lock();
        this.datagramPackets_Lock.lock();
        this.datagramPackets_LINKLOCAL_Lock.lock();

        boolean res = this.smi.scheduledTransmissions.containsKey(transferID) && (this.transferMetaInfos.containsKey(transferID) || this.transferMetaInfos_LINKLOCAL.containsKey(transferID));

        this.smi_Lock.unlock();
        this.datagramPackets_Lock.unlock();
        this.datagramPackets_LINKLOCAL_Lock.unlock();

        return res;
    }

    /*
     * Writes the SchedulerMetaInfo to a Folder
     * */
    private void writeSchedulerMetaInfoFile(){
        this.smi_Lock.lock();
        String schedulerMetaInfoFilePath = this.smi.Root + "SchedulerMeta.info";
        this.smi_Lock.unlock();

        File smiInfo = new File(schedulerMetaInfoFilePath);


        FileOutputStream fileOut = null;
        try {
            fileOut = new FileOutputStream(smiInfo);
            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
            this.smi_Lock.lock();
            objectOut.writeObject(this.smi);
            this.smi_Lock.unlock();
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
        this.smi_Lock.lock();
        String schedulerMetaInfoFilePath = this.smi.Root + "SchedulerMeta.info";
        File FileInfo = new File(this.smi.Root);
        this.smi_Lock.unlock();

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

    private void createNetworkFolder(String Root) {
        String path = folderPathNormalizer(Root);
        File root = new File(path);
        while (!root.exists() && !root.isDirectory() && !root.mkdir()) ;
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

        this.datagramPackets_Lock.lock();
        this.datagramPackets_LINKLOCAL_Lock.lock();

        boolean res = !this.transferMetaInfos.isEmpty() || !this.transferMetaInfos_LINKLOCAL.isEmpty();

        this.datagramPackets_Lock.unlock();
        this.datagramPackets_LINKLOCAL_Lock.unlock();
        return res;
    }

    public void printSchedule(){
        this.smi_Lock.lock();
        System.out.println("\n================================================================================");
        System.out.println("SCHEDULED TRANSMISSIONS:");
        for(Transmission t : this.smi.scheduledTransmissions.values()){
            System.out.println("\tTRANSFERID => " + t.transferID);
            System.out.println("\tDestIP => " + t.destIP);
            System.out.println("\tDestPort => " + t.destPort + "\n");
        }
        System.out.println("----------------------------------------------------------------------------------");
        System.out.println("ON GOING TRANSMISSIONS:");
        for(Transmission t : this.smi.onGoingTransmissions.values()){
            System.out.println("\tTRANSFERID => " + t.transferID);
            System.out.println("\tDestIP => " + t.destIP);
            System.out.println("\tDestPort => " + t.destPort + "\n");
        }
        System.out.println("----------------------------------------------------------------------------------");
        System.out.println("FINISHED TRANSMISSIONS:");
        for(Transmission t : this.smi.finishedTransmissions.values()){
            System.out.println("\tTRANSFERID => " + t.transferID);
            System.out.println("\tDestIP => " + t.destIP);
            System.out.println("\tDestPort => " + t.destPort + "\n");
        }
        System.out.println("================================================================================\n");
        this.smi_Lock.unlock();
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