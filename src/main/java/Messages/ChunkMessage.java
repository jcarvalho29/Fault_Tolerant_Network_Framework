package Messages;

import Data.Chunk;

import java.io.Serializable;

public class ChunkMessage implements Serializable {
    public int transferID;
    public Chunk chunk;

    public ChunkMessage(int transferID, Chunk chunk){
        this.transferID = transferID;
        this.chunk = chunk;
    }
}
