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

    private ReentrantLock statsLock;

    private int speed;
    private int mtu;

    private int w_BitRate;
    private int w_TxPower;
    private int w_LinkQuality;
    private int w_SignalLevel;

    public ArrayList<InetAddress> addresses;

    private ScheduledExecutorService ipChangeCheckerSES;

    private ArrayList<ListenerMainUnicast> nicListeners_Rcv;
    private ArrayList<TransferMultiSender> nicListeners_Snd;

    public NIC(String nicName, boolean isWireless){
        this.isWireless = isWireless;
        this.name = nicName;

        this.hasConnection = false;
        this.stopChecker = true;

        this.speed = -1;
        this.mtu = -1;

        this.w_BitRate = -1;
        this.w_TxPower = -1;
        this.w_LinkQuality = 0;
        this.w_SignalLevel = 0;

        this.addresses = new ArrayList<InetAddress>();

        this.statsLock = new ReentrantLock();
        this.nicListeners_Rcv = new ArrayList<ListenerMainUnicast>();
        this.nicListeners_Snd = new ArrayList<TransferMultiSender>();

        this.ipChangeCheckerSES = Executors.newSingleThreadScheduledExecutor();

        this.ipChangeCheckerSES.schedule(this.checkIPChange,1, TimeUnit.SECONDS);
    }

    public void startIPChangeChecker(){
        this.stopChecker = false;
        this.ipChangeCheckerSES.schedule(this.checkIPChange,1, TimeUnit.SECONDS);

    }

    public void stopIPChangeChecker(){
        this.stopChecker = true;
    }

    public void registerNewLMUListener(ListenerMainUnicast lmu){
        if(!this.nicListeners_Rcv.contains(lmu))
            this.nicListeners_Rcv.add(lmu);

        if(this.stopChecker)
            startIPChangeChecker();
    }

    public void removeLMUListener(ListenerMainUnicast lmu){
        this.nicListeners_Rcv.remove(lmu);

        if(this.nicListeners_Rcv.size() + this.nicListeners_Snd.size() == 0)
            this.stopChecker = true;
    }

    public void registerNewTMSListener(TransferMultiSender tms){
        if(!this.nicListeners_Snd.contains(tms))
            this.nicListeners_Snd.add(tms);

        if(this.stopChecker)
            startIPChangeChecker();
    }

    public void removeTMSListener(TransferMultiSender tms){
        this.nicListeners_Snd.remove(tms);

        if(this.nicListeners_Rcv.size() + this.nicListeners_Snd.size() == 0)
            this.stopChecker = true;
    }

    private final Runnable checkIPChange = () -> {
        //CASE THAT THERES NO NIC

        try{
            NetworkInterface nic = NetworkInterface.getByName(this.name);

            if(nic != null) {
                //System.out.println("NOT NULL " + nic);
                boolean change = false;
                InetAddress address;

                //System.out.println("        UPDATED MTU AND HAS CONNECTION");
                Enumeration<InetAddress> inetEnum = nic.getInetAddresses();
                ArrayList<InetAddress> newAddresses = new ArrayList<InetAddress>();

                while(inetEnum.hasMoreElements()) {
                    address = inetEnum.nextElement();
                    newAddresses.add(address);
                    if(!this.addresses.contains(address)) {
                        change = true;
                    }
                }

                if(change) {
                    System.out.println("OLD => " + this.addresses);
                    this.addresses = newAddresses;
                    System.out.println("NEW => " + this.addresses);
                    this.hasConnection = true;

                    ListenerMainUnicast lmu;
                    TransferMultiSender tms;
                    int numberOfRcv = this.nicListeners_Rcv.size();
                    int numberOfSnd = this.nicListeners_Snd.size();

                    for(int i = 0; i < numberOfRcv; i++){
                        lmu = this.nicListeners_Rcv.get(i);
                        lmu.changeIP(this.addresses);
                    }

                    for(int i = 0; i < numberOfSnd; i++){
                        tms = this.nicListeners_Snd.get(i);
                        tms.changeOwnIP(this.addresses);
                    }
                }

                //UPDATE VARIABLES
                this.statsLock.lock();
                this.mtu = nic.getMTU();
                this.statsLock.unlock();

                if(this.isWireless)
                    getWirelessStatus();
                else
                    getWiredSpeed();

                if(this.speed == 1)
                    System.out.println("\t\t\tNIC SPEED SET TO 1");
                if(!hasConnection){
                    this.hasConnection = true;
                    updateNICListenersConnectionStatus(true);
                }

            }
            else{
                System.out.println("        NO NIC CONNECTION ( " + this.name + " )");
                this.mtu = 0;
                this.speed = -1;
                this.hasConnection = false;

                //ATUALIZAR O ESTADO PARA NO CONNECTION
                updateNICListenersConnectionStatus(false);
            }

        } catch (SocketException e) {
            e.printStackTrace();
        }

        if(!this.stopChecker)
            this.ipChangeCheckerSES.schedule(this.checkIPChange,1, TimeUnit.SECONDS);
        else
            System.out.println("            STOPPED IPCHANGECHECKER ( " + this.name + " )");
    };

    private void updateNICListenersConnectionStatus(boolean value){
        ListenerMainUnicast lmu;
        TransferMultiSender tms;
        int numberOfRcv = this.nicListeners_Rcv.size();
        int numberOfSnd = this.nicListeners_Snd.size();

        for(int i = 0; i < numberOfRcv; i++){
            lmu = this.nicListeners_Rcv.get(i);
            lmu.updateConnectionStatus(value);
        }

        for(int i = 0; i < numberOfSnd; i++){
            tms = this.nicListeners_Snd.get(i);
            tms.updateConnectionStatus(value);
        }
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

            int exitVal = process.waitFor();
            if (exitVal == 0) {
                String[] aux = output.toString().split("\n");

                for(int i = 0; i < aux.length; i++)
                aux[i] = aux[i].trim();

                String[] lineParams;
                boolean correctNIC = false;

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
                        /*case 5:{
                            if(correctNIC){

                            }
                            break;
                        }*/
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
}
