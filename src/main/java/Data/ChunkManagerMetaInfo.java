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
        if(mi.missingChunks != null)
            this.missingChunks = new ArrayList<Integer>(mi.missingChunks);
        this.Hash = mi.Hash;
        this.HashAlgoritm = mi.HashAlgoritm;
    };
}
