package Data;

public class CompressedMissingChunksID {

    public byte[] increments;
    public byte[] toAdd;
    public int referenceID;

    public CompressedMissingChunksID(int referenceID, byte[] toAdd, byte[] increments){
        this.increments = increments;
        this.toAdd = toAdd;
        this.referenceID = referenceID;
    }
}
