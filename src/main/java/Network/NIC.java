package Network;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


public class NIC {

    public boolean isWireless;
    public String name;

    public boolean hasConnection;
    private boolean stopChecker;
    private ReentrantLock stopChecker_Lock;
    private boolean isCheckerRunning;


    private ReentrantLock statsLock;

    public int speed;
    private int mtu;

    private ReentrantLock nicTransmissionSpeed_Lock;
    private int scheduledTransmissions;
    private int activeTransmissions;

    private ReentrantLock nicReceptionSpeed_Lock;
    private int activeReception;

    /*private ArrayList<Integer> scheduledTransferIDs;
    private ArrayList<Integer> activeTransferIDs;
    private HashMap<Integer, Float> nicSpeed_byTransferID;*/

    private int i = 0;
    private int w_BitRate;
    private int w_TxPower;
    private int w_LinkQuality;
    private int w_SignalLevel;

    public ArrayList<InetAddress> addresses;
    private ReentrantLock addresses_Lock;

    private ScheduledExecutorService ipChangeCheckerSES;

    private ArrayList<ListenerMainUnicast> nicListeners_Rcv;
    private ArrayList<KnockManager> knockManagers;
    private Scheduler scheduler;

    public NIC(String nicName, boolean isWireless, Scheduler scheduler){
        this.isWireless = isWireless;
        this.name = nicName;

        this.hasConnection = false;
        this.stopChecker = true;

        this.stopChecker_Lock = new ReentrantLock();

        this.speed = -1;
        this.mtu = -1;

        this.nicTransmissionSpeed_Lock = new ReentrantLock();
        this.scheduledTransmissions = 0;
        this.activeTransmissions = 0;

        this.nicReceptionSpeed_Lock = new ReentrantLock();
        this.activeReception = 0;

        /*this.transferID_byOrder = new ArrayList<Integer>();
        this.nicSpeed_byTransferID = new HashMap<Integer, Float>();*/

        this.w_BitRate = -1;
        this.w_TxPower = -1;
        this.w_LinkQuality = 0;
        this.w_SignalLevel = 0;

        this.addresses = new ArrayList<InetAddress>();
        this.addresses_Lock = new ReentrantLock();

        this.statsLock = new ReentrantLock();
        this.nicListeners_Rcv = new ArrayList<ListenerMainUnicast>();
        this.knockManagers = new ArrayList<KnockManager>();
        this.scheduler = scheduler;

        this.ipChangeCheckerSES = Executors.newSingleThreadScheduledExecutor();

        this.ipChangeCheckerSES.schedule(this.checkIPChange,0, TimeUnit.MILLISECONDS);
        this.isCheckerRunning = true;
    }

    public void startIPChangeChecker(){
        this.stopChecker_Lock.lock();
        System.out.println("                            AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        this.stopChecker = false;

        if(!this.isCheckerRunning) {
            System.out.println("                            BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");

            this.ipChangeCheckerSES.schedule(this.checkIPChange,500, TimeUnit.MILLISECONDS);
        }
        this.stopChecker_Lock.unlock();
    }

    public void stopIPChangeChecker(){
        this.stopChecker_Lock.lock();
        this.stopChecker = true;
        this.stopChecker_Lock.unlock();

    }

    public void registerLMUListener(ListenerMainUnicast lmu){
        System.out.println("ESTA A TENTAR LIGAR O CHECKER PORQUE CRIOU UM LMU NOVO");
        if(!this.nicListeners_Rcv.contains(lmu))
            this.nicListeners_Rcv.add(lmu);

        this.stopChecker_Lock.lock();
        if(this.stopChecker) {
            startIPChangeChecker();
        }
        this.stopChecker_Lock.unlock();
    }

    public void removeLMUListener(ListenerMainUnicast lmu){
        this.nicListeners_Rcv.remove(lmu);

        if(this.nicListeners_Rcv.isEmpty() && this.scheduler.hasTMIToSend() && this.knockManagers.isEmpty()){
            this.stopChecker_Lock.lock();
            this.stopChecker = true;
            this.stopChecker_Lock.unlock();
        }
    }

    public void registerKnockManager(KnockManager km){
        System.out.println("ESTA A TENTAR LIGAR O CHECKER PORQUE CRIOU UM KNOCKMANAGER NOVO");
        if(!this.knockManagers.contains(km))
            this.knockManagers.add(km);

        this.stopChecker_Lock.lock();
        if(this.stopChecker) {
            startIPChangeChecker();
        }
        this.stopChecker_Lock.unlock();
    }

    public void removeKnockManager(KnockManager km){
        this.knockManagers.remove(km);

        if(this.knockManagers.isEmpty() && this.scheduler.hasTMIToSend() && this.nicListeners_Rcv.isEmpty()){
            this.stopChecker_Lock.lock();
            this.stopChecker = true;
            this.stopChecker_Lock.unlock();
        }
    }

    public void markSchedulerAsActive(){
        System.out.println("(NIC) MARKING " + this.name + " ACTIVE");
        this.stopChecker_Lock.lock();
        if(this.stopChecker) {
            startIPChangeChecker();
            System.out.println("    (NIC) MARKED " + this.name + " ACTIVE");
        }
        else
            System.out.println("    (NIC) " + this.name + " ALREADY ACTIVE");
        this.stopChecker_Lock.unlock();

    }

    public void markSchedulerAsInactive(){
        System.out.println("(NIC) MARKING " + this.name + " INACTIVE");

        if(this.nicListeners_Rcv.isEmpty() && this.knockManagers.isEmpty()) {
            this.stopChecker_Lock.lock();
            this.stopChecker = true;
            this.stopChecker_Lock.unlock();
            System.out.println("    (NIC) MARKED " + this.name + " INACTIVE");
        }
        else
            System.out.println("    (NIC) " + this.name + " HAS:\n\t" + this.nicListeners_Rcv.size() + " LMUs ACTIVE\n\t" + this.knockManagers.size() + " KM ACTIVE");
    }

    private final Runnable checkIPChange = () -> {
        this.stopChecker_Lock.lock();
        this.isCheckerRunning = true;
        this.stopChecker_Lock.unlock();

        //CASE THAT THERES NO NIC
        //System.out.println("CHECKING IP CHANGE " + this.name + " " + this.i++);
        try{
            //System.out.println("    TRYING TO GET NICS");
            NetworkInterface nic = NetworkInterface.getByName(this.name);

            //System.out.println("    GOT NICS?");
            if(nic != null) {
                //AQUI JA É CONFIRMADO QUE TEM CONEXÃO
                this.hasConnection = true;
                //System.out.println("        YES");
                boolean change = false;
                InetAddress address;

                Enumeration<InetAddress> inetEnum = nic.getInetAddresses();
                ArrayList<InetAddress> newAddresses = new ArrayList<InetAddress>();
                boolean hasLinkLocal = false;
                boolean hasNONLinkLocal = false;
                this.addresses_Lock.lock();
                while(inetEnum.hasMoreElements()) {
                    address = inetEnum.nextElement();
                    newAddresses.add(address);
                    if(!this.addresses.contains(address)) {
                        change = true;
                    }
                    if(address.isLinkLocalAddress())
                        hasLinkLocal = true;
                    else
                        hasNONLinkLocal = true;
                }
                this.addresses_Lock.unlock();
                //System.out.println("        NEW IPS?");
                if(change) {
                    //System.out.println("            YES");
                    this.addresses_Lock.lock();

                    System.out.println("(NIC) " + this.name + " OLD => " + this.addresses);
                    this.addresses = new ArrayList<>(newAddresses);
                    System.out.println("(NIC) " + this.name + " NEW => " + this.addresses);
                    this.addresses_Lock.unlock();


                    ListenerMainUnicast lmu;
                    KnockManager km;
                    int numberOfLMU = this.nicListeners_Rcv.size();
                    int numberOfKM = this.knockManagers.size();

                    for(int i = 0; i < numberOfLMU; i++){
                        lmu = this.nicListeners_Rcv.get(i);
                        lmu.changeIP(newAddresses);
                    }

                    for(int i = 0; i < numberOfKM; i++){
                        km = this.knockManagers.get(i);
                        km.changeIP(newAddresses);
                    }

                    this.scheduler.changeSchedulerIP(this, newAddresses);

                }
                //else
                //System.out.println("            NO");

                //System.out.println("        INICIO DO UPDATE DAS VARIAVEIS");
                //UPDATE VARIABLES
                this.statsLock.lock();
                this.mtu = nic.getMTU();
                this.statsLock.unlock();

                //System.out.println("        EXECUTANDO COMANDOS");
                if(this.isWireless)
                    //this.speed = 300;
                    getWirelessStatus();
                else
                    getWiredSpeed();
                //System.out.println("        COMANDOS EXECUTADOS => " + this.speed);

                //System.out.println("        FIM DO UPDATE DAS VARIAVEIS");

                //System.out.println("        UPDATE CONNECTION STATUS");

                updateNICListenersConnectionStatus(true, hasNONLinkLocal, hasLinkLocal);

            }
            else{
                //System.out.println("        NOTHING");
                this.addresses_Lock.lock();
                this.addresses.clear();
                this.addresses_Lock.unlock();
                if(this.hasConnection) {
                    //System.out.println("        NO NIC CONNECTION ( " + this.name + " )");
                    this.mtu = 0;
                    this.speed = -1;
                    this.hasConnection = false;

                    //ATUALIZAR O ESTADO PARA NO CONNECTION
                    updateNICListenersConnectionStatus(false, false, false);

                }
            }

        } catch (SocketException e) {
            e.printStackTrace();
        }
        this.stopChecker_Lock.lock();
        //System.out.println("============================>>>>>>>>>>CHECKING CHECKER");
        if(!this.stopChecker){
            //System.out.println("RESTARTING " + this.name + " IP CHECKER");
            this.ipChangeCheckerSES.schedule(this.checkIPChange,500, TimeUnit.MILLISECONDS);
        }
        else {
            this.isCheckerRunning = false;
            //System.out.println("    STOPPING " + this.name + " IP CHECKER");
        }
        this.stopChecker_Lock.unlock();

        //System.out.println("==========\n");
    };

    private void updateNICListenersConnectionStatus(boolean value, boolean hasNONLinkLocal, boolean hasLinkLocal){
        //System.out.println("(NIC) UPDATING " + this.name + " CONNECTION STATUS TO " + value);
        ListenerMainUnicast lmu;
        KnockManager km;
        int numberOfRcv = this.nicListeners_Rcv.size();
        int numberOfKM = this.knockManagers.size();


        for(int i = 0; i < numberOfRcv; i++){
            lmu = this.nicListeners_Rcv.get(i);
            lmu.updateConnectionStatus(value);
        }

        for(int i = 0; i < numberOfKM; i++){
            km = this.knockManagers.get(i);
            km.updateConnectionStatus(value);
        }

        this.scheduler.changeNICConnectionStatus(this, value, hasNONLinkLocal, hasLinkLocal);
    }

    private void getWirelessStatus() {

        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            //processBuilder.command("bash", "-c", "cat /var/log/syslog | egrep -i 'wlp3s0.{1,}txrate' | tail -1");
            processBuilder.command("bash", "-c", "iwconfig");
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null)
                output.append(line).append('\n');

            boolean exitVal = process.waitFor(200, TimeUnit.MILLISECONDS);
            if (exitVal) {
                String[] aux = output.toString().split("\n");

                for(int i = 0; i < aux.length; i++)
                aux[i] = aux[i].trim();

                String[] lineParams;
                boolean correctNIC = false;
                boolean connected = false;
                //System.out.println("            GOT THE RESULTS");

                for(int i = 0; i < aux.length; i++){
                    //System.out.println(aux[i] + " i = " + i);
                    switch (i % 8){
                        case 0:{
                            lineParams = aux[i].split("  ");
                            correctNIC = lineParams[0].equals(this.name);
                            connected = lineParams[3].contains("\"");
                            //System.out.println(Arrays.toString(lineParams));
                            break;
                        }
                        case 2:{
                            if(correctNIC && connected){
                                lineParams = aux[i].split("  ");
                                String[] speed = lineParams[0].split("=")[1].split(" ");
                                this.statsLock.lock();
                                if(speed[1].equals("kb/s")) {
                                    this.w_BitRate = 1;
                                    this.speed = 1;
                                }
                                else {
                                    int unit = 1;
                                    if (speed[1].equals("Gb/s")) {
                                        unit = 1000;
                                    }
                                    this.w_BitRate = (int) Float.parseFloat(speed[0]) * unit;
                                    this.speed = w_BitRate;
                                }
                                this.statsLock.unlock();
                                //System.out.println(Arrays.toString(speed));
                            }
                            break;
                        }
                        default:
                            break;
                    }
                }
                //System.out.println(Arrays.toString(aux));
               /*String[] values = output.toString().split(" ");
                this.statsLock.lock();
                this.w_above = Integer.parseInt(values[values.length-4].split("=")[1]);
                this.w_signal = Integer.parseInt(values[values.length-3].split("=")[1]);
                this.w_noise = Integer.parseInt(values[values.length-2].split("=")[1]);
                this.w_txrate = Integer.parseInt(values[values.length-1].split("=")[1]);
                this.speed = this.w_txrate;
                this.statsLock.unlock();*/

            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void getWiredSpeed() {
        File speed = new File("/sys/class/net/" + this.name + "/speed");
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new FileReader(speed));
            String text = null;
            StringBuilder number = new StringBuilder();

            while ((text = reader.readLine()) != null)
                number.append(text);

            this.statsLock.lock();
            this.speed = Integer.parseInt(number.toString());
            this.statsLock.unlock();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    /*public void registerTransferStart(int transferID){

        this.nicSpeed_Lock.lock();
        int order = this.transferID_byOrder.size();
        this.transferID_byOrder.add(transferID);
        int auxTransferID;

        switch (order) {
            case 0:
                this.nicSpeed_byTransferID.put(transferID, (float) 1);
                break;

            case 1:
                auxTransferID = this.transferID_byOrder.get(0);
                this.nicSpeed_byTransferID.remove(auxTransferID);
                this.nicSpeed_byTransferID.put(auxTransferID, (float) 0.5);

                this.nicSpeed_byTransferID.put(transferID, (float) 0.5);
                break;

            case 2:
                auxTransferID = this.transferID_byOrder.get(0);
                this.nicSpeed_byTransferID.remove(auxTransferID);
                this.nicSpeed_byTransferID.put(auxTransferID, (float) 0.45);

                auxTransferID = this.transferID_byOrder.get(1);
                this.nicSpeed_byTransferID.remove(auxTransferID);
                this.nicSpeed_byTransferID.put(auxTransferID, (float) 0.45);

                this.nicSpeed_byTransferID.put(transferID, (float) 0.1);
                break;

            default:
                float speed = (float) 0.1 / (order - 1);

                for (int i = 2; i < order; i++) {
                    auxTransferID = this.transferID_byOrder.get(i);
                    this.nicSpeed_byTransferID.get(auxTransferID);
                    this.nicSpeed_byTransferID.put(auxTransferID, speed);
                }
                this.nicSpeed_byTransferID.put(transferID, speed);
                break;
        }
        this.nicSpeed_Lock.unlock();
    }

    public void registerTransferEnd(int transferID){

        this.nicSpeed_Lock.lock();

        this.transferID_byOrder.remove((Object)transferID);
        this.nicSpeed_byTransferID.remove(transferID);

        int numberOfTransfers = this.transferID_byOrder.size();
        int auxTransferID;

        if(numberOfTransfers != 0) {
            switch (numberOfTransfers) {
                case 1:
                    auxTransferID = this.transferID_byOrder.get(0);
                    this.nicSpeed_byTransferID.remove(auxTransferID);
                    this.nicSpeed_byTransferID.put(auxTransferID, (float) 1);
                    break;

                case 2:
                    auxTransferID = this.transferID_byOrder.get(0);
                    this.nicSpeed_byTransferID.remove(auxTransferID);
                    this.nicSpeed_byTransferID.put(auxTransferID, (float) 0.5);

                    auxTransferID = this.transferID_byOrder.get(1);
                    this.nicSpeed_byTransferID.remove(auxTransferID);
                    this.nicSpeed_byTransferID.put(auxTransferID, (float) 0.5);
                    break;
                default:
                    auxTransferID = this.transferID_byOrder.get(0);
                    this.nicSpeed_byTransferID.remove(auxTransferID);
                    this.nicSpeed_byTransferID.put(auxTransferID, (float) 0.45);

                    auxTransferID = this.transferID_byOrder.get(1);
                    this.nicSpeed_byTransferID.remove(auxTransferID);
                    this.nicSpeed_byTransferID.put(auxTransferID, (float) 0.45);

                    float speed = (float) (0.1 / (numberOfTransfers - 2));

                    for (int i = 2; i < numberOfTransfers; i++) {
                        auxTransferID = this.transferID_byOrder.get(i);
                        this.nicSpeed_byTransferID.get(auxTransferID);
                        this.nicSpeed_byTransferID.put(auxTransferID, speed);
                    }

                    break;
            }
        }
        this.nicSpeed_Lock.unlock();
    }

    public int getTransferSpeed(int transferID) {
        int sp;

        this.nicSpeed_Lock.lock();
        float portion = this.nicSpeed_byTransferID.get(transferID);
        this.nicSpeed_Lock.unlock();
        this.statsLock.lock();
        sp = (int) Math.max(this.speed * portion, 1);
        this.statsLock.unlock();

        return sp;
    }*/

    //TRANSMISSION SPEED

    public void registerTransmissionSchedule(){
        this.nicTransmissionSpeed_Lock.lock();
        this.scheduledTransmissions++;
        this.nicTransmissionSpeed_Lock.unlock();
    }
    public void registerTransmissionCancel() {
        this.nicTransmissionSpeed_Lock.lock();
        this.scheduledTransmissions--;
        this.nicTransmissionSpeed_Lock.unlock();
    }
    public void registerTransmissionStart(){
        this.nicTransmissionSpeed_Lock.lock();
        this.scheduledTransmissions--;
        this.activeTransmissions++;
        this.nicTransmissionSpeed_Lock.unlock();
    }
    public void registerTransmissionEnd() {
        this.nicTransmissionSpeed_Lock.lock();
        this.activeTransmissions--;
        this.nicTransmissionSpeed_Lock.unlock();
    }

    public int getActiveTransmissionSpeed(){
        int sp = -1;

        while(sp == -1) {
            this.nicTransmissionSpeed_Lock.lock();
            sp = this.speed ;
            this.nicTransmissionSpeed_Lock.unlock();
            if(sp == -1) {
                if(this.isWireless)
                    getWirelessStatus();
                else
                    getWiredSpeed();
            }
        }

        if(this.activeTransmissions != 0)
            sp /= this.activeTransmissions;

        return sp;
    }

    public int getNewTransmissionSpeed(){
        int sp = -1;

        while(sp == -1) {
            this.nicTransmissionSpeed_Lock.lock();
            sp = this.speed;
            this.nicTransmissionSpeed_Lock.unlock();
            if(sp == -1) {
                if(this.isWireless)
                    getWirelessStatus();
                else
                    getWiredSpeed();
            }
        }
        sp /= (this.activeTransmissions + 1);
        return sp;
    }

    //RECEPTION SPEED

    public void registerReceptionStart(){
        this.nicReceptionSpeed_Lock.lock();
        this.activeReception++;
        this.nicReceptionSpeed_Lock.unlock();
    }
    public void registerReceptionEnd() {
        this.nicReceptionSpeed_Lock.lock();
        this.activeReception--;
        this.nicReceptionSpeed_Lock.unlock();
    }

    public int getActiveReceptionSpeed(){
        int sp = -1;

        while(sp == -1) {
            this.nicReceptionSpeed_Lock.lock();
            sp = this.speed ;
            this.nicReceptionSpeed_Lock.unlock();
            if(sp == -1) {
                if(this.isWireless)
                    getWirelessStatus();
                else
                    getWiredSpeed();
            }
        }

        if(this.activeReception != 0)
            sp /= this.activeReception;

        return sp;
    }

    public int getNICFirstLinkConnectionSpeed(){
        int speed;

        this.nicTransmissionSpeed_Lock.lock();
        speed = this.speed;
        this.nicTransmissionSpeed_Lock.unlock();

        return speed;
    }


    public int getMTU() {
        int mtu = 0;

        while(mtu == 0) {
            this.statsLock.lock();
            mtu = this.mtu;
            this.statsLock.unlock();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return mtu;
    }

/*    public ArrayList<InetAddress> getAddresses(){

        this.addresses_Lock.lock();
        ArrayList<InetAddress> ips = new ArrayList<InetAddress>(this.addresses);
        this.addresses_Lock.unlock();

        return ips;
    }*/

    public ArrayList<InetAddress> getLinkLocalAddresses() {
        this.addresses_Lock.lock();
        ArrayList<InetAddress> ips = new ArrayList<InetAddress>(this.addresses);
        this.addresses_Lock.unlock();

        int size = ips.size();
        InetAddress ip;

        for (int i = 0; i < size; i++){
            ip = ips.get(i);
            if (!ip.isLinkLocalAddress()){
                ips.remove(i);
                i--;
                size--;
            }
        }

        return ips;
    }

    public ArrayList<InetAddress> getNONLinkLocalAddresses() {
        this.addresses_Lock.lock();
        ArrayList<InetAddress> ips = new ArrayList<InetAddress>(this.addresses);
        this.addresses_Lock.unlock();

        int size = ips.size();
        InetAddress ip;

        for (int i = 0; i < size; i++){
            ip = ips.get(0);
            if (ip.isLinkLocalAddress()){
                ips.remove(ip);
                i--;
                size--;
            }
        }

        return ips;
    }

    public boolean checkConnectionStatus(){
        boolean res = false;

        try {
            NetworkInterface nic = NetworkInterface.getByName(this.name);

            if(nic != null)
                res = true;

        } catch (SocketException e) {
            e.printStackTrace();
        }

        return  res;
    }
}
