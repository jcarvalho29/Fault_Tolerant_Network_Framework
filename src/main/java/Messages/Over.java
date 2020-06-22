package Messages;

import java.io.Serializable;

public class Over implements Serializable {

    public int transferID;
    public boolean isInterrupt;

    public Over(int transferID, boolean isInterrupt){
        this.transferID = transferID;
        this.isInterrupt = isInterrupt;
    }
}
