package vo.av.fly.evaluator;

import org.apache.commons.cli.*;
/**
 * Command line arguments
 * These are arguments for both controller and simulator - must be synchronised between the two
 */
public class CmdLnArgs {
    public final static String DELIM = ",";
    public final static String SETUP_COMPLETED = "SETUP COMPLETED";
    public final static String TERMINATE = "TERMINATE";
    public static final String MISSION_ACCOMPLISHED = "MISSION ACCOMPLISHED";

    public static CommandLine parseArgs(String[] args) {
        Options options = new Options();

        Option o;

        o = new Option("port", true, "port to listen from (optional)");
        o.setRequired(false);
        options.addOption(o);

        o = new Option("ports", true, "port numbers for remote-multi evaluators");
        o.setRequired(false);
        options.addOption(o);

        o = new Option("host", true, "hostname (optional)");
        o.setRequired(false);
        options.addOption(o);

        // input path
        o = new Option("i", "input", true, "HDFS directory or file to read from");
        o.setRequired(false);
        options.addOption(o);

        o = new Option("p", "partitions", true, "min number of partitions");
        o.setRequired(false);
        options.addOption(o);

        o = new Option("offset", true, "point cloud offset parameters");
        o.setArgs(3);
        o.setRequired(false);
        options.addOption(o);

        o = new Option("ls", "line_spacing", true, "flight line spacing");
        o.setRequired(true);
        options.addOption(o);

        o = new Option("ss", "sample_spacing", true, "LiDAR sample spacing");
        o.setRequired(true);
        options.addOption(o);

        o = new Option("alt", "altitude", true, "flight altitude");
        o.setRequired(true);
        options.addOption(o);

        o = new Option("agl", "angular_parameters", true, "scanning angles");
        o.setArgName("minA, maxA, deltaA");
        o.setArgs(3);
        o.setRequired(true);
        options.addOption(o);

        o = new Option("u", "unique_v_per_u", true, "unique v/u constraint (optional)");
        o.setArgs(0);
        o.setRequired(false);
        options.addOption(o);

        // number of reducers
        o = new Option("nr", "number_of_reducers", true, "number of reducers (optional)");
        o.setRequired(false);
        options.addOption(o);

        o = new Option("timeout", true, "maximum waiting time on fitness computation (optional)");
        o.setRequired(false);
        options.addOption(o);

        o = new Option("population_size", true, "population size");
        o.setRequired(true);
        options.addOption(o);

        o = new Option("generations", true, "max. number of generations");
        o.setRequired(true);
        options.addOption(o);

        o = new Option("nr_survivors", true, "number of survivors (optional)");
        o.setRequired(false);
        options.addOption(o);

        o = new Option("crossover_rate", true, "crossover rate");
        o.setRequired(true);
        options.addOption(o);

        o = new Option("evaluator", true, "evaluator");
        o.setRequired(true);
        options.addOption(o);

        o = new Option("codec", true, "codec");
        o.setRequired(true);
        options.addOption(o);

        o = new Option("seed", true, "seed number [0-9] (optional)");
        o.setRequired(false);
        options.addOption(o);

        o = new Option("mutation_rate", true, "mutation rate");
        o.setRequired(true);
        options.addOption(o);

        o = new Option("log", true, "path to log file");
        o.setRequired(true);
        options.addOption(o);

        o = new Option("steady_fitness_termination", true, "steady fitness termination");
        o.setRequired(true);
        options.addOption(o);

        //custom_bitvector_alterer
        o = new Option("custom_bitvector_alterer", true, "custom bitvector alterer (optional)");
        o.setArgs(0);
        o.setRequired(false);
        options.addOption(o);

        o = new Option("reverse", false, "reverse the cost function (optional)");
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
            System.out.println("DEBUG ON");
        }

        return cmd;
    }
}
