package Network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class SchedulerMetaInfo implements Serializable {
    // Tabela de <IPs / <Prioridade / ArrayList <informação> > > a ser enviada
    public HashMap<String, HashMap<Integer, ArrayList<Transmission>>> infoByIP_Priority;
    public ArrayList<String> Hashs;

    public String Root;

    public SchedulerMetaInfo(String Root){
        this.Root = Root;
        this.infoByIP_Priority = new HashMap<String, HashMap<Integer, ArrayList<Transmission>>>();
        this.Hashs = new ArrayList<String>();
    }
}
