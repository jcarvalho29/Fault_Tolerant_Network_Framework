package Messages;

import java.io.Serializable;

public class Over implements Serializable {

    public int ID;
    public boolean isInterrupt;

    public Over(int ID, boolean isInterrupt){
        this.ID = ID;
        this.isInterrupt = isInterrupt;
    }
}
