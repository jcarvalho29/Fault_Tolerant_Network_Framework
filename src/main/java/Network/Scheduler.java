package Network;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class Scheduler {
    private SchedulerMetaInfo smi;

    public Scheduler(String Root, boolean fetch){
        SchedulerMetaInfo smi = null;
        Root = folderPathNormalizer(Root);


        if (fetch) {
            smi = fetchSMI(Root + "Network/");
        }

        if(smi == null) {
            createNetworkFolder(Root);
            this.smi = new SchedulerMetaInfo(Root);
            writeSchedulerMetaInfoFile();
        }
        else {
            this.smi = smi;
        }
    }

    private void createNetworkFolder(String Root) {
        String path = folderPathNormalizer(Root) + "Network/";
        File root = new File(path);
        while(!root.exists() && !root.isDirectory() && !root.mkdir());
    }

    /*
    * Schedules the transmission of the information based ont its destination IP and priority
    * */
    public void schedule(String ip, int priority, int port, String infoHash){
        HashMap<Integer, ArrayList<Transmission>> priority_Hashs;
        ArrayList<Transmission> transmissions;
        Transmission t = new Transmission(infoHash, port);

        if(this.smi.infoByIP_Priority.containsKey(ip)){
            if (this.smi.infoByIP_Priority.get(ip).containsKey(priority)){
                transmissions = this.smi.infoByIP_Priority.get(ip).get(priority);
            }
            else{
                transmissions = new ArrayList<Transmission>();

                this.smi.infoByIP_Priority.get(ip).put(priority, transmissions);

                makePriorityFolder(ip, priority);
            }
        }
        else{
            priority_Hashs = new HashMap<Integer, ArrayList<Transmission>>();
            transmissions = new ArrayList<Transmission>();

            priority_Hashs.put(priority, transmissions);
            this.smi.infoByIP_Priority.put(ip, priority_Hashs);
            makeIPFolder(ip);
            makePriorityFolder(ip, priority);
        }

        boolean b = false;
        int tam = this.smi.infoByIP_Priority.get(ip).get(priority).size();
        ArrayList<Transmission> t_pointer = this.smi.infoByIP_Priority.get(ip).get(priority);
        Transmission a;

        for(int i = 0; !b && i < tam; i++){
            a = t_pointer.get(i);
            if(t.infoHash.equals(a.infoHash)) {
                b = true;
            }
        }

        if(!b) {
            transmissions.add(t);
            this.smi.Hashs.add(infoHash);

            this.smi.infoByIP_Priority.get(ip).put(priority, transmissions);

            writeTransmissionToFolder(ip, priority, t);
            updateSchedulerMetaInfoFile();
        }
    }

    public void editPriority(String ip, int oldPriority, int newPriority, String infoHash){
        ArrayList<Transmission> transmissions;

        if(this.smi.infoByIP_Priority.containsKey(ip))
            if(this.smi.infoByIP_Priority.get(ip).containsKey(oldPriority)){
                transmissions = this.smi.infoByIP_Priority.get(ip).get(oldPriority);
                boolean b = false;
                int tam = transmissions.size();

                for(int i = 0; !b && i < tam; i++) {
                    if (transmissions.get(i).infoHash.equals(infoHash))
                        b = true;
                }

                if(b){
                    Transmission t = readTransmission(ip, oldPriority, infoHash);
                    schedule(ip, newPriority, t.port, t.infoHash);
                    deleteTransmissionFromFolder(ip, oldPriority, infoHash);
                    removeTransmissionFromSMI(ip, oldPriority, t);
                    updateSchedulerMetaInfoFile();
                }
            }
    }

    public void editIP(String oldIP, String newIP, int priority, String infoHash){
        ArrayList<Transmission> transmissions;

        if(this.smi.infoByIP_Priority.containsKey(oldIP))
            if(this.smi.infoByIP_Priority.get(oldIP).containsKey(priority)) {
                transmissions = this.smi.infoByIP_Priority.get(oldIP).get(priority);
                int tam = transmissions.size();
                boolean b = false;

                for(int i = 0; !b && i < tam; i++){
                    if(transmissions.get(i).infoHash.equals(infoHash))
                        b = true;
                }

                if(b){
                    Transmission t = readTransmission(oldIP, priority, infoHash);
                    schedule(newIP, priority, t.port, t.infoHash);
                    deleteTransmissionFromFolder(oldIP, priority, infoHash);
                    removeTransmissionFromSMI(oldIP, priority, t);
                    updateSchedulerMetaInfoFile();
                }
            }
    }

    public void editPort(String ip, int priority, String infoHash, int newPort){
        ArrayList<Transmission> transmissions = null;

        if(this.smi.infoByIP_Priority.containsKey(ip)) {
            if (this.smi.infoByIP_Priority.get(ip).containsKey(priority)) {
                transmissions = this.smi.infoByIP_Priority.get(ip).get(priority);
            }
        }

        if(transmissions != null) {
            Transmission t = readTransmission(ip, priority, infoHash);
            t.port = newPort;

            deleteTransmissionFromFolder(ip, priority, infoHash);
            writeTransmissionToFolder(ip, priority, t);
        }
    }

    public ArrayList<Transmission> getTransmissionsByIP_Priority(String ip, int priority){
        ArrayList<Transmission> transmissions = null;

        if(this.smi.infoByIP_Priority.containsKey(ip)){
            if(this.smi.infoByIP_Priority.get(ip).containsKey(priority)){
                transmissions = this.smi.infoByIP_Priority.get(ip).get(priority);
            }
        }

        return transmissions;
    }

    public ArrayList<Transmission> getTransmissionsToIP(String ip){
        ArrayList<Transmission> allTransmissions = new ArrayList<Transmission>();
        int prioritiesTam;

        if(this.smi.infoByIP_Priority.containsKey(ip)){
            prioritiesTam = this.smi.infoByIP_Priority.get(ip).keySet().size();
            for(int i = prioritiesTam-1; i >= 0; i--){
                allTransmissions.addAll(this.smi.infoByIP_Priority.get(ip).get(i));
            }
        }

        return allTransmissions;
    }

    public ArrayList<Transmission> getTransmissionsToIP(String ip, int max){
        ArrayList<Transmission> Transmissions = new ArrayList<Transmission>();
        int TransmissionsSize = 0;
        ArrayList<Transmission> transmissionsByPriority;

        Set<Integer> existingPrioritiesSet;
        Integer[] existingPriorities;
        int prioritiesTam;
        int numberOfTransmissions;
        Transmission t;

        if(this.smi.infoByIP_Priority.containsKey(ip)){
            existingPrioritiesSet = this.smi.infoByIP_Priority.get(ip).keySet();
            existingPriorities = existingPrioritiesSet.toArray(new Integer[0]);
            prioritiesTam = existingPrioritiesSet.size();
            for(int i = prioritiesTam-1; i >= 0; i--){
                transmissionsByPriority = this.smi.infoByIP_Priority.get(ip).get(existingPriorities[i]);
                numberOfTransmissions = transmissionsByPriority.size();
                for(int j = 0; j < numberOfTransmissions && TransmissionsSize < max; j++) {
                    Transmissions.add(transmissionsByPriority.get(j));
                    TransmissionsSize++;
                }
            }
        }

        return Transmissions;
    }

    private void removeTransmissionFromSMI(String ip, int priority, Transmission transmission) {
        ArrayList<Transmission> transmissions = this.smi.infoByIP_Priority.get(ip).get(priority);

        removeTransmissionFromArrayList(transmissions, transmission);

        if(transmissions.size() == 0){
            HashMap<Integer, ArrayList<Transmission>> priorityHashTable = this.smi.infoByIP_Priority.get(ip);

            priorityHashTable.remove(priority);
            deletePriorityFolder(ip, priority);
            if(priorityHashTable.keySet().size() == 0){
                this.smi.infoByIP_Priority.remove(ip);
                deleteIPFolder(ip);

            }
            else{
                HashMap<Integer, ArrayList<Transmission>> a = this.smi.infoByIP_Priority.get(ip);
                a.remove(priority);
                this.smi.infoByIP_Priority.put(ip, a);
            }
        }
        else
            this.smi.infoByIP_Priority.get(ip).put(priority, transmissions);

        this.smi.Hashs.remove(transmission.infoHash);

    }

    public ArrayList<String> getDestinationServers(){
        return new ArrayList<String>(this.smi.infoByIP_Priority.keySet());
    }

    public boolean isScheduled(String hash){
        return this.smi.Hashs.contains(hash);
    }
    private void removeTransmissionFromArrayList(ArrayList<Transmission> transmissions, Transmission t){
        transmissions.removeIf(a -> a.infoHash.equals(t.infoHash) && a.port == t.port);
    }

    private void deleteIPFolder(String ip) {
        String ipFolderPath = this.smi.Root + ip + "/";

        File ipFolder = new File(ipFolderPath);

        while(ipFolder.exists() && !ipFolder.delete());

    }

    private void deletePriorityFolder(String ip, int priority) {
        String priorityFolderPath = this.smi.Root + ip + "/" + priority + "/";

        File priorityFolder = new File(priorityFolderPath);

        while(priorityFolder.exists() && !priorityFolder.delete());
    }

    private void deleteTransmissionFromFolder(String ip, int priority, String infoHash) {
        String transmissionFilePath = this.smi.Root + ip + "/" + priority + "/" + infoHash;

        File transmissionFile = new File(transmissionFilePath);

        while(transmissionFile.exists() && !transmissionFile.delete());
    }

    private Transmission readTransmission(String ip, int priority, String infoHash) {
        String transmissionFilePath = this.smi.Root + ip + "/" + priority + "/" + infoHash;

        File FileInfo = new File(transmissionFilePath);
        Object obj = null;

        if(FileInfo.exists()) {
            FileInputStream fileIn = null;

            try {
                fileIn = new FileInputStream(transmissionFilePath);
                ObjectInputStream objectIn = new ObjectInputStream(fileIn);

                obj = objectIn.readObject();

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return (Transmission) obj;
    }

    private void makeIPFolder(String ip) {
        String path = this.smi.Root + ip + "/";
        File ipFolder = new File(path);

        while(!ipFolder.exists() && !ipFolder.isDirectory() && !ipFolder.mkdir());

    }

    private void makePriorityFolder(String ip, int priority) {
        String path = this.smi.Root + ip + "/" + priority + "/";
        File priorityFolder = new File(path);

        while(!priorityFolder.exists() && !priorityFolder.isDirectory() && !priorityFolder.mkdir());
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

    private void writeTransmissionToFolder(String ip, int priority, Transmission t) {
        String ipPriorityFolderPath = this.smi.Root + ip + "/" + priority + "/" + t.infoHash;

        File transmissionInfo = new File(ipPriorityFolderPath);


        FileOutputStream fileOut = null;
        try {
            fileOut = new FileOutputStream(transmissionInfo);
            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
            objectOut.writeObject(t);
            objectOut.close();

        } catch (IOException e) {
            e.printStackTrace();
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

    private void printScheduler(){
        for(String ip : this.smi.infoByIP_Priority.keySet()){
            for(int priority : this.smi.infoByIP_Priority.get(ip).keySet())
                for(Transmission t : this.smi.infoByIP_Priority.get(ip).get(priority))
                    System.out.println("IP => " + ip + " PRIORITY => " + priority + "\nHASH => " + t.infoHash);
        }
    }
}
