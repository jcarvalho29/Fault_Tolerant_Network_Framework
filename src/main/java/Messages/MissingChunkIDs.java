package Messages;

import Data.CompressedMissingChunksID;

import java.io.Serializable;

public class MissingChunkIDs implements Serializable {
    public int ID;
    public CompressedMissingChunksID cmcID;
    public int DatagramsPerSecondPerSender;

    public MissingChunkIDs(int ID, CompressedMissingChunksID cmcID, int dpsps){
        this.ID = ID;
        this.cmcID = cmcID;
        this.DatagramsPerSecondPerSender = dpsps;
    }
}
