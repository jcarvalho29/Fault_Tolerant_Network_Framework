package Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class DataManagerMetaInfo implements Serializable {
    public String Root;

    //public HashMap<String, ArrayList<String>> macHashs;

    public HashMap <String, Boolean> isDocumentFull;
    public HashMap <String, String> documentsNames;
    public ArrayList<String> cmHashs;
    public HashMap<String, Boolean> isMessageFull;

    public DataManagerMetaInfo(String Root){
        this.Root = Root;

        //this.macHashs = new HashMap<String, ArrayList<String>>();

        this.cmHashs = new ArrayList<String>();
        this.isDocumentFull = new HashMap<String, Boolean>();
        this.documentsNames = new HashMap<String, String>();

        this.isMessageFull = new HashMap<String, Boolean>();
    }
}
