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

    private int speed;
    private int mtu;

    private int i = 0;
    private int w_BitRate;
    private int w_TxPower;
    private int w_LinkQuality;
    private int w_SignalLevel;

    public ArrayList<InetAddress> addresses;
    private ReentrantLock addresses_Lock;

    private ScheduledExecutorService ipChangeCheckerSES;

    private ArrayList<ListenerMainUnicast> nicListeners_Rcv;
    private Scheduler scheduler;

    public NIC(String nicName, boolean isWireless, Scheduler scheduler){
        this.isWireless = isWireless;
        this.name = nicName;

        this.hasConnection = false;
        this.stopChecker = true;

        this.stopChecker_Lock = new ReentrantLock();

        this.speed = -1;
        this.mtu = -1;

        this.w_BitRate = -1;
        this.w_TxPower = -1;
        this.w_LinkQuality = 0;
        this.w_SignalLevel = 0;

        this.addresses = new ArrayList<InetAddress>();
        this.addresses_Lock = new ReentrantLock();

        this.statsLock = new ReentrantLock();
        this.nicListeners_Rcv = new ArrayList<ListenerMainUnicast>();
        this.scheduler = scheduler;
        //this.nicListeners_Snd = new ArrayList<TransferMultiSender>();

        this.ipChangeCheckerSES = Executors.newSingleThreadScheduledExecutor();

        this.ipChangeCheckerSES.schedule(this.checkIPChange,0, TimeUnit.MILLISECONDS);
        this.isCheckerRunning = true;
    }

    public void startIPChangeChecker(){
        System.out.println("                            AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        this.stopChecker_Lock.lock();
        this.stopChecker = false;

        if(!this.isCheckerRunning)
            this.ipChangeCheckerSES.schedule(this.checkIPChange,1000, TimeUnit.MILLISECONDS);
        this.stopChecker_Lock.unlock();
    }

    public void stopIPChangeChecker(){
        this.stopChecker_Lock.lock();
        this.stopChecker = true;
        this.stopChecker_Lock.unlock();

    }

    public void registerNewLMUListener(ListenerMainUnicast lmu){
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

        //if(this.nicListeners_Rcv.isEmpty() && this.nicListeners_Snd.isEmpty())
        if(this.nicListeners_Rcv.isEmpty() && this.scheduler.hasTMIToSend()){
            this.stopChecker_Lock.lock();
            this.stopChecker = true;
            this.stopChecker_Lock.unlock();
        }
    }

    public void markSchedulerAsActive(){
        System.out.println("==============> MARKING " + this.name + " ACTIVE");
        this.stopChecker_Lock.lock();
        if(this.stopChecker) {
            startIPChangeChecker();
            System.out.println("    ==============> MARKED " + this.name + " ACTIVE");
        }
        else
            System.out.println("    ==============> " + this.name + " ALREADY ACTIVE");
        this.stopChecker_Lock.unlock();

    }

    public void markSchedulerAsInactive(){
        System.out.println("==============> MARKING " + this.name + " INACTIVE");

        if(this.nicListeners_Rcv.size() == 0) {
            this.stopChecker_Lock.lock();
            this.stopChecker = true;
            this.stopChecker_Lock.unlock();
            System.out.println("    ==============> MARKED " + this.name + " INACTIVE");
        }
        else
            System.out.println("    ==============> " + this.name + " HAS " + this.nicListeners_Rcv.size() + " LMUs ACTIVE (CANT STOP IT)");
    }

    private final Runnable checkIPChange = () -> {
        this.stopChecker_Lock.lock();
        this.isCheckerRunning = true;
        this.stopChecker_Lock.unlock();

        //CASE THAT THERES NO NIC
        System.out.println("CHECKING IP CHANGE " + this.name + " " + this.i++);
        try{
            //System.out.println("    TRYING TO GET NICS");
            NetworkInterface nic = NetworkInterface.getByName(this.name);

            //System.out.println("    GOT NICS?");
            if(nic != null) {
                //AQUI JA É CONFIRMADO QUE TEM CONEXÃO
                //System.out.println("        YES");
                boolean change = false;
                InetAddress address;

                Enumeration<InetAddress> inetEnum = nic.getInetAddresses();
                ArrayList<InetAddress> newAddresses = new ArrayList<InetAddress>();

                this.addresses_Lock.lock();
                while(inetEnum.hasMoreElements()) {
                    address = inetEnum.nextElement();
                    newAddresses.add(address);
                    if(!this.addresses.contains(address)) {
                        change = true;
                    }
                }
                this.addresses_Lock.unlock();
                //System.out.println("        NEW IPS?");
                if(change) {
                    //System.out.println("            YES");
                    this.addresses_Lock.lock();

                    System.out.println("OLD => " + this.addresses);
                    this.addresses = newAddresses;
                    System.out.println("NEW => " + this.addresses);

                    ListenerMainUnicast lmu;
                    TransferMultiSender tms;
                    int numberOfRcv = this.nicListeners_Rcv.size();
                    //int numberOfSnd = this.nicListeners_Snd.size();

                    for(int i = 0; i < numberOfRcv; i++){
                        lmu = this.nicListeners_Rcv.get(i);
                        lmu.changeIP(this.addresses);
                    }

                    this.scheduler.changeNICListenersIP(this, this.addresses);
                    /*for(int i = 0; i < numberOfSnd; i++){
                        tms = this.nicListeners_Snd.get(i);
                        tms.changeOwnIP(this.addresses, true);
                    }*/
                    this.addresses_Lock.unlock();
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
                    getWirelessStatus();
                else
                    getWiredSpeed();
                //System.out.println("        COMANDOS EXECUTADOS");

                //System.out.println("        FIM DO UPDATE DAS VARIAVEIS");

                //System.out.println("        UPDATE CONNECTION STATUS");

                if(!this.hasConnection) {
                    this.hasConnection = true;
                    updateNICListenersConnectionStatus(true);
                }

            }
            else{
                System.out.println("        NOTHING");
                if(this.hasConnection) {
                    System.out.println("        NO NIC CONNECTION ( " + this.name + " )");
                    this.mtu = 0;
                    this.speed = -1;
                    this.hasConnection = false;

                    //ATUALIZAR O ESTADO PARA NO CONNECTION
                    updateNICListenersConnectionStatus(false);

                }}

        } catch (SocketException e) {
            e.printStackTrace();
        }
        this.stopChecker_Lock.lock();
        System.out.println("============================>>>>>>>>>>CHECKING CHECKER");
        if(!this.stopChecker){
            System.out.println("RESTARTING IP CHECKER");
            this.ipChangeCheckerSES.schedule(this.checkIPChange,1000, TimeUnit.MILLISECONDS);
        }
        else {
            this.isCheckerRunning = false;
            System.out.println("    STOPPING IP CHECKER");
        }
        this.stopChecker_Lock.unlock();

        System.out.println("==========\n");
    };

    private void updateNICListenersConnectionStatus(boolean value){
        System.out.println("UPDATING " + this.name + " CONNECTION STATUS");
        ListenerMainUnicast lmu;
        TransferMultiSender tms;
        int numberOfRcv = this.nicListeners_Rcv.size();
        //int numberOfSnd = this.nicListeners_Snd.size();

        for(int i = 0; i < numberOfRcv; i++){
            lmu = this.nicListeners_Rcv.get(i);
            lmu.updateConnectionStatus(value);
        }

        this.scheduler.changeNICConnectionStatus(this, value);
/*        for(int i = 0; i < numberOfSnd; i++){
            tms = this.nicListeners_Snd.get(i);
            tms.updateConnectionStatus(value);
        }*/
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
                System.out.println("            GOT THE RESULTS");

                for(int i = 0; i < aux.length; i++){
                    switch (i % 8){
                        case 0:{
                            lineParams = aux[i].split("  ");
                            correctNIC = lineParams[0].equals(this.name);
                            //System.out.println(Arrays.toString(lineParams));
                            break;
                        }
                        case 2:{
                            if(correctNIC){
                                lineParams = aux[i].split("  ");
                                String[] speed = lineParams[0].split("=")[1].split(" ");
                                int unit = 1;
                                if(speed[1].equals("Mb/s")) {
                                    unit = 1000;
                                }
                                if(speed[1].equals("Gb/s")) {
                                    unit *= 1000;
                                }
                                this.statsLock.lock();
                                this.w_BitRate = (int)(Float.parseFloat(speed[0]) * unit);
                                this.speed = this.w_BitRate;
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

    public int getSpeed() {
        int sp;

        this.statsLock.lock();
        sp = speed;
        this.statsLock.unlock();

        return sp;
    }

    public int getMTU() {
        int mtu;

        this.statsLock.lock();
        mtu = this.mtu;
        this.statsLock.unlock();

        return mtu;
    }

    public ArrayList<InetAddress> getAddresses(){
        this.addresses_Lock.lock();
        ArrayList<InetAddress> ips = new ArrayList<InetAddress>(this.addresses);
        this.addresses_Lock.unlock();

        return ips;
    }

    public ArrayList<InetAddress> getLinkLocalAddresses() {
        this.addresses_Lock.lock();
        ArrayList<InetAddress> ips = new ArrayList<InetAddress>(this.addresses);
        this.addresses_Lock.unlock();

        int size = ips.size();
        InetAddress ip;

        for (int i = 0; i < size; i++){
            ip = ips.get(0);
            if (!ip.isLinkLocalAddress()){
                ips.remove(ip);
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
