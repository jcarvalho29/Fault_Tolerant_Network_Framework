package Data;

import java.io.Serializable;
import java.util.ArrayList;

public class ChunkManagerMetaInfo implements Serializable {
    public int datagramMaxSize;
    public int numberOfChunks;
    public int numberOfChunksInArray;
    public long chunksSize;
    public boolean full;
    public boolean[] missingChunks;
    public String Hash;
    public String HashAlgorithm;

    public ChunkManagerMetaInfo(){};

    public ChunkManagerMetaInfo(ChunkManagerMetaInfo mi){
        this.datagramMaxSize = mi.datagramMaxSize;
        this.numberOfChunks = mi.numberOfChunks;
        this.numberOfChunksInArray = mi.numberOfChunksInArray;
        this.chunksSize = mi.chunksSize;
        this.full = mi.full;
        this.missingChunks = new boolean[this.numberOfChunks];
        if(mi.missingChunks != null)
            System.arraycopy(mi.missingChunks, 0, this.missingChunks, 0, mi.numberOfChunks);
        this.Hash = mi.Hash;
        this.HashAlgorithm = mi.HashAlgorithm;
    };

    public void print(){
        System.out.println("Datagram Max Size " + datagramMaxSize);
        System.out.println("Number of Chunks " + numberOfChunks);
        System.out.println("Number of Chunks in Array " + numberOfChunksInArray);
        System.out.println("Chunks Size " + chunksSize);
        System.out.println("Full " + full);
        System.out.println("Hash " + Hash);
        System.out.println("Hash Algorithm " + HashAlgorithm);
    }
}
