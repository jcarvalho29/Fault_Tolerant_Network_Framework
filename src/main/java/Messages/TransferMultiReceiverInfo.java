package Messages;

import Data.CompressedMissingChunksID;

import java.io.Serializable;

public class TransferMultiReceiverInfo implements Serializable {
    public int transferID;

    public int[] ports;
    public int datagramPacketsPerSecondPerReceiver;
    public CompressedMissingChunksID cmcID;

    public TransferMultiReceiverInfo(int transferID, int[] ports, int datagramPacketsPerSecondPerReceiver, CompressedMissingChunksID cmcID){
        this.transferID = transferID;

        this.ports = ports;
        this.datagramPacketsPerSecondPerReceiver = datagramPacketsPerSecondPerReceiver;
        this.cmcID = cmcID;
    }
}
