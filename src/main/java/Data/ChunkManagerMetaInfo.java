package Data;

import java.io.Serializable;
import java.util.ArrayList;

public class ChunkManagerMetaInfo implements Serializable {
    public int datagramMaxSize;
    public int numberOfChunks;
    public int numberOfChunksInArray;
    public long chunksSize;
    public boolean full;
    public ArrayList<Integer> missingChunks;
    public String Hash;
    public String HashAlgoritm;

    public ChunkManagerMetaInfo(){};

    public ChunkManagerMetaInfo(ChunkManagerMetaInfo mi){
        this.datagramMaxSize = mi.datagramMaxSize;
        this.numberOfChunks = mi.numberOfChunks;
        this.numberOfChunksInArray = mi.numberOfChunksInArray;
        this.chunksSize = mi.chunksSize;
        this.full = mi.full;
        this.missingChunks = (ArrayList<Integer>) mi.missingChunks.clone();
        this.Hash = mi.Hash;
        this.HashAlgoritm = mi.HashAlgoritm;
    };
}
