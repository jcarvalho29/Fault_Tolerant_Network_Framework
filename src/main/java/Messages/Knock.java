package Messages;

import java.io.Serializable;

public class Knock implements Serializable {
    public byte[] bytes;

    public  Knock(byte[] bytes){
        this.bytes = bytes;
    }
}
