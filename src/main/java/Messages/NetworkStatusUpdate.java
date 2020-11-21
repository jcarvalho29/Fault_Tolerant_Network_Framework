package Messages;

import java.io.Serializable;
import java.net.InetAddress;

public class NetworkStatusUpdate implements Serializable {
    public int transferID;
    public InetAddress newIP;

    public int firstLinkSpeed;
    public int newDPS;

    public NetworkStatusUpdate(int transferID, InetAddress newIP, int firstLinkSpeed){
        this.transferID = transferID;
        this.newIP = newIP;
        this.firstLinkSpeed = firstLinkSpeed;
        this.newDPS = -1;
    }

    public NetworkStatusUpdate(int transferID, int newDPS, InetAddress newIP){
        this.transferID = transferID;
        this.newIP = newIP;
        this.firstLinkSpeed = -1;
        this.newDPS = newDPS;
    }
}
