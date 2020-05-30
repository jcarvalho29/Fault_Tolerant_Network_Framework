package Data;

import java.io.Serializable;

public class Chunk implements Serializable {


    public byte[] Chunk;
    public int place;

    public Chunk(){}

    public Chunk(byte[] data, int p){
        this.Chunk = data;
        this.place = p;
    }
}
