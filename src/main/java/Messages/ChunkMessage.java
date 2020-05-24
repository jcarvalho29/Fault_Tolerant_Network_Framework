package Messages;

import Data.Chunk;

import java.io.Serializable;

public class ChunkMessage implements Serializable {
    public int ID;
    public Chunk chunk;

    public ChunkMessage(int ID, Chunk chunk){
        this.ID = ID;
        this.chunk = chunk;
    }
}
