package vo.av.fly.evaluator;

import org.joda.time.DateTime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

import static vo.av.fly.evaluator.RemoteEvaluatorMulti.DTF;

public class Conn {

    //private static DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");


    BufferedReader reader;
    PrintWriter writer;
    int connIdx;
    long timeout;


    public Conn(BufferedReader reader, PrintWriter writer, long timeout, int i){
        this.reader = reader;
        this.writer = writer;
        this.timeout = timeout;
        this.connIdx = i;
    }


    public synchronized String sendAndReceiveLocked(String outgoingMsg) throws IOException, InterruptedException {
        synchronized (this){ // lock the connection from other threads
            send(outgoingMsg);
            String incomingMsg = receive();
            return incomingMsg;
        }
    }

    public void send(String outgoingMsg){
        System.out.printf("%s >>> E#%d: %s\n", DTF.print(new DateTime()), connIdx, outgoingMsg);
        writer.println(outgoingMsg);
        writer.flush();
    }

    public String receive() throws IOException, InterruptedException {
        String incomingMsg;

        long start = System.currentTimeMillis();
        while((incomingMsg = reader.readLine())==null){
            long current = System.currentTimeMillis();
            long waitTime = (long)(current-start)/1000;
            if(waitTime > timeout){
                System.out.printf("%s !!! E#%d timeout after %ds\n", DTF.print(new DateTime()), connIdx, waitTime);
                return null;
            }
            Thread.sleep(100); // pause for 1 second until the next check
            System.out.printf("%s ... E#%d: %ds\n", DTF.print(new DateTime()), connIdx, waitTime);
        }
        System.out.printf("%s <<< E#%d: %s\n", DTF.print(new DateTime()), connIdx, incomingMsg);
        return incomingMsg;
    }
}
