package Network;

import java.util.ArrayList;
import java.util.Date;

public class ReceiverStats {

    private int MTU;
    private int NICCapacity;
    private int numberOfListeners;
    private long numberOfMissingChunks;

    private final int averageDPSize;
    // Receptor => medido entre o envio do TMRI e a chegada do primeiro ChunkMessage
    private long trmiSendTime;
    private long firstChunkReceiveTime;

    public int handshakeRTT;

    //RTT medido entre ciclos do bloco Transfer devido a retransmissões
    private ArrayList<Long> MCIDsSendTime;
    private ArrayList<Long> firstRetransmittedCMReceiveTime;

    public ArrayList<Integer> retransmissionRTT;

    /* % perdas total medidas no Receptor podem ser medidas por perdas
        No UpLink:
            => TMRI (Visto pela chegada repetida de TMI)
            => MissingChunksIDS (Um grupo de Identificadores que fazia parte da mesma estrutura tem que ser enviado de novo no ciclo seguinte)

        No DownLink:
            => CM (sabe-se quantos CM são supostos chegar e quantos chegaram)
    */
    public int dpReceived;
    public int dpExpected;

    //perdas por ciclo do bloco Transfer
    //Medições destas perdas, relativamente a uma Transferencia, vao diferenciar no Transmissor e no Receptor
    public ArrayList<Long> dpReceived_PerTransferCycle;
    public ArrayList<Long> dpExpected_PerTransferCycle;
    public ArrayList<Float> drops_PerTransferCycle;

    private ArrayList<Long> transferCycleStartTime;
    private ArrayList<Long> transferCycleEndTime;
    public ArrayList<Integer> transferCyclesDuration;

    public long protocolStartTime;
    public long protocolEndTime;

    //Velocidade da tranferência
    public long transferStartTime;
    public long transferEndTime;
    public long bytesReceived;

    public ArrayList<Integer> dpsPerCycle;

    public ReceiverStats(int MTU, int NICCapacity, int numberOfListeners, int numberOfMissingChunks){
        this.MTU = MTU;
        this.NICCapacity = NICCapacity;
        this.numberOfListeners = numberOfListeners;
        this.numberOfMissingChunks = numberOfMissingChunks;

        this.averageDPSize = MTU - 100;

        this.protocolStartTime = 0;
        this.protocolEndTime = 0;

        this.transferStartTime = 0;
        this.transferEndTime = 0;
        this.bytesReceived = 0;

        this.dpExpected_PerTransferCycle = new ArrayList<Long>();
        this.dpReceived_PerTransferCycle = new ArrayList<Long>();
        this.drops_PerTransferCycle = new ArrayList<Float>();
        this.transferCycleStartTime = new ArrayList<Long>();
        this.transferCycleEndTime = new ArrayList<Long>();
        this.transferCyclesDuration = new ArrayList<Integer>();

        this.dpExpected = 0;
        this.dpReceived = 0;

        this.trmiSendTime = 0;
        this.firstChunkReceiveTime = 0;
        this.handshakeRTT = -1;

        this.MCIDsSendTime = new ArrayList<Long>();
        this.firstRetransmittedCMReceiveTime = new ArrayList<Long>();
        this.retransmissionRTT = new ArrayList<Integer>();

        this.dpsPerCycle = new ArrayList<Integer>();
        calculateDPS();

    }

    public void markProtocolStartTime(){
        this.protocolStartTime = System.currentTimeMillis();
    }

    public void markProtocolEndTime(){
        this.protocolEndTime = System.currentTimeMillis();
    }

    public void markTransferStartTime(){
        this.transferStartTime = System.currentTimeMillis();
    }

    public void markTransferEndTime(){
        this.transferEndTime = System.currentTimeMillis();
    }

    public void markTrmiSendTime(){
        this.trmiSendTime = System.currentTimeMillis();
    }

    public void setFirstChunkReceivedTime(long timestamp){
        this.firstChunkReceiveTime = timestamp;
        calculateHandshakeRTT();
    }

    private void calculateHandshakeRTT(){
        this.handshakeRTT = (int)(this.firstChunkReceiveTime - this.trmiSendTime);
    }

    public void markMCIDsSendTime(){
        long time = System.currentTimeMillis();
        if(this.MCIDsSendTime.size() == this.firstRetransmittedCMReceiveTime.size() + 1){
            this.MCIDsSendTime.remove(this.MCIDsSendTime.size()-1);
        }
        this.MCIDsSendTime.add(time);

    }

    public void markFirstRetransmittedCMReceivedTime(long time){
        this.firstRetransmittedCMReceiveTime.add(time);
        calculateRetransmissionRTT();
    }

    private void calculateRetransmissionRTT(){
        int pointer = this.firstRetransmittedCMReceiveTime.size()-1;
        long receiveTime = this.firstRetransmittedCMReceiveTime.get(pointer);
        long sendTime = this.MCIDsSendTime.get(pointer);

        this.retransmissionRTT.add((int)(receiveTime - sendTime));
    }

    public int getAverageRTT(){
        int avg = 0;
        int nSamples = 2;
        long rcvSum = 0;
        long sndSum = 0;

        if(this.firstRetransmittedCMReceiveTime.size() == 0)
            avg = this.handshakeRTT;
        else {
            int i;

            if (this.MCIDsSendTime.size() != this.firstRetransmittedCMReceiveTime.size())
                i = this.MCIDsSendTime.size()-2;

            else
                i = this.MCIDsSendTime.size()-1;

            for(int j = nSamples; (i >= 0) && (j > 0); i--, j--)
                sndSum += this.MCIDsSendTime.get(i);

            i = this.firstRetransmittedCMReceiveTime.size()-1;
            int j = nSamples;
            for(; (i >= 0) && (j > 0); i--, j--)
                rcvSum += this.firstRetransmittedCMReceiveTime.get(i);

            avg = ((int)(rcvSum - sndSum)) / (nSamples - j);
        }

        return avg;
    }

    private void addToDPExpected(long numDP){
        this.dpExpected += numDP;
    }

    private void addToDPReceived(long numDP){
        this.dpReceived += numDP;
        this.bytesReceived += (numDP * this.averageDPSize);
    }

    public float dropPercentage(){
        long dropped = this.dpExpected - this.dpReceived;

        return ((float)dropped / (float)this.dpExpected) * 100;
    }

    public void registerDPReceivedInCycle(long numDP){
        this.dpReceived_PerTransferCycle.add(numDP);
        addToDPReceived(numDP);
        updateMissingChunks(numDP);

        long dropped = this.dpExpected_PerTransferCycle.get(this.dpExpected_PerTransferCycle.size()-1) - numDP;
        this.drops_PerTransferCycle.add(((float) dropped / (float) this.dpExpected_PerTransferCycle.get(this.dpExpected_PerTransferCycle.size()-1)) * 100);
    }

    public void registerDPExpectedInCycle(long numDP){
        this.dpExpected_PerTransferCycle.add(numDP);
        addToDPExpected(numDP);
    }

    public void markTransferCycleBeginning(){
        this.transferCycleStartTime.add(System.currentTimeMillis());
    }

    public void setTransferCycleBeginning(long timestamp){
        this.transferCycleStartTime.add(timestamp);
    }

    public void markTransferCycleEnding(){

        long time = System.currentTimeMillis();
        //System.out.println(this.transferCycleStartTime);
        int duration =(int) (time - this.transferCycleStartTime.get(this.transferCycleStartTime.size()-1));//!!!! EXCEPTION OUT OF BOUNDS FIRST CYCLE
        //System.out.println(time + " - " + this.transferCycleStartTime.get(this.transferCycleStartTime.size()-1) + " = " + duration);
        this.transferCycleEndTime.add(time);

        //System.out.println("START TIME " + this.transferCycleStartTime);
        //System.out.println("END TIME " + this.transferCycleEndTime);

        this.transferCyclesDuration.add(duration);
    }

    public float bytesPerSecondProtocol(){
        float percent;
        if(this.protocolEndTime == 0)
            percent = (float)this.bytesReceived / (float)(System.currentTimeMillis() - this.protocolStartTime);
        else
            if((this.protocolEndTime - this.protocolStartTime) == 0)
                percent = (float)(this.bytesReceived);
            else
                percent = (float)this.bytesReceived / (float)(this.protocolEndTime - this.protocolStartTime);

        return percent*1000;
    }

    public float bytesPerSecondTransfer(){
        float percent;

        if(this.transferEndTime == 0)
            percent = (float)this.bytesReceived / (float)(System.currentTimeMillis() - this.transferStartTime);
        else
            if((this.transferEndTime - this.transferStartTime) == 0)
                percent = (float)(this.bytesReceived);
            else
                percent = (float)this.bytesReceived / (float)(this.transferEndTime - this.transferStartTime);

        return percent*1000;
    }

    public int getNumberOfTransferCycles(){
        return this.transferCycleStartTime.size();
    }

    public void calculateDPS(){
        int capacityInDPS = ((this.NICCapacity*1000000)/(this.averageDPSize*8));

        int maxDPSPerListener;// = Math.min(capacityInDPS/this.numberOfListeners, 5000);
        maxDPSPerListener = Math.max(capacityInDPS/this.numberOfListeners, 1);

        if(this.numberOfMissingChunks/capacityInDPS > 20) {
            System.out.println("                    Multipliquei por 0.85");
            maxDPSPerListener *= .80;
        }
        //else
            //maxDPSPerListener *= .90;

        //aqui é para ser feito o calculo do novo DPS tendo em conta todos os dados disponíveis!!!!
        this.dpsPerCycle.add(maxDPSPerListener);

    }

    public int getDPS(){
        return this.dpsPerCycle.get(this.dpsPerCycle.size()-1);
    }
    private void updateMissingChunks(long receivedChunks){
        this.numberOfMissingChunks -= receivedChunks;
    }
    public void printStats(){
        System.out.println("Drop % => " + dropPercentage() + "%");
        System.out.println("Handshake RTT => " + this.handshakeRTT + " ms");
        System.out.println("Average RTT => " + getAverageRTT() + " ms");
        System.out.println(this.MCIDsSendTime);
        System.out.println(this.firstRetransmittedCMReceiveTime);
        System.out.println("Protocol Speed => " + bytesPerSecondProtocol()/1024 + " KB/s");
        System.out.println("Transfer Speed => " + bytesPerSecondTransfer()/1024 + " KB/s");

        System.out.println("Drops per Transfer Cycle:");

        for(int i = 0; i < this.transferCycleEndTime.size(); i++){
            System.out.println("    Cycle " + i + ")\n      Duration => " + this.transferCyclesDuration.get(i) + " ms \n        Chunks Received => " + this.dpReceived_PerTransferCycle.get(i) + " / " + this.dpExpected_PerTransferCycle.get(i) + "( " + this.drops_PerTransferCycle.get(i) + " %)\n       DPS => " + this.dpsPerCycle.get(i));
        }
    }
}
