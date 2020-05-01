package Messages;

import Data.CompressedMissingChunksID;

public class MissingChunksID {
    public int ID;
    public CompressedMissingChunksID cmcID;

    public MissingChunksID(int ID, CompressedMissingChunksID cmcID){
        this.ID = ID;
        this.cmcID = cmcID;
    }
}
