package Messages;

import Data.ChunkManagerMetaInfo;

import java.io.Serializable;

public class TransferMetaInfo implements Serializable {
    public String MacAddress;
    public int ID;

    public ChunkManagerMetaInfo cmmi;
    public String DocumentName;
    public Boolean Save;
    public Boolean Confirmation;

    public TransferMetaInfo(String MacAddress, int ID, ChunkManagerMetaInfo cmmmi, boolean save, boolean confirmation) {
        this.MacAddress = MacAddress;
        this.ID = ID;

        this.cmmi = cmmmi;
        this.DocumentName = null;
        this.Save = save;
        this.Confirmation = confirmation;
    }

    public TransferMetaInfo(String MacAddress, int ID, ChunkManagerMetaInfo cmmmi, String documentName, boolean save, boolean confirmation) {
        this.MacAddress = MacAddress;
        this.ID = ID;

        this.cmmi = cmmmi;
        this.DocumentName = documentName;
        this.Save = save;
        this.Confirmation = confirmation;
    }
}
