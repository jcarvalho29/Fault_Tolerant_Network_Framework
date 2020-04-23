import java.io.IOException;
import java.net.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
public class Teste implements Runnable {

    public Teste(){}

    private Runnable sendPing = () -> {
        try {
            MulticastSocket mcs = new MulticastSocket();
            DatagramPacket dp = new DatagramPacket(new byte[2], 2, InetAddress.getByName("FF02:0:0:0:0:0:0:175"), 3000);
            mcs.send(dp);
        } catch (IOException e) {
            e.printStackTrace();
        }
    };

    @Override
    public void run() {
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        ses.scheduleWithFixedDelay(sendPing, 0, 4, TimeUnit.SECONDS);
        DatagramPacket dp = new DatagramPacket(new byte[2], 2);
        try {
            DatagramSocket ds = new DatagramSocket(4000);
            while (true) {
                ds.receive(dp);
                System.out.println("RECEBI UNICAST DE " + dp.getAddress().toString());
            }

        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}
