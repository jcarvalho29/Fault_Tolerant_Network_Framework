package Network;

public class TransmitterStats {
    private long tmiSendTime;
    private long trmiReceiveTime;

    public int handshakeRTT;


    public void markTmiSendTime(){
        this.tmiSendTime = System.currentTimeMillis();
    }

    public void setTrmiReceiveTime(long timestamp){
        this.trmiReceiveTime = timestamp;
        calculateHandshakeRTT();
    }
    private void calculateHandshakeRTT(){
        this.handshakeRTT = (int)(this.tmiSendTime - this.trmiReceiveTime);
    }

}
