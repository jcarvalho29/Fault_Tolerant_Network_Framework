package Messages;

import Data.CompressedMissingChunksID;

import java.io.Serializable;

public class MissingChunksID implements Serializable {
    public int ID;
    public CompressedMissingChunksID cmcID;
    public Byte DatagramsPerSecondPerSender;
    public MissingChunksID(int ID, CompressedMissingChunksID cmcID, byte dpsps){
        this.ID = ID;
        this.cmcID = cmcID;
        this.DatagramsPerSecondPerSender = dpsps;
    }
}
