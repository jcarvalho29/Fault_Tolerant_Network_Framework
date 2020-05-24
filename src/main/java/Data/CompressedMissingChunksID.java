package Data;

import java.io.Serializable;

public class CompressedMissingChunksID implements Serializable {

    public byte[] increments;
    public boolean[] toAdd;
    public int referenceID;

    public CompressedMissingChunksID(int referenceID, boolean[] toAdd, byte[] increments){
        this.increments = increments;
        this.toAdd = toAdd;
        this.referenceID = referenceID;
    }
}
