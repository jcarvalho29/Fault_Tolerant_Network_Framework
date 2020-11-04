package Network;

import java.io.Serializable;
import java.net.InetAddress;

public class Transmission implements Serializable {
    public int transferID;
    public String infoHash;
    public InetAddress destIP;
    public int destPort;
    public  boolean confirmation;

    public Transmission(int transferID, String infoHash, InetAddress destIP, int destPort, boolean confirmation){
        this.transferID = transferID;
        this.infoHash = infoHash;
        this.destIP = destIP;
        this.destPort = destPort;
        this.confirmation = confirmation;
    }
}
