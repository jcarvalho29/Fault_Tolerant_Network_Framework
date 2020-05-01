package Messages;

import Data.Chunk;

import java.io.Serializable;

public class ChunkHeader implements Serializable {
    public int ID;
    public Chunk chunk;

    public ChunkHeader(int ID, Chunk chunk){
        this.ID = ID;
        this.chunk = chunk;
    }
}
