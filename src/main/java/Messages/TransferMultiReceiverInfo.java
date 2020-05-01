package Messages;

import Data.CompressedMissingChunksID;

import java.io.Serializable;

public class TransferMultiReceiverInfo implements Serializable {
    public int ID;

    public int[] ports;
    public int datagramPacketsPerSecondPerReceiver;
    public CompressedMissingChunksID cmcID;

    public TransferMultiReceiverInfo(int ID, int[] ports, int datagramPacketsPerSecondPerReceiver, CompressedMissingChunksID cmcID){
        this.ID = ID;

        this.ports = ports;
        this.datagramPacketsPerSecondPerReceiver = datagramPacketsPerSecondPerReceiver;
        this.cmcID = cmcID;
    }
}
