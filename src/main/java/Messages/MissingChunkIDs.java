package Messages;

import Data.CompressedMissingChunksID;

import java.io.Serializable;

public class MissingChunkIDs implements Serializable {
    public int transferID;
    public CompressedMissingChunksID cmcID;
    public int DatagramsPerSecondPerSender;

    public MissingChunkIDs(int transferID, CompressedMissingChunksID cmcID, int dpsps){
        this.transferID = transferID;
        this.cmcID = cmcID;
        this.DatagramsPerSecondPerSender = dpsps;
    }
}
