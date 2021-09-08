package vo.av.fly.pre;

import com.github.davidmoten.rtreemulti.Entry;
import com.github.davidmoten.rtreemulti.RTree;
import com.github.davidmoten.rtreemulti.geometry.Point;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Prune facade points from input LiDAR point cloud to mimic data acquired from a high-altitude flight.
 */
public class PruneFacadePoints {
    private static final Logger LOG = LogManager.getLogger(PruneFacadePoints.class);

    public static void main(String[] args) throws IOException {

        final CommandLine cmd = parseArgs(args);

        String inFN = cmd.getOptionValue("i");//"/home/vvo/scratch/flight-optimisation/data/pcloud/sp14.txt";
        String outFN = cmd.getOptionValue("o");//"/home/vvo/scratch/flight-optimisation/data/pcloud/sp14-out.txt";
        double searchRadius = Double.parseDouble(cmd.getOptionValue("r"));//.5;
        double tolerance = Double.parseDouble(cmd.getOptionValue("tolerance"));//.25;

        long start, stop;

        BufferedReader reader = new BufferedReader(new FileReader(inFN));

        List<Entry<Double, Point>> entries = new ArrayList();

        String line;
        start = System.currentTimeMillis();
        while( (line = reader.readLine()) != null){
            String[] tokens = line.split(",");

            Point pt = Point.create(
                    Double.parseDouble(tokens[0]),
                    Double.parseDouble(tokens[1])
            );

            entries.add(Entry.entry(Double.parseDouble(tokens[2]), pt));
        }
        reader.close();
        stop = System.currentTimeMillis();
        LOG.debug(String.format("parse input file: %d ms", stop-start));
        LOG.info(String.format("num input points: %d", entries.size()));

        start = System.currentTimeMillis();
        RTree<Double, Point> tree = RTree
                .star()
                .maxChildren(16)
                .dimensions(2)
                .create(entries); // note that the tree is 2D
        stop = System.currentTimeMillis();
        LOG.debug(String.format("build index: %d ms", stop-start));

        start = System.currentTimeMillis();
        BufferedWriter writer = new BufferedWriter(new FileWriter(outFN));
        int prunedPoints = 0;
        for (Entry<Double, Point> p : entries){
            Iterable<Entry<Double, Point>> neighbours
                    = tree.search(p.geometry(), searchRadius);

            boolean isAPeak = true;

            for(Entry<Double, Point> neighbor : neighbours){
                if(p.value() - neighbor.value() < -tolerance){
                    isAPeak = false;
                    break;
                }
            }

            if(isAPeak){
                writer.write(String.format("%.3f,%.3f,%.3f\n",
                        p.geometry().values()[0],
                        p.geometry().values()[1],
                        p.value()));
            }else{
                prunedPoints++;
            }
        }
        writer.close();
        stop = System.currentTimeMillis();
        LOG.debug(String.format("search and prune: %d ms", stop-start));

        LOG.info(String.format("num points pruned: %d", prunedPoints));
    }

    public static CommandLine parseArgs(String[] args) {
        Options options = new Options();

        Option o;

        o = new Option("i", "input", true, "input point cloud file in text format");
        options.addOption(o);

        o = new Option("o", "output", true, "output file");
        options.addOption(o);

        o = new Option("r", "radius", true, "search radius");
        options.addOption(o);

        o = new Option("tolerance",true, "tolerance");
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
            System.out.println("DEBUG ON");
        }

        return cmd;
    }


}
