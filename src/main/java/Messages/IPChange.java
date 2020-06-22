package Messages;

import java.io.Serializable;
import java.net.InetAddress;

public class IPChange implements Serializable {
    public int transferID;
    public InetAddress newIP;

    public IPChange(int transferID, InetAddress newIP){
        this.transferID = transferID;
        this.newIP = newIP;
    }
}
