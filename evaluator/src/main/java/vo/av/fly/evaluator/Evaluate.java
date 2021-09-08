package vo.av.fly.evaluator;

import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.PairFlatMapFunction;
import org.apache.spark.broadcast.Broadcast;
import org.locationtech.jts.geom.LineSegment;
import scala.Tuple2;
import vo.av.fly.evaluator.geom.*;

import javax.vecmath.Vector3d;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class Evaluate {

    public final static String NAME = "Flight Grid Evaluator";
    static Logger LOGGER = Logger.getLogger(Evaluate.class);

    public static void main(String[] args){
        SparkConf conf = new SparkConf()
                .setAppName(NAME);
        ///////
        // set kryo serialization
        ///////

        //TODO: prune
        conf = conf.set("spark.serializer","org.apache.spark.serializer.KryoSerializer")
                .registerKryoClasses(new Class[]{
                        ImmutableSwathSegment.class,
                        ImmutableBasePoint.class,
                        SerializableLineSegment.class,
                        SerializableTransform3D.class,
                        LinkedList.class,
                        Integer.class
                })
                .set("spark.kryo.registrationRequire", "true");

        JavaSparkContext sc = new JavaSparkContext(conf);

        final CommandLine cmd = parseArgs(args);

        /////////
        // Set up connection to evolve
        /////////
        ServerSocket serverSocket = null;

        try{
            int portNumber;
            if (cmd.hasOption("port")) {
                portNumber = Integer.parseInt(cmd.getOptionValue("port"));
                serverSocket = new ServerSocket(portNumber);
            } else {
                serverSocket = new ServerSocket(0); // search for available port
                portNumber = serverSocket.getLocalPort(); // assign the found port number back to the variable
            }

            LOGGER.info(String.format("Listening on port %d", portNumber));
            System.out.printf("Listening on port %d\n", portNumber);

            // accept client connection
            Socket clientSocket = serverSocket.accept();

            // set up reading and writing channels
            final PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
            final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));

            String msgFromEvolve, returningMsg;
            // Initiate conversation with client
            returningMsg = "CONNECTION ESTABLISHED - READY FOR COMPUTING FITNESS";
            writer.println(returningMsg); // this sends a welcome message to clients

            //////////////
            // wait for input arguments from client, process input, then notify client once setup is completed
            //////////////
            String _altitude = null;
            String _lineSpacing = null;
            String _sampleSpacing = null;
            String _inputPointCloud = null;
            String[] _aglStr = null;
            String[] _offsetStr = null;
            boolean _uniqueVPerU = true;

            int _partitions = 8;

            while ((msgFromEvolve = reader.readLine()) != null) { // detect an inbound message
                LOGGER.info(String.format("Receive input arguments from client \n%s", msgFromEvolve.replace(CmdLnArgs.DELIM, " ")));

                // parse input argument and perform the needed set up
                final String[] clientArgs = msgFromEvolve.split(CmdLnArgs.DELIM);
                final CommandLine clientCmd = CmdLnArgs.parseArgs(clientArgs);

                _offsetStr = clientCmd.hasOption("offset") ? clientCmd.getOptionValues("offset") : null;
                _altitude = clientCmd.getOptionValue("alt");
                _lineSpacing = clientCmd.getOptionValue("ls");
                _sampleSpacing = clientCmd.getOptionValue("ss");
                _inputPointCloud = clientCmd.getOptionValue("i");
                _aglStr = clientCmd.getOptionValues("agl");
                _uniqueVPerU = clientCmd.hasOption("u");

                _partitions = clientCmd.hasOption("p") ? Integer.parseInt(clientCmd.getOptionValue("p")) : 8;

                // signal the client
                returningMsg = CmdLnArgs.SETUP_COMPLETED;
                writer.println(returningMsg); // this sends a welcome message to clients
                break;
            }

            final double[] offset;
            if(_offsetStr == null){
                offset = new double[]{0,0,0};
            }else{
                offset = new double[]{
                        Double.parseDouble(_offsetStr[0]),
                        Double.parseDouble(_offsetStr[1]),
                        Double.parseDouble(_offsetStr[2])
                };
            }

            final double altitude = Double.parseDouble(_altitude);
            final double lineSpacing = Double.parseDouble(_lineSpacing);
            final double sampleSpacing = Double.parseDouble(_sampleSpacing);

            final String inputPointCloud = _inputPointCloud;
            final String[] aglStr = _aglStr;
            final double minAngle = Double.parseDouble(aglStr[0]);
            final double maxAngle = Double.parseDouble(aglStr[1]);
            final double aglResolution = Double.parseDouble(aglStr[2]);

            final boolean uniqueVPerU = _uniqueVPerU;

            final double halfSwathWidth = altitude * Math.tan(Math.max(Math.abs(minAngle), Math.abs(maxAngle)) * Math.PI / 180f);

            final int partitions = _partitions;

            //////////////
            // Keep processing fitness queries from client until receiving termination request
            //////////////
            //
            // Parse point cloud from text file
            //
            JavaRDD<ImmutableBasePoint> samples = sc.textFile(inputPointCloud, partitions).flatMap(
                    Functions.parsePoints(offset)
            );

            //TODO: how does this influence computation time?
            samples.cache(); // cache samples for later use

            while ((msgFromEvolve = reader.readLine()) != null) { // detect an inbound message
                LOGGER.info(String.format("Receive  [%s]", msgFromEvolve));

                if (msgFromEvolve.equals(CmdLnArgs.TERMINATE)) {
                    returningMsg = CmdLnArgs.MISSION_ACCOMPLISHED;
                    writer.println(returningMsg);
                    break;
                }

                String[] tokens = msgFromEvolve.split(CmdLnArgs.DELIM);

                if (tokens.length != 3) {
                    LOGGER.warn(String.format("Cannot process received message '%s'", msgFromEvolve));
                    continue;
                }

                final double orientation = Double.parseDouble(tokens[0]);
                final double shiftX = Double.parseDouble(tokens[1]);
                final double shiftY = Double.parseDouble(tokens[2]);

                /////////////
                // Apply transformations, map each sample to its base points, and then group the samples by their base points
                /////////////
                List<SerializableTransform3D> transformers = new LinkedList();
                SerializableTransform3D rotate = new SerializableTransform3D();
                rotate.rotZ(orientation * Math.PI / 180);
                transformers.add(rotate);

                SerializableTransform3D shift = new SerializableTransform3D();
                shift.setTranslation(new Vector3d(shiftX, shiftY, 0));
                transformers.add(shift);

                final Broadcast<List<SerializableTransform3D>> bcTransformers = sc.broadcast(transformers);

                JavaPairRDD<ImmutableSwathSegment, ImmutableBasePoint> pwPairs = samples
                        .flatMapToPair(
                                new PairFlatMapFunction<ImmutableBasePoint, ImmutableSwathSegment, ImmutableBasePoint>() {
                                    private static final long serialVersionUID = -2679615219585090286L;

                                    @Override
                                    public Iterator<Tuple2<ImmutableSwathSegment, ImmutableBasePoint>> call(ImmutableBasePoint sample) throws Exception {

                                        ImmutableBasePoint transformedSample = sample.transform(bcTransformers.getValue());

                                        List<Tuple2<ImmutableSwathSegment, ImmutableBasePoint>> result = new LinkedList();

                                        result.addAll(ComputeFitness.pair(true, transformedSample, lineSpacing, sampleSpacing, halfSwathWidth, altitude));
                                        result.addAll(ComputeFitness.pair(false, transformedSample, lineSpacing, sampleSpacing, halfSwathWidth, altitude));

                                        return result.iterator();// result.iterator();
                                    }

                                });

                // replace groupByKey by aggregateByKey
                JavaPairRDD<ImmutableSwathSegment, List<ImmutableBasePoint>> swathSegments = pwPairs.aggregateByKey(
                        new ArrayList<ImmutableBasePoint>(),
                        new Function2<List<ImmutableBasePoint>,ImmutableBasePoint, List<ImmutableBasePoint>>(){
                            @Override
                            public List<ImmutableBasePoint> call(List<ImmutableBasePoint>
                                                                         list,
                                                                 ImmutableBasePoint
                                                                         point) {
                                list.add(point);
                                return list;
                            }
                        },
                        new Function2<List<ImmutableBasePoint>,List<ImmutableBasePoint>, List<ImmutableBasePoint>>(){
                            @Override
                            public List<ImmutableBasePoint> call(List<ImmutableBasePoint>
                                                                         l1,
                                                                 List<ImmutableBasePoint>
                                                                         l2) {
                                l1.addAll(l2);
                                return l1;
                            }
                        }
                );

                //LOGGER.info("\n\n\n\n\n\n"+pointSlices.count());
                ////////////
                // Interpolate missing pulses
                ////////////
                JavaRDD<Integer> numberOfInterpolatedPulses = swathSegments.map(
                        new Function<Tuple2<ImmutableSwathSegment, List<ImmutableBasePoint>>, Integer>() {
                            private static final long serialVersionUID = -7233659639760373602L;

                            @Override
                            public Integer call(Tuple2<ImmutableSwathSegment, List<ImmutableBasePoint>> slice) throws Exception {
                                ImmutableSwathSegment centre = slice._1();

                                Transformer transformer = centre.flyingDirection() ? new YTransformer(centre.y()) : new XTransformer(centre.x());

                                double[] aircraftPos = new double[]{
                                        centre.x(),
                                        centre.y(),
                                        centre.z()
                                };

                                RayCasting visibilityComputation = new RayCasting(
                                        aircraftPos,
                                        aglResolution,
                                        minAngle, maxAngle,
                                        uniqueVPerU,
                                        transformer
                                );

                                //final Sample[] sampleArray = slice._2();
                                for (ImmutableBasePoint sample : slice._2()) {
                                    //LOGGER.debug(String.format("%s", sample.toString()));
                                    visibilityComputation.addXYZ(
                                            new double[]{
                                                    sample.x(),
                                                    sample.y(),
                                                    sample.z()
                                            }
                                    );
                                }
                                List<LineSegment> interpolatedPulses = visibilityComputation.generateInterpolatedPulses();
                                return interpolatedPulses.size();
                            }
                        }
                );

                ////////////
                // Aggregate the results and compute the fitness value
                ////////////
                List<Integer> result = numberOfInterpolatedPulses.collect();

                int fitness = 0;
                for (int r : result) {
                    fitness += r;
                }

                returningMsg = String.format(
                        msgFromEvolve
                                + CmdLnArgs.DELIM
                                + "%d",
                        fitness
                );

                writer.println(returningMsg);

            }
        } catch (Exception ex) {
            LOGGER.error(ex.toString());
            ex.printStackTrace();
        } finally {
            sc.stop();
            sc.close();
            try {
                serverSocket.close();

            } //System.exit(0);}
            catch (IOException ex) {
                LOGGER.error(ex);
            }
        }
    }

    private static CommandLine parseArgs(String[] args) {
        Options options = new Options();

        Option o;

        o = new Option("host", true, "host (optional)");
        o.setRequired(false);
        options.addOption(o);

        o = new Option("port", true, "port to listen from (optional)");
        o.setRequired(false);
        options.addOption(o);

        CommandLineParser parser = new PosixParser();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage() + "\n");
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(NAME + " ", options, true);
            System.exit(-1);
        }

        return cmd;
    }
}
