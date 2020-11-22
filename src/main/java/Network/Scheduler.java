package Network;

import Data.ChunkManager;
import Data.ChunkManagerMetaInfo;
import Data.DataManager;
import Data.Document;
import Messages.*;

import java.io.*;
import java.net.*;
import java.util.*;
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
    private HashMap<String, NIC> nics;

    private ReentrantLock ipListeners_Lock;
    private HashMap<String, SchedulerIPListener> ipListeners_byNIC;
    private HashMap<String, SchedulerIPListener> ipListeners_LINKLOCAL_byNIC;

    private ReentrantLock tms_Lock;
    private HashMap<String, ArrayList<Integer>> transferID_byNIC;
    private HashMap<Integer, TransferMultiSender> tms_byTransferID;

    private ReentrantLock datagramPackets_Lock;
    private ReentrantLock datagramPackets_LINKLOCAL_Lock;
    private HashMap<Integer, TransferMetaInfo> transferMetaInfos;
    private HashMap<Integer, TransferMetaInfo> transferMetaInfos_LINKLOCAL;

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
        this.nics = new HashMap<String, NIC>();

        this.ipListeners_Lock = new ReentrantLock();
        this.ipListeners_byNIC = new HashMap<String, SchedulerIPListener>();
        this.ipListeners_LINKLOCAL_byNIC = new HashMap<String, SchedulerIPListener>();


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
        this.transferMetaInfos = new HashMap<Integer, TransferMetaInfo>();
        this.transferMetaInfos_LINKLOCAL = new HashMap<Integer, TransferMetaInfo>();

        this.tmiScheduler = Executors.newSingleThreadScheduledExecutor();
        this.tmiScheduler.scheduleWithFixedDelay(sendTMIs, 0, 3, TimeUnit.SECONDS);
        this.rand = new Random();
        this.nodeIdentifier = this.rand.nextInt();

        this.smi_Lock.lock();
        for (Transmission t : this.smi.scheduledTransmissions.values())
           createTMIDP(t);
        this.smi_Lock.unlock();
    }

    public void registerNIC(NIC nic) {

        this.nics_Lock.lock();
        this.nics.put(nic.name, nic);
        this.nics_Lock.unlock();

        this.tms_Lock.lock();
        this.transferID_byNIC.put(nic.name, new ArrayList<Integer>());
        this.tms_Lock.unlock();

        this.smi_Lock.lock();
        this.smi.scheduledTransmissions.keySet().forEach(transferID -> nic.registerTransferSchedule());

        SchedulerIPListener sch1 = createNICIPListeners(nic);
        SchedulerIPListener sch2 = createNICIPListeners_LINKLOCAL(nic);
        if(this.smi.scheduledTransmissions.size() != 0) {

            if(!this.transferMetaInfos.isEmpty()) {
                sch1.reactivate();
            }
            if(!this.transferMetaInfos_LINKLOCAL.isEmpty()) {
                sch2.reactivate();
            }
            nic.markSchedulerAsActive();
        }
        else
            nic.markSchedulerAsInactive();
        this.smi_Lock.unlock();

        System.out.println("(SCHEDULER) REGISTERED NIC");
    }

    private SchedulerIPListener createNICIPListeners(NIC nic){
        InetAddress ip = null;
        SchedulerIPListener sch;
        ArrayList<InetAddress> ips = nic.getNONLinkLocalAddresses();
        if(!ips.isEmpty())
            ip = ips.get(0);
        sch = new SchedulerIPListener(this, nic, ip, false);

        this.ipListeners_byNIC.put(nic.name, sch);
        return sch;
    }

    private SchedulerIPListener createNICIPListeners_LINKLOCAL(NIC nic){
        InetAddress ip = null;
        SchedulerIPListener sch = null;
        ArrayList<InetAddress> ips = nic.getLinkLocalAddresses();
        if(!ips.isEmpty())
            ip = ips.get(0);
        sch = new SchedulerIPListener(this, nic, ip, true);

        this.ipListeners_LINKLOCAL_byNIC.put(nic.name, sch);
        return sch;
    }

    private void createTMIDP(Transmission t){
        System.out.println("CREATING TMI");
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

        cmmi.missingChunks = null;
        TransferMetaInfo tmi = new TransferMetaInfo(this.nodeIdentifier, t.transferID, cmmi, docName, t.confirmation);
        System.out.println("GOT NEW TMI");
        /*byte[] serializedData = getBytesFromObject(tmi);
        DatagramPacket dp = new DatagramPacket(serializedData, serializedData.length, t.destIP, t.destPort);*/

        if (t.destIP.isLinkLocalAddress()) {
            this.datagramPackets_LINKLOCAL_Lock.lock();
            System.out.println("ADDED TO LINKLOCAL TMIS");
            this.transferMetaInfos_LINKLOCAL.put(tmi.transferID, tmi);
            this.datagramPackets_LINKLOCAL_Lock.unlock();
        }
        else {
            this.datagramPackets_Lock.lock();
            System.out.println("ADDED TO TMIS");
            this.transferMetaInfos.put(tmi.transferID, tmi);
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

        System.out.println("PROCESS RECEIVED DP");
        if(obj instanceof TransferMultiReceiverInfo) {
            System.out.println("    IT'S A TRANSFERMULTIRECEIVERINFO");
            long receivingTime = System.currentTimeMillis();
            TransferMultiReceiverInfo tmri = (TransferMultiReceiverInfo) obj;

            //POR CAUSA DE RESPOSTAS CONCORRENTES DE 2 OU MAIS NICS
            this.tms_Lock.lock();
            if (!this.tms_byTransferID.containsKey(tmri.transferID)){

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

                TransferMultiSender tms = new TransferMultiSender(this, this.nodeIdentifier, tmri.transferID, nic.name, ip, port, t.destIP, t.destPort, cm, true);

                ArrayList<Integer> tmsID = this.transferID_byNIC.get(nic.name);

                tmsID.add(tmri.transferID);
                this.transferID_byNIC.remove(nic.name);
                this.transferID_byNIC.put(nic.name, tmsID);

                this.tms_byTransferID.put(tmri.transferID, tms);
                this.tms_Lock.unlock();

                moveToOngoing(nic.name, t);
                tms.processTransferMultiReceiverInfo(tmri, receivingTime);
            }
            else
                this.tms_Lock.unlock();

        }
        else{
            if(obj instanceof MissingChunkIDs){
                System.out.println("    IT'S A MISSINGCHUNKS");
                MissingChunkIDs mcid = (MissingChunkIDs) obj;

                this.tms_Lock.lock();
                TransferMultiSender tms = this.tms_byTransferID.get(mcid.transferID);
                this.tms_Lock.unlock();

                tms.processMissingChunkIDs(mcid);
            }
            else{
                if(obj instanceof NetworkStatusUpdate){
                    System.out.println("    ==========================================>>>>>>> IT'S AN NetworkStatusUpdate");

                    NetworkStatusUpdate nsu = (NetworkStatusUpdate) obj;

                    this.tms_Lock.lock();
                    TransferMultiSender tms = this.tms_byTransferID.get(nsu.transferID);
                    this.tms_Lock.unlock();

                    tms.processNetworkStatusUpdate(nsu, dp.getAddress());
                }
                else{
                    if(obj instanceof Over) {

                        Over over = (Over) obj;


                        this.smi_Lock.lock();
                        boolean isScheduled = this.smi.scheduledTransmissions.containsKey(over.transferID);
                        boolean isOnGoing = this.smi.onGoingTransmissions.containsKey(over.transferID);
                        this.smi_Lock.unlock();

                        if (isScheduled || isOnGoing) {
                            System.out.println("    IT'S AN OVER (" + over.transferID + ")");
                            this.tms_Lock.lock();
                            TransferMultiSender tms = this.tms_byTransferID.get(over.transferID);
                            this.tms_Lock.unlock();

                            if (tms != null)
                                tms.processOver(over);
                            else {
                                processEarlyOver(over, ip.isLinkLocalAddress());
                            }
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
            for(NIC nic : this.nics.values())
                checkIPListeners(nic);

        printSchedule();
        updateSchedulerMetaInfoFile();
    }

    private void checkIPListeners(NIC nic) {

        ArrayList<Integer> transferIDs = this.transferID_byNIC.get(nic.name);
        int numberOfNICTransfers = transferIDs.size();

        boolean hasLinkLocal = false;
        boolean hasNONLinkLocal = false;

        for (int i = 0; i < numberOfNICTransfers && (!hasLinkLocal || !hasNONLinkLocal); i++){
            if(this.smi.onGoingTransmissions.get(transferIDs.get(i)).destIP.isLinkLocalAddress())
                hasLinkLocal = true;
            else
                hasNONLinkLocal = true;
        }

        if(!hasNONLinkLocal)
            this.ipListeners_byNIC.get(nic.name).kill();
        if(!hasLinkLocal)
            this.ipListeners_LINKLOCAL_byNIC.get(nic.name).kill();

        if(!hasLinkLocal && !hasNONLinkLocal)
            nic.markSchedulerAsInactive();

    }

    public void sendDP(String nicName, int transferID, InetAddress myIP, int myPort, DatagramPacket dp){
        this.tms_Lock.lock();
        boolean res = this.transferID_byNIC.get(nicName).contains(transferID);
        this.tms_Lock.unlock();

        SchedulerIPListener sch;

        if(res){
            this.ipListeners_Lock.lock();

            if(myIP.isLinkLocalAddress())
                sch = this.ipListeners_LINKLOCAL_byNIC.get(nicName);
            else
                sch = this.ipListeners_byNIC.get(nicName);

            this.ipListeners_Lock.unlock();

            sch.sendDP(dp);
        }
    }

    private final Runnable sendTMIs = () -> {

        ArrayList<DatagramPacket> datagramPackets;
        SchedulerIPListener sch;
        this.datagramPackets_Lock.lock();
        this.datagramPackets_LINKLOCAL_Lock.lock();

        if(!this.transferMetaInfos.values().isEmpty()) {
            System.out.println("TRYING TO SEND TMIS");
            //NON LINKLOCAL
            for(NIC nic : this.nics.values()){
                //System.out.println("NIC hasConnection?" + nic.hasConnection);
                if(nic.hasConnection) {
                    sch = this.ipListeners_byNIC.get(nic.name);
                    datagramPackets = createDatagramPackets(nic.name, this.transferMetaInfos);
                    for (DatagramPacket dp : datagramPackets) {
                        //System.out.println("CCC222");
                        sch.sendDP(dp);
                    }
                }
            }
        }

        if(!this.transferMetaInfos_LINKLOCAL.values().isEmpty()) {
            System.out.println("TRYING TO SEND LINKLOCAL TMIS");
            //LINKLOCAL
            for(NIC nic: this.nics.values()){
                if(nic.hasConnection) {
                    sch = this.ipListeners_LINKLOCAL_byNIC.get(nic.name);
                    datagramPackets = createDatagramPackets(nic.name, this.transferMetaInfos_LINKLOCAL);
                    //System.out.println("AAA");

                    for (DatagramPacket dp : datagramPackets) {
                        //System.out.println("CCC222");
                        sch.sendDP(dp);
                    }
                }
            }
        }


        this.datagramPackets_Lock.unlock();
        this.datagramPackets_LINKLOCAL_Lock.unlock();
    };

    private ArrayList<DatagramPacket> createDatagramPackets(String nicName, HashMap<Integer, TransferMetaInfo> transferMetaInfos){
        NIC nic = this.nics.get(nicName);
        int nicSpeed;
        ArrayList<DatagramPacket> dps = new ArrayList<DatagramPacket>();
        Transmission t;
        byte[] data;
        TransferMetaInfo tmi;

        System.out.println(nic.name + " SPEED " + nic.speed);
        this.smi_Lock.lock();
        for(int transferID : transferMetaInfos.keySet()){
            nicSpeed = nic.getNewTransferSpeed();
            t = this.smi.scheduledTransmissions.get(transferID);
            tmi = transferMetaInfos.get(transferID);
            tmi.setFirstLinkConnection(nicSpeed);
            data = getBytesFromObject(tmi);
            dps.add(new DatagramPacket(data, data.length, t.destIP, t.destPort));
        }
        this.smi_Lock.unlock();

        return dps;
    }

    private void reactivateNICIPListeners(NIC nic){
        SchedulerIPListener sch1 = this.ipListeners_byNIC.get(nic.name);
        SchedulerIPListener sch2 = this.ipListeners_LINKLOCAL_byNIC.get(nic.name);

        this.smi_Lock.lock();
        this.tms_Lock.lock();

        boolean hasLinkLocal = false;
        boolean hasNONLinkLocal = false;

        Collection<Transmission> transmissions = this.smi.scheduledTransmissions.values();
        Iterator<Transmission> it = transmissions.iterator();
        Transmission t;

        while (it.hasNext() && (!hasLinkLocal || !hasNONLinkLocal)) {
            t = it.next();
            if (t.destIP.isLinkLocalAddress())
                hasLinkLocal = true;
            else
                hasNONLinkLocal = true;
        }

        ArrayList<Integer> transferIDs = this.transferID_byNIC.get(nic.name);
        int numberOfNICTransfers = transferIDs.size();

        for (int i = 0; i < numberOfNICTransfers && (!hasLinkLocal || !hasNONLinkLocal); i++){
            if(this.smi.onGoingTransmissions.get(transferIDs.get(i)).destIP.isLinkLocalAddress())
                hasLinkLocal = true;
            else
                hasNONLinkLocal = true;
        }

        if(nic.hasConnection && (hasLinkLocal || hasNONLinkLocal)){
            if(hasNONLinkLocal && !sch1.isRunning && sch1.ownIP != null)
                sch1.reactivate();

            if(hasLinkLocal && !sch2.isRunning && sch2.ownIP != null)
                sch2.reactivate();

            //nic.markSchedulerAsActive();
        }
        else {
            sch1.kill();
            sch2.kill();
            //nic.markSchedulerAsInactive();
        }

        this.smi_Lock.unlock();
        this.tms_Lock.unlock();
    }

    public void changeSchedulerIP(NIC nic, ArrayList<InetAddress> ips){
        if(this.nics.containsKey(nic.name)) {
            System.out.println("CHANGE IP");

            //UPDATE TRANSFERMULTISENDERs IP

            Transmission transm;
            TransferMultiSender tms;

            this.tms_Lock.lock();
            ArrayList<Integer> transferIDs = this.transferID_byNIC.get(nic.name);
            this.tms_Lock.unlock();
            System.out.println("got transferID");
            int numberOfTransferIDs = transferIDs.size();
            int transferID;

            for (int i = 0; i < numberOfTransferIDs; i++) {
                System.out.println("cycle start");
                transferID = transferIDs.get(i);
                System.out.println("GOT TRANSFERID");
                this.smi_Lock.lock();
                System.out.println("GOT SMI LOCK");
                transm = this.smi.onGoingTransmissions.get(transferID);
                System.out.println("GOT ON GOING TRANSMISSION");
                this.smi_Lock.unlock();

                this.tms_Lock.lock();
                System.out.println("GOT TMS LOCK");
                tms = this.tms_byTransferID.get(transferID);
                System.out.println("GOT TMS");
                this.tms_Lock.unlock();

                System.out.println("BEFORE CHANGEIP");
                int speed = nic.getActiveTransferSpeed();
                System.out.println("GOT SPEED");
                boolean b = transm.destIP.isLinkLocalAddress();
                System.out.println("GOT ISLINKLOCAL");
                tms.changeOwnIP(ips, b, speed);
                System.out.println("AFTER CHANGEIP");
            }

            System.out.println("before getting ip listeners");
            SchedulerIPListener sch1 = this.ipListeners_byNIC.get(nic.name);
            SchedulerIPListener sch2 = this.ipListeners_LINKLOCAL_byNIC.get(nic.name);
            System.out.println("before changing ip listeners ip");
            sch1.changeOwnIP(ips);
            sch2.changeOwnIP(ips);

            System.out.println("before reactivating nics");
            reactivateNICIPListeners(nic);
            System.out.println("CHANGED IP");
        }
    }

    public void changeNICConnectionStatus(NIC nic, boolean hasConnection, boolean hasNONLinkLocal, boolean hasLinkLocal) {
        if(this.nics.containsKey(nic.name)) {
            TransferMultiSender tms;

            this.tms_Lock.lock();
            for (int transferID : this.transferID_byNIC.get(nic.name)) {
                tms = this.tms_byTransferID.get(transferID);
                this.tms_Lock.unlock();
                if(!hasConnection)
                    tms.updateConnectionStatus(hasConnection);
                else {
                    if ((tms.ownIP.isLinkLocalAddress() && hasLinkLocal) || (!tms.ownIP.isLinkLocalAddress() && hasNONLinkLocal)) {
                        //System.out.println("UPDATING TRANSFERMULTISENDER CONNECTION STATUS NOW");
                        tms.updateConnectionStatus(hasConnection);
                    }
                }
                this.tms_Lock.lock();
            }
            this.tms_Lock.unlock();


            //System.out.println("NON " + hasNONLinkLocal + " ll" + hasLinkLocal);
            SchedulerIPListener sch1 = this.ipListeners_byNIC.get(nic.name);
            SchedulerIPListener sch2 = this.ipListeners_LINKLOCAL_byNIC.get(nic.name);

            if(!hasConnection) {
                sch1.updateConnectionStatus(hasConnection);
                sch2.updateConnectionStatus(hasConnection);
            }
            else{
                if(hasNONLinkLocal)
                    sch1.updateConnectionStatus(hasConnection);
                else
                    sch2.updateConnectionStatus(hasConnection);
            }
            reactivateNICIPListeners(nic);

        }

    }

    /*
     * Schedules the transmission of the information based on its destination IP and priority
     * */
    public void schedule(String infoHash, InetAddress destIP, int destPort, boolean confirmation) {

        if(this.dm.isReadyToBeSent(infoHash)) {
            Transmission t = new Transmission(this.rand.nextInt(), infoHash, destIP, destPort, confirmation);

            this.smi_Lock.lock();
            this.smi.scheduledTransmissions.put(t.transferID, t);
            this.smi_Lock.unlock();

            createTMIDP(t);
            this.nics_Lock.lock();
            for (NIC nic : this.nics.values()) {
                nic.registerTransferSchedule();
                nic.markSchedulerAsActive();
                reactivateNICIPListeners(nic);
            }

            this.nics_Lock.unlock();

            printSchedule();

            updateSchedulerMetaInfoFile();
        }
    }

    private void moveToOngoing(String nicName, Transmission transmission) {

        this.smi_Lock.lock();
        this.smi.scheduledTransmissions.remove(transmission.transferID);
        this.smi.onGoingTransmissions.put(transmission.transferID, transmission);
        int numberOfSchdT = this.smi.scheduledTransmissions.size();
        this.smi_Lock.unlock();

        if(numberOfSchdT == 0)
            for(NIC nic : this.nics.values())
                checkIPListeners(nic);

        this.nics_Lock.lock();
        for(NIC nic : this.nics.values())
            if(!nic.name.equals(nicName))
                nic.registerTransferCancel();
            else
                nic.registerTransferStart();

        this.nics_Lock.unlock();

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

        this.nics_Lock.lock();
        for(NIC nic : this.nics.values()) {
            if(nic.name.equals(nicName))
                nic.registerTransferEnd();
            nic.registerTransferStart();
            nic.markSchedulerAsActive();

            reactivateNICIPListeners(nic);

        }
        this.nics_Lock.unlock();

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

        this.nics_Lock.lock();
        this.nics.get(nicName).registerTransferEnd();
        this.nics_Lock.unlock();

        ArrayList<Integer> tmsIDs = this.transferID_byNIC.get(nicName);

        if(tmsIDs.contains(transferID)) {
            tmsIDs.remove((Object)transferID);
            this.transferID_byNIC.remove(nicName);
            this.transferID_byNIC.put(nicName, tmsIDs);

            if(numberofSchT == 0)
                for(NIC nic : this.nics.values())
                    checkIPListeners(nic);

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
            for(NIC nic : this.nics.values())
                checkIPListeners(nic);

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
        if(data == null || data.length == 0) {
            System.out.println("                MENSAGEM VAZIA WHATTTTTTTTTTTTTT " + (data==null) + " " + data.length);
            try {
                Thread.sleep(100000000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInput in = null;
        Object o = null;

        try {
            in = new ObjectInputStream(bis);
            o = in.readObject(); //EXCEPTION!!!! java.io.EOFException
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