package Network;

public class TransmitterStats {
    private long tmiSendTime;
    private long trmiReceiveTime;

    public int handshakeRTT;

    public TransmitterStats(){
    }

    public void setTmiSendTime(long timestamp){
        this.tmiSendTime = timestamp;
    }

    public void setTrmiReceiveTime(long timestamp){
        this.trmiReceiveTime = timestamp;
        calculateHandshakeRTT();
    }
    private void calculateHandshakeRTT(){
        this.handshakeRTT = (int)(this.trmiReceiveTime - this.tmiSendTime);
        System.out.println("CALCULATING RTT " + this.trmiReceiveTime + " - " + this.tmiSendTime + " = " + this.handshakeRTT);
    }

}
