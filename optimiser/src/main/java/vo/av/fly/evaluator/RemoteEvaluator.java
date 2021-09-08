package vo.av.fly.evaluator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import vo.av.fly.optimiser.CmdLnArgs;
import vo.av.fly.optimiser.FlightGrid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joda.time.DateTime;

/**
 * This class encapsulates the fitness computation on the client side. The
 * implementation is not stateless due to the use of the shared {
 *
 * @ resultPool}. {@code ConcurrentHashMap} is used to ensure synchronization
 * between threads but this solution is not optimal.
 *
 * @author vvo
 */
public class RemoteEvaluator implements Evaluator {

    private static final Logger LOG = LogManager.getLogger(RemoteEvaluator.class);

    private static BufferedReader reader;
    private static PrintWriter writer;

    private static long timeout; // m-secs

    //static Map<FlightGrid, Integer> resultPool; // this is not memory friendly - all results are deposited reader this map, though the objects are small and their population is not supposed to be too learge, this should be revised
    private static Map<FlightGrid, Integer> resultPool;


    private static void setReader(BufferedReader _reader){
        RemoteEvaluator.reader = _reader;
    };

    private static void setWriter(PrintWriter _writer){
        RemoteEvaluator.writer = _writer;
    };

    private static void setTimeout(long _timeout){
        RemoteEvaluator.timeout = _timeout;
    }

    private static void initiateResultPool(){
        RemoteEvaluator.resultPool = new ConcurrentHashMap();
    }

    /**
     * Create a builder.
     *
     * @param hostName
     * @param portNumber
     * @return a builder
     */
    public static Builder builder(String hostName, int portNumber) {
        return new Builder(hostName, portNumber);
    }

    @Override
    public void passOnArgs(String[] args) {
        try {
            String inputArguments = concatenate(CmdLnArgs.DELIM, args); //TODO: test
            writer.println(inputArguments);

            // wait for confirmation from server that all set up are done
            String msgFromServer;
            while ((msgFromServer = reader.readLine()) != null) { // is this the right logic for waiting?
                System.out.println(String.format("%s Server returns '%s' ", new DateTime(), msgFromServer));
                long startWaiting = System.currentTimeMillis();
                while (!msgFromServer.equals(CmdLnArgs.SETUP_COMPLETED) && System.currentTimeMillis()-startWaiting<timeout) { // keep reading until
                    msgFromServer = reader.readLine();
                    System.out.println(String.format("%s Server returns '%s' ", new DateTime(), msgFromServer));
                }
                break; // exit loop upon receipt of a correct confirmation message
            }
        } catch (IOException ex) {
            LOG.error(ex,ex);
        }
    }

    @Override
    public void requestToTerminateService() {
        try {
            writer.println(CmdLnArgs.TERMINATE);
            String msgFromServer;
            while ((msgFromServer = reader.readLine()) != null) {
                System.out.println(String.format("%s Server returns %s", new DateTime(), msgFromServer));
            }
        } catch (IOException ex) {
            LOG.error(ex,ex);
        }
    }

    //@Override
    public static int eval(FlightGrid flightGrid) {
        try {
            // send the flight grid definition to server
            System.out.println(String.format("%s Send    %s to server", new DateTime(), flightGrid));

            writer.println(String.format("%d" + CmdLnArgs.DELIM + "%d" + CmdLnArgs.DELIM + "%d", flightGrid.orientation(), flightGrid.shiftX(), flightGrid.shiftY()));
            writer.flush();

            // wait for response from server
            int score = -Integer.MAX_VALUE;
            String msgFromServer;
            int receivedOrientation, receivedShiftX, receivedShiftY;

            boolean found = false;

            while ((msgFromServer = reader.readLine()) != null) { // detect a message received from the server
                String[] tokens = msgFromServer.split(CmdLnArgs.DELIM);

                receivedOrientation = Integer.parseInt(tokens[0]);
                receivedShiftX = Integer.parseInt(tokens[1]);
                receivedShiftY = Integer.parseInt(tokens[2]);
                int receivedScore = Integer.parseInt(tokens[3]);

                if (receivedOrientation == flightGrid.orientation() && receivedShiftX == flightGrid.shiftX() && receivedShiftY == flightGrid.shiftY()) { // matched
                    score = receivedScore;
                    found = true;
                    System.out.println(String.format("%s Receive score of %d for [%d, %d, %d] from server", new DateTime(), score, receivedOrientation, receivedShiftX, receivedShiftY));

                } else {
                    //System.writer.println(String.format("MISMATCH: Waiting for [%d, %d, %d] but receives [%d, %d, %d]", orientation, shiftX, shiftY, receivedOrientation, receivedShiftX, receivedShiftY));

                    FlightGrid receivedFlightGrid = FlightGrid.makeFlightGrid(receivedOrientation, receivedShiftX, receivedShiftY);
                    System.out.println(String.format("%s Deposit %s %d to result pool", new DateTime(), receivedFlightGrid, receivedScore));
                    resultPool.put(receivedFlightGrid, receivedScore);
                }
                break; // do this for only one returned message
            }

            Integer foundScore;
            long start = System.currentTimeMillis();
            while (!found) { // while result is still not found

                foundScore = resultPool.get(flightGrid);
                if (foundScore != null) {
                    System.out.println(String.format("%s Found   %s with score of %d from the result pool", new DateTime(), flightGrid, foundScore));
                    score = foundScore;
                    found = true;
                    // resultPool.remove(flightGrid); // this will retain the size of the Hash Map but may penalize performance TODO
                }

                long stop = System.currentTimeMillis();
                if (stop - start > timeout) {
                    System.out.println(String.format("%s %s Timeout after %d ms", new DateTime(), flightGrid, (stop - start)));
                    break;
                }

                Thread.sleep(1000);
            }

            // log the result
            LOG.info(String.format(
                    "%02d\t%03d\t%03d\t%d",
                    flightGrid.orientation(), flightGrid.shiftX(), flightGrid.shiftY(), score
            ));

            return score;
        } catch (Exception ex) {
            LOG.error(ex, ex);
            return 0;
        }
    }

    public static final class Builder {
        private final String hostName;
        private final int portNumber;
        private long timeout = 1800000; // m-secs

        /**
         * Constructor.
         * @param hostName
         * @param portNumber
         */
        private Builder(String hostName, int portNumber) {
            this.hostName = hostName;
            this.portNumber = portNumber;
        }

        public Builder setTimeout(long timeout) {
            this.timeout = timeout;
            return this;
        }

        public RemoteEvaluator build() {

            try {
                Socket socket = new Socket(hostName, portNumber);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));

                // set state for the class
                RemoteEvaluator.setReader(reader);
                RemoteEvaluator.setWriter(writer);
                RemoteEvaluator.setTimeout(timeout);
                RemoteEvaluator.initiateResultPool();

                return new RemoteEvaluator();

            } catch (IOException ex) {
                LOG.error(ex, ex);
                return null;
            }

        }
    }

    /**
     * Aggregate all elements of an array of String
     *
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
}