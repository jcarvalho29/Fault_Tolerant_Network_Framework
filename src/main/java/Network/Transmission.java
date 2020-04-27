package Network;

import java.io.Serializable;

public class Transmission implements Serializable {
    public String infoHash;
    public int port;

    public Transmission(String infoHash, int port){
        this.infoHash = infoHash;
        this.port = port;
    }
}
