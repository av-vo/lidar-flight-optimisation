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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Naive implementation of multi-evaluators.
 *
 * @author vvo
 */
public class RemoteEvaluatorMulti implements Evaluator {

    private static final Logger LOG = LogManager.getLogger(RemoteEvaluatorMulti.class);

    protected static DateTimeFormatter DTF = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

    //private static BufferedReader[] readers;
    //private static PrintWriter[] writers;

    private static Conn[] connections;

    private static Random rand;

    private static long timeout = 7200000; // m-secs //TODO leave this for user input

    private static int index = 0;
    //private static final AtomicInteger counter = new AtomicInteger(0);

    //static Map<FlightGrid, Integer> resultPool; // this is not memory friendly - all results are deposited reader this map, though the objects are small and their population is not supposed to be too learge, this should be revised
    private static Map<FlightGrid, Integer> resultPool;

    //private static void setReader(BufferedReader[] _readers){
    //    RemoteEvaluatorMulti.readers = _readers;
    //};

    //private static void setWriter(PrintWriter[] _writers){
    //    RemoteEvaluatorMulti.writers = _writers;
    //};

    private static void setConnections(Conn[] conns){
        RemoteEvaluatorMulti.connections = conns;
    }

    //private static void setTimeout(long _timeout){
    //    RemoteEvaluatorMulti.timeout = _timeout;
    //}

    //private static void initRand(){
    //    rand = new Random();
    //}

    private static void initiateResultPool(){
        RemoteEvaluatorMulti.resultPool = new ConcurrentHashMap();
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

            int numEvals = connections.length;

            // distribute input arguments to all evaluators (sequential)
            String inputArguments = concatenate(CmdLnArgs.DELIM, args);
            // TODO this is currently sequential
            for(int i=0; i<numEvals; i++) {
                //writers[i].println(inputArguments);
                String msg = connections[i].sendAndReceiveLocked(inputArguments);
                msg = connections[i].receive();
                //System.out.printf("\n\n\n %s \n\n\n", msg);
                assert(msg.equals(CmdLnArgs.SETUP_COMPLETED)); //TODO do we need a logic to handle unexpected message here?
                System.out.printf("Connection to E#%d successfully set up\n", i);
                //sendMsgToEvaluator(i, inputArguments);
                //System.out.printf("%s Pass input params to E#%d\n", dtf.print(new DateTime()), i);
            }
            // wait for confirmations from all evaluators (sequential)
            //for (int i=0; i<numEvals; i++){
            //    while ((msgFromServer = connections[i].receive()) != null) { // is this the right logic for waiting?
            //        System.out.printf("%s E#%d returns '%s'\n", dtf.print(new DateTime()), i, msgFromServer);
            //        long startWaiting = System.currentTimeMillis();
            //        while (!msgFromServer.equals(CmdLnArgs.SETUP_COMPLETED) && System.currentTimeMillis()-startWaiting<timeout) { // keep reading until
            //            msgFromServer = readMsgFromEvaluator(i);
            //            System.out.printf("%s E#%d returns '%s'\n", dtf.print(new DateTime()), i, msgFromServer);
            //        }
            //        break; // exit loop upon receipt of a correct confirmation message
            //    }
            //}
        } catch (IOException | InterruptedException ex) {
            LOG.error(ex,ex);
        }
    }

    @Override
    public void requestToTerminateService() {
        try {
            int numEvals = connections.length;
            for(int i=0; i<numEvals; i++) {
                String msg = connections[i].sendAndReceiveLocked(CmdLnArgs.TERMINATE);
                assert(msg.equals(CmdLnArgs.MISSION_ACCOMPLISHED));

                //writers[i].println(CmdLnArgs.TERMINATE);
                //System.out.printf("%s Request E#%d to terminate\n", dtf.print(new DateTime()), i);

                //String msgFromServer;
                //while ((msgFromServer = readMsgFromEvaluator(i)) != null) {
                //    System.out.printf("%s E#%d returns %s\n", dtf.print(new DateTime()), i, msgFromServer);
                //}
            }
        } catch (IOException | InterruptedException ex) {
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


    //public static int getIndex() {
    //    return counter.get();
    //}

    //public static void increment() {
    //    while(true) {
    //        int existingValue = getIndex();
    //        int newValue = existingValue + 1;
    //        if(counter.compareAndSet(existingValue, newValue)) {
    //            return;
    //        }
    //    }
    //}

    /**
     * Deposit result to pool.
     * //TODO to verify: do not need a lock here because the result pool is a ConcurrentHashMap?
     * @param flightGrid
     * @param score
     */
    private static void deposit(FlightGrid flightGrid, int score){
        resultPool.put(flightGrid, score);
    }

    //@Override
    public static int eval(FlightGrid flightGrid) {
        //increment();
        //int index = getIndex();

        int index = increaseIndex();

        try {

            int numEvals = connections.length;
            int i = index %(numEvals);

            // send the flight grid definition to server
            //System.out.printf("%s Send %s (%d) to E#%d\n", dtf.print(new DateTime()), flightGrid, index, i);

            String msg = connections[i].sendAndReceiveLocked(
                    String.format(
                            "%d" + CmdLnArgs.DELIM + "%d" + CmdLnArgs.DELIM + "%d",
                            flightGrid.orientation(), flightGrid.shiftX(), flightGrid.shiftY()
                    )
            );

            //sendMsgToEvaluator(i, String.format("%d" + CmdLnArgs.DELIM + "%d" + CmdLnArgs.DELIM + "%d", flightGrid.orientation(), flightGrid.shiftX(), flightGrid.shiftY()));
            //writers[i].println(String.format("%d" + CmdLnArgs.DELIM + "%d" + CmdLnArgs.DELIM + "%d", flightGrid.orientation(), flightGrid.shiftX(), flightGrid.shiftY()));
            //writers[i].flush();

            // wait for response from server

            int score = -Integer.MAX_VALUE;
            boolean found = false;

            if(msg!=null){
                String[] tokens = msg.split(CmdLnArgs.DELIM);
                int receivedOrientation = Integer.parseInt(tokens[0]);
                int receivedShiftX = Integer.parseInt(tokens[1]);
                int receivedShiftY = Integer.parseInt(tokens[2]);
                int receivedScore = Integer.parseInt(tokens[3]);

                if (receivedOrientation == flightGrid.orientation()
                        && receivedShiftX == flightGrid.shiftX()
                        && receivedShiftY == flightGrid.shiftY()) { // matched
                    score = receivedScore;
                    found = true;
                    //System.out.printf("%s Receive [%d %d %d] -> %d from E#%d\n", dtf.print(new DateTime()), receivedOrientation, receivedShiftX, receivedShiftY, score, i);

                } else { // TODO Would this ever happen?
                    FlightGrid receivedFlightGrid = FlightGrid.makeFlightGrid(receivedOrientation, receivedShiftX, receivedShiftY);
                    System.out.printf("%s --> %s -> %d L&F\n", DTF.print(new DateTime()), receivedFlightGrid, receivedScore);
                    deposit(receivedFlightGrid, receivedScore);
                }
            }else{
                System.out.printf("!!! E#%d: NULL", i);
            }

            // TODO this logic is unnecessary?
            Integer foundScore;
            long start = System.currentTimeMillis();
            while (!found) { // keep searching the result pool

                foundScore = resultPool.get(flightGrid);
                if (foundScore != null) {
                    System.out.printf(
                            "%s <-- L&F %s -> %d\n",
                            DTF.print(new DateTime()), flightGrid, foundScore);
                    score = foundScore;
                    found = true;
                    // resultPool.remove(flightGrid); // this will retain the size of the Hash Map but may penalize performance TODO
                }

                long stop = System.currentTimeMillis();
                if (stop - start > timeout) {
                    System.out.printf("%s !!! Lost %s, timeout after %d ms", DTF.print(new DateTime()), flightGrid, (stop - start));
                    break;
                }

                Thread.sleep(1000);
            }

            // log the result
            // System.out.print("Log ");
            LOG.info(String.format(
                    "[%d] %d,%d,%d,%d",
                    index,
                    flightGrid.orientation(), flightGrid.shiftX(), flightGrid.shiftY(), score
            ));

            //System.out.printf("%s === complete [%d]",  dtf.print(new DateTime()), index);
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

        public RemoteEvaluatorMulti build() {

            try {

                int numEvals = hostNames.length;

                Conn[] _conns = new Conn[numEvals];
                //PrintWriter writers[] = new PrintWriter[numEvals];
                //BufferedReader readers[] = new BufferedReader[numEvals];

                for(int i=0; i < hostNames.length; i++){
                    Socket socket = new Socket(hostNames[i], portNumbers[i]);
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));
                    Conn conn = new Conn(reader, writer, timeout, i);
                    _conns[i] = conn;
                }
                RemoteEvaluatorMulti.setConnections(_conns);
                //RemoteEvaluatorMulti.setReader(readers);
                //RemoteEvaluatorMulti.setWriter(writers);
                //RemoteEvaluatorMulti.setTimeout(timeout);
                RemoteEvaluatorMulti.initiateResultPool();
                //RemoteEvaluatorMulti.initRand();

                return new RemoteEvaluatorMulti();

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
        System.out.println(DTF.print(new DateTime()));
    }


}