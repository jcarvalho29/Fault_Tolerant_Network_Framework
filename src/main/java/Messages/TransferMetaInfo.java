package Messages;

import Data.ChunkManagerMetaInfo;

import java.io.Serializable;

public class TransferMetaInfo implements Serializable {
    public int nodeIdentifier;
    public int transferID;

    public int firstLinkSpeed;
    public byte numberOfCPUCores;

    public ChunkManagerMetaInfo cmmi;
    public String DocumentName;
    public Boolean Confirmation;

/*    public TransferMetaInfo(int nodeIdentifier, int transferID, boolean isWireless, ChunkManagerMetaInfo cmmmi, boolean confirmation) {
        this.nodeIdentifier = nodeIdentifier;
        this.transferID = transferID;

        //this. firstLinkSpeed = firstLinkSpeed;
        //this.isWireless = isWireless;

        this.cmmi = cmmmi;
        this.DocumentName = null;
        this.Confirmation = confirmation;
    }*/

    public TransferMetaInfo(int nodeIdentifier, byte numberOfCPUCores, int transferID, ChunkManagerMetaInfo cmmi, String documentName, boolean confirmation) {
        this.nodeIdentifier = nodeIdentifier;
        this.transferID = transferID;

        this. firstLinkSpeed = 10;
        this.numberOfCPUCores = numberOfCPUCores;

        this.cmmi = cmmi;
        this.DocumentName = documentName;
        this.Confirmation = confirmation;
    }

    public void setFirstLinkConnection(int firstLinkSpeed) {
        this.firstLinkSpeed = firstLinkSpeed;
        //this.isWireless = isWireless;
    }
}
