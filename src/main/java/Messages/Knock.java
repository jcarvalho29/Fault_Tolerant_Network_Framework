package Messages;

import java.io.Serializable;

public class Knock implements Serializable {
    public int responsePort;
    public byte[] bytes;

    public  Knock(int responsePort, byte[] bytes){
        this.responsePort = responsePort;
        this.bytes = bytes;
    }
}
