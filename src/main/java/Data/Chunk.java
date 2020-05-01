package Data;

import java.io.Serializable;

public class Chunk implements Serializable {


    private byte[] Chunk;
    private int place;

    public Chunk(){}

    public Chunk(byte[] data, int p){
        this.Chunk = data;
        this.place = p;
    }

    public byte[] getChunk(){
        return this.Chunk;
    }

    public int getPlace(){
        return this.place;
    }

}
