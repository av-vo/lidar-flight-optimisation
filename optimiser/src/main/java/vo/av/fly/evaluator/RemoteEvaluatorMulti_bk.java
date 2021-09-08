package vo.av.fly.evaluator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import vo.av.fly.optimiser.CmdLnArgs;
import vo.av.fly.optimiser.FlightGrid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Naive implementation of multi-evaluators.
 *
 * @author vvo
 */
public class RemoteEvaluatorMulti_bk implements Evaluator {

    private static final Logger LOG = LogManager.getLogger(RemoteEvaluatorMulti_bk.class);

    private static DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd hh:mm:ss");

    private static BufferedReader[] readers;
    private static PrintWriter[] writers;

    private static Random rand;

    private static long timeout; // m-secs

    private static int index = 0;

    //static Map<FlightGrid, Integer> resultPool; // this is not memory friendly - all results are deposited reader this map, though the objects are small and their population is not supposed to be too learge, this should be revised
    private static Map<FlightGrid, Integer> resultPool;

    private static void setReader(BufferedReader[] _readers){
        RemoteEvaluatorMulti_bk.readers = _readers;
    };

    private static void setWriter(PrintWriter[] _writers){
        RemoteEvaluatorMulti_bk.writers = _writers;
    };

    private static void setTimeout(long _timeout){
        RemoteEvaluatorMulti_bk.timeout = _timeout;
    }

    private static void initRand(){
        rand = new Random();
    }

    private static void initiateResultPool(){
        RemoteEvaluatorMulti_bk.resultPool = new ConcurrentHashMap();
    }

    /**
     * Create a builder.
     *
     * @param hostNames
     * @param portNumbers
     * @return a builder
     */
    public static Builder builder(
            String[] hostNames,
            int[] portNumbers
    ) {
        return new Builder(
                hostNames,
                portNumbers
        );
    }

    @Override
    public void passOnArgs(String[] args) {
        try {

            int numEvals = readers.length;

            // distribute input arguments to all evaluators (sequential)
            String inputArguments = concatenate(CmdLnArgs.DELIM, args);
            for(int i=0; i<numEvals; i++) {
                //writers[i].println(inputArguments);
                sendMsgToEvaluator(i, inputArguments);
                System.out.printf("%s Pass input params to E#%d\n", dtf.print(new DateTime()), i);
            }
            // wait for confirmations from all evaluators (sequential)
            for (int i=0; i<numEvals; i++){
                String msgFromServer;
                while ((msgFromServer = readMsgFromEvaluator(i)) != null) { // is this the right logic for waiting?
                    System.out.printf("%s E#%d returns '%s'\n", dtf.print(new DateTime()), i, msgFromServer);
                    long startWaiting = System.currentTimeMillis();
                    while (!msgFromServer.equals(CmdLnArgs.SETUP_COMPLETED) && System.currentTimeMillis()-startWaiting<timeout) { // keep reading until
                        msgFromServer = readMsgFromEvaluator(i);
                        System.out.printf("%s E#%d returns '%s'\n", dtf.print(new DateTime()), i, msgFromServer);
                    }
                    break; // exit loop upon receipt of a correct confirmation message
                }
            }
        } catch (IOException ex) {
            LOG.error(ex,ex);
        }
    }

    @Override
    public void requestToTerminateService() {
        try {
            int numEvals = readers.length;
            for(int i=0; i<numEvals; i++) {
                writers[i].println(CmdLnArgs.TERMINATE);
                System.out.printf("%s Request E#%d to terminate\n", dtf.print(new DateTime()), i);

                String msgFromServer;
                while ((msgFromServer = readMsgFromEvaluator(i)) != null) {
                    System.out.printf("%s E#%d returns %s\n", dtf.print(new DateTime()), i, msgFromServer);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex,ex);
        }
    }

    /**
     * Increase index.
     * Only one thread can access this
     */
    private static synchronized int increaseIndex() {
        index++;
        return index;
    }

    /**
     * Deposit result to pool.
     * Locked - only one thread can access this at a time.
     * @param flightGrid
     * @param score
     */
    private static synchronized void deposit(FlightGrid flightGrid, int score){
        resultPool.put(flightGrid, score);
    }

    /**
     * Send a message to an evaluator
     * @param i evaluator index
     * @param msg message
     */
    private static synchronized void sendMsgToEvaluator(int i, String msg){
        writers[i].println(msg);
        writers[i].flush();
    }

    /**
     * Read a message from an evaluator
     * TODO This should not be locked? Locking this will result in a sequential behaviour
     * @param i evaluator index
     * @return
     * @throws IOException
     */
    private static String readMsgFromEvaluator(int i) throws IOException {
        return readers[i].readLine();
    }

    //@Override
    public static int eval(FlightGrid flightGrid) {
        int index = increaseIndex();
        try {

            int numEvals = readers.length;
            int i = index %(numEvals);

            // send the flight grid definition to server
            System.out.printf("%s Send %s (%d) to E#%d\n", dtf.print(new DateTime()), flightGrid, index, i);

            sendMsgToEvaluator(i, String.format("%d" + CmdLnArgs.DELIM + "%d" + CmdLnArgs.DELIM + "%d", flightGrid.orientation(), flightGrid.shiftX(), flightGrid.shiftY()));
            //writers[i].println(String.format("%d" + CmdLnArgs.DELIM + "%d" + CmdLnArgs.DELIM + "%d", flightGrid.orientation(), flightGrid.shiftX(), flightGrid.shiftY()));
            //writers[i].flush();

            // wait for response from server
            int score = -Integer.MAX_VALUE;
            String msgFromServer;
            int receivedOrientation, receivedShiftX, receivedShiftY;

            boolean found = false;

            while ((msgFromServer = readMsgFromEvaluator(i)) != null) { // detect a message received from evaluator
                String[] tokens = msgFromServer.split(CmdLnArgs.DELIM);

                receivedOrientation = Integer.parseInt(tokens[0]);
                receivedShiftX = Integer.parseInt(tokens[1]);
                receivedShiftY = Integer.parseInt(tokens[2]);
                int receivedScore = Integer.parseInt(tokens[3]);

                if (receivedOrientation == flightGrid.orientation() && receivedShiftX == flightGrid.shiftX() && receivedShiftY == flightGrid.shiftY()) { // matched
                    score = receivedScore;
                    found = true;
                    System.out.printf("%s Receive [%d %d %d] -> %d from E#%d\n", dtf.print(new DateTime()), receivedOrientation, receivedShiftX, receivedShiftY, score, i);

                } else {
                    //System.writer.println(String.format("MISMATCH: Waiting for [%d, %d, %d] but receives [%d, %d, %d]", orientation, shiftX, shiftY, receivedOrientation, receivedShiftX, receivedShiftY));

                    FlightGrid receivedFlightGrid = FlightGrid.makeFlightGrid(receivedOrientation, receivedShiftX, receivedShiftY);
                    System.out.printf("%s Deposit %s %d to pool\n", dtf.print(new DateTime()), receivedFlightGrid, receivedScore);
                    deposit(receivedFlightGrid, receivedScore);
                }
                break; // do this for only one returned message
            }

            // TODO remove this logic - Given the evaluator is FIFO, this will never happen
            Integer foundScore;
            long start = System.currentTimeMillis();
            while (!found) { // while result is still not found

                foundScore = resultPool.get(flightGrid);
                if (foundScore != null) {
                    System.out.printf("%s Found %s -> %d\n", dtf.print(new DateTime()), flightGrid, foundScore);
                    score = foundScore;
                    found = true;
                    // resultPool.remove(flightGrid); // this will retain the size of the Hash Map but may penalize performance TODO
                }

                long stop = System.currentTimeMillis();
                if (stop - start > timeout) {
                    System.out.println(String.format("%s %s Timeout after %d ms", dtf.print(new DateTime()), flightGrid, (stop - start)));
                    break;
                }

                Thread.sleep(1000);
            }

            // log the result
            System.out.print("Log ");
            LOG.info(String.format(
                    "%02d %03d %03d %d",
                    flightGrid.orientation(), flightGrid.shiftX(), flightGrid.shiftY(), score
            ));

            return score;
        } catch (Exception ex) {
            LOG.error(ex, ex);
            return 0;
        }
    }

    public static final class Builder {
        private final String[] hostNames;
        private final int[] portNumbers;
        private long timeout = 1800000; // m-secs

        /**
         * Constructor.
         * @param hostNames
         * @param portNumbers
         */
        private Builder(String[] hostNames, int[] portNumbers) {
            this.hostNames = hostNames;
            this.portNumbers = portNumbers;
        }

        public Builder setTimeout(long timeout) {
            this.timeout = timeout;
            return this;
        }

        public RemoteEvaluatorMulti_bk build() {

            try {

                int numEvals = hostNames.length;

                PrintWriter writers[] = new PrintWriter[numEvals];
                BufferedReader readers[] = new BufferedReader[numEvals];

                for(int i=0; i < hostNames.length; i++){
                    Socket socket = new Socket(hostNames[i], portNumbers[i]);
                    writers[i] = new PrintWriter(socket.getOutputStream(), true);
                    readers[i] = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));

                }
                RemoteEvaluatorMulti_bk.setReader(readers);
                RemoteEvaluatorMulti_bk.setWriter(writers);
                RemoteEvaluatorMulti_bk.setTimeout(timeout);
                RemoteEvaluatorMulti_bk.initiateResultPool();
                RemoteEvaluatorMulti_bk.initRand();

                return new RemoteEvaluatorMulti_bk();

            } catch (IOException ex) {
                LOG.error(ex, ex);
                return null;
            }
        }
    }

    /**
     * Aggregate all elements of an array of String
     * @param delim
     * @param strings
     * @return
     */
    private static String concatenate(String delim, String... strings) {
        StringBuilder builder = new StringBuilder();
        for (String string : strings) {
            builder.append(string).append(delim);
        }
        return builder.toString();
    }

    /**
     * Test random number generator.
     * Ob: not always balanced.
     * @param args
     */
    /*public static void main(String[] args){
        int upperBound = 2;
        int one = 0;
        int two = 0;
        Random rnd = new Random();
        for (int i=0; i<50; i++){
            int r = rnd.nextInt(upperBound);
            if(r==1-1) one++;
            else if (r==2-1) two++;
        }
        System.out.printf("%d/%d", one, two);
    }*/
    public static void main(String[] args){
        System.out.println(5%2);
        System.out.println(dtf.print(new DateTime()));
    }
}