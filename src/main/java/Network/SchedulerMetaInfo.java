package Network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class SchedulerMetaInfo implements Serializable {
    // Tabela de <IPs / <Prioridade / ArrayList <informação> > > a ser enviada
    public HashMap<Integer, Transmission> scheduledTransmissions;
    public HashMap<Integer, Transmission> onGoingTransmissions;
    public HashMap<Integer, Transmission> finishedTransmissions;

    public String Root;

    public SchedulerMetaInfo(String Root){
        this.Root = Root;
        this.scheduledTransmissions = new HashMap<Integer, Transmission>();
        this.onGoingTransmissions = new HashMap<Integer, Transmission>();
        this.finishedTransmissions = new HashMap<Integer, Transmission>();
    }
}
