package vo.av.fly.evaluator;

import org.apache.commons.cli.*;
import org.apache.log4j.Level;
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
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Evaluate one single flight grid
 */
public class ComputeFitness2 {

    static Logger LOG = Logger.getLogger(ComputeFitness2.class);

    public static void main(String[] args) throws IOException {

        final CommandLine cmd = parseArgs(args);

        final double[] offset;
        if(!cmd.hasOption("offset")){
            offset = new double[]{0,0,0};
        }else{
            String[] offsetStr = cmd.getOptionValues("offset");
            offset = new double[]{
                Double.parseDouble(offsetStr[0]),
                Double.parseDouble(offsetStr[1]),
                Double.parseDouble(offsetStr[2])
            };
        }

        final double lineSpacing = Double.parseDouble(cmd.getOptionValue("ls"));
        final double sampleSpacing = Double.parseDouble(cmd.getOptionValue("ss"));
        final double altitude = Double.parseDouble(cmd.getOptionValue("alt"));
        final double halfSwathWidth = altitude / 2 * 1.5;
        final String[] algs = cmd.getOptionValues("agl");
        final double minAngle = Double.parseDouble(algs[0]);
        final double maxAngle = Double.parseDouble(algs[1]);
        final double aglResolution = Double.parseDouble(algs[2]);
        final boolean uniqueVPerU = cmd.hasOption("u");


        long start = System.currentTimeMillis();
        JavaSparkContext sc = new JavaSparkContext(new SparkConf().setAppName("FO - Compute Fitness"));

        /////////////////
        // Parse input arguments
        /////////////////
        String inputFN = cmd.getOptionValue("i");

        final int partitions = cmd.hasOption("p") ? Integer.parseInt(cmd.getOptionValue("p")) : 8;

        final double orientation = Double.parseDouble(cmd.getOptionValue("orientation"));
        String[] shiftStr = cmd.getOptionValues("shift");
        final double shiftX = Double.parseDouble(shiftStr[0]);
        final double shiftY = Double.parseDouble(shiftStr[1]);

        /////////////
        // Parse point cloud from text file
        /////////////
        JavaRDD<ImmutableBasePoint> samples = sc.textFile(inputFN, partitions).flatMap(
                Functions.parsePoints(offset)
        );


        /////////////
        // Apply transformations, map each sample to its base points, and then group the samples by their base points
        /////////////
        List<SerializableTransform3D> transformations = new LinkedList();
        SerializableTransform3D rotate = new SerializableTransform3D();
        rotate.rotZ(orientation * Math.PI / 180);
        transformations.add(rotate);

        SerializableTransform3D shift = new SerializableTransform3D();
        shift.setTranslation(new Vector3d(shiftX, shiftY, 0));
        transformations.add(shift);

        final Broadcast<List<SerializableTransform3D>> transformationsBroadcast = sc.broadcast(transformations);

        JavaPairRDD<ImmutableSwathSegment, ImmutableBasePoint> pwPairs = samples
                .flatMapToPair(
                        new PairFlatMapFunction<ImmutableBasePoint, ImmutableSwathSegment, ImmutableBasePoint>() {
                            private static final long serialVersionUID = -2679615219585090286L;

                            @Override
                            public Iterator<Tuple2<ImmutableSwathSegment, ImmutableBasePoint>> call(ImmutableBasePoint sample) throws Exception {
                                ImmutableBasePoint transformedSample = sample.transform(
                                        transformationsBroadcast.getValue()
                                );

                                List<Tuple2<ImmutableSwathSegment, ImmutableBasePoint>> result = new LinkedList();

                                result.addAll(
                                        pair(
                                                true,
                                                transformedSample,
                                                lineSpacing,
                                                sampleSpacing,
                                                halfSwathWidth,
                                                altitude
                                        )
                                );
                                result.addAll(
                                        pair(
                                                false,
                                                transformedSample,
                                                lineSpacing,
                                                sampleSpacing,
                                                halfSwathWidth,
                                                altitude
                                        )
                                );

                                return result.iterator();// result.iterator();
                            }

                        });

        /*
        JavaPairRDD<ImmutableSwathSegment, Iterable<ImmutableBasePoint>> swathSegments = pwPairs.aggregateByKey(
                new ArrayList<ImmutableBasePoint>(),
                new Function2<Iterable<ImmutableBasePoint>,ImmutableBasePoint, Iterable<ImmutableBasePoint>>(){
                    @Override
                    public Iterable<ImmutableBasePoint> call(Iterable<ImmutableBasePoint>
                                                                     immutableBasePoints,
                                                             ImmutableBasePoint
                                                                     immutableBasePoint) throws Exception {

                        return null;
                    }
                },
                new Function2<Iterable<ImmutableBasePoint>,Iterable<ImmutableBasePoint>, Iterable<ImmutableBasePoint>>(){
                    @Override
                    public Iterable<ImmutableBasePoint> call(Iterable<ImmutableBasePoint> immutableBasePoints, Iterable<ImmutableBasePoint> immutableBasePoints2) throws Exception {
                        return null;
                    }
                }
        );*/

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

        //LOGGER.info("\n\n\n\n\n\n"+swathSegments.count());
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

                        LOG.debug(String.format("\nAircraft position %s", centre.toString()));

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

        if(cmd.hasOption("result_log")){
            BufferedWriter writer = new BufferedWriter(new FileWriter(cmd.getOptionValue("result_log")));
            writer.write(String.valueOf(fitness));
            writer.flush();
            writer.close();
        }
        LOG.info(
                String.format("\n-----\nScore: %d\nElapsed time: %d ms\n-----", fitness, System.currentTimeMillis() - start)
        );
    }

    /**
     *
     * @param flyingDirection
     * @param sample
     * @param lineSpacing
     * @param sampleSpacing
     * @param halfSwathWidth
     * @param altitude
     * @return
     */
    public static List<Tuple2<ImmutableSwathSegment, ImmutableBasePoint>> pair(
            final boolean flyingDirection,
            final ImmutableBasePoint sample,
            final double lineSpacing,
            final double sampleSpacing,
            final double halfSwathWidth,
            final double altitude
    ) {
        final double anchor = flyingDirection ? sample.x() : sample.y();
        final double coordinate = flyingDirection ? sample.y() : sample.x();

        final double closestLine = Math.round(anchor / lineSpacing) * lineSpacing;
        final double closestSample = Math.round(coordinate / sampleSpacing) * sampleSpacing;

        List< Tuple2<ImmutableSwathSegment, ImmutableBasePoint>> result = new LinkedList();

        //final SampleArray sampleArray = new SampleArray(new Sample[]{sample});
        // moving left
        int i = 0;
        while (true) {
            double lineAnchor = closestLine - (lineSpacing * (i++));
            if (anchor - lineAnchor <= halfSwathWidth) {

                ImmutableSwathSegment basePoint = ImmutableSwathSegment.valueOf(
                        flyingDirection,
                        flyingDirection ? lineAnchor : closestSample,
                        flyingDirection ? closestSample : lineAnchor,
                        altitude
                );

                Tuple2<ImmutableSwathSegment, ImmutableBasePoint> tuple = new Tuple2(basePoint, sample);
                result.add(tuple);
            } else {
                break;
            }
        }

        // moving right
        i = 1;
        while (true) {
            double lineAnchor = closestLine + (lineSpacing * (i++));
            if (lineAnchor - anchor <= halfSwathWidth) {
                final ImmutableSwathSegment basePoint = ImmutableSwathSegment.valueOf(
                        flyingDirection,
                        flyingDirection ? lineAnchor : closestSample,
                        flyingDirection ? closestSample : lineAnchor,
                        altitude
                );

                Tuple2<ImmutableSwathSegment, ImmutableBasePoint> tuple = new Tuple2(basePoint, sample);
                result.add(tuple);
            } else {
                break;
            }
        }

        return result;
    }

    public static CommandLine parseArgs(String[] args) {
        Options options = new Options();

        Option o;

        // input path
        o = new Option("i", "input", true, "input directory");
        options.addOption(o);

        o = new Option("p", "partitions", true, "min number of partitions");
        o.setRequired(false);
        options.addOption(o);

        o = new Option("offset", true, "point cloud offset parameters");
        o.setArgs(3);
        o.setRequired(false);
        options.addOption(o);

        o = new Option("ls", "line_spacing", true, "flight line spacing");
        options.addOption(o);

        o = new Option("ss", "sample_spacing", true, "sample spacing");
        options.addOption(o);

        o = new Option("alt", "altitude", true, "flight altitude");
        options.addOption(o);

        o = new Option("agl", "angular_parameters", true, "angular parameters");
        o.setArgName("minA, maxA, deltaA");
        o.setArgs(3);
        options.addOption(o);

        o = new Option("orientation", true, "orientation");
        options.addOption(o);

        o = new Option("shift", true, "shift");
        o.setArgs(2);
        options.addOption(o);

        o = new Option("result_log", true, "result log (optional)");
        o.setRequired(false);
        options.addOption(o);

        o = new Option("u", "unique_v_per_u", true, "unique u/v constraint");
        o.setArgs(0);
        o.setRequired(false);
        options.addOption(o);

        // debug flag
        options.addOption("d", "debug", false, "switch on DEBUG log level");

        CommandLineParser parser = new PosixParser();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage() + "\n");
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(" ", options, true);
            System.exit(-1);
        }

        if (cmd.hasOption("d")) {
            LOG.setLevel(Level.DEBUG);
            System.out.println("DEBUG ON");
        }

        return cmd;
    }
}
