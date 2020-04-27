package Network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class SchedulerMetaInfo implements Serializable {
    // Tabela de <IPs / <Prioridade / ArrayList <informação> > > a ser enviada
    public HashMap<String, HashMap<Integer, ArrayList<Transmission>>> infoByIP_Priority;

    public String Root;

    public SchedulerMetaInfo(String Root){
        this.Root = Root + "Network/";
        this.infoByIP_Priority = new HashMap<String, HashMap<Integer, ArrayList<Transmission>>>();
    }
}
