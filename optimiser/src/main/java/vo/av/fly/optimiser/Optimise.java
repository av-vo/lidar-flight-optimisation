package vo.av.fly.optimiser;

import io.jenetics.*;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.util.ISeq;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import vo.av.fly.evaluator.DummyEvaluator;
import vo.av.fly.evaluator.Evaluator;
import vo.av.fly.evaluator.RemoteEvaluator;
import vo.av.fly.evaluator.RemoteEvaluatorMulti;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.function.Function;

import static io.jenetics.engine.EvolutionResult.toBestPhenotype;
import static io.jenetics.engine.Limits.bySteadyFitness;

public class Optimise {
    private static final Logger LOG = LogManager.getLogger(Optimise.class);

    public static void main(String[] args) throws IOException {

        CommandLine cmd = CmdLnArgs.parseArgs(args);

        ///////////////////////
        // Create evaluator and fitness function
        ///////////////////////
        Evaluator evaluator;
        Function<FlightGrid, Integer> fitnessFunction;

        switch (cmd.getOptionValue("evaluator")) {
            case "dummy":
                evaluator = new DummyEvaluator();
                fitnessFunction = DummyEvaluator::eval;
                break;
            case "remote":
                String hostName = "localhost";
                if (cmd.hasOption("host")) {
                    hostName = cmd.getOptionValue("host");
                }

                int portNumber = 51303; //default
                //ServerSocket serverSocket;
                if (cmd.hasOption("port")) {
                    portNumber = Integer.parseInt(cmd.getOptionValue("port"));
                    //serverSocket = new ServerSocket(portNumber);
                } else {
                    try {
                        ServerSocket serverSocket = new ServerSocket(0); // search for available port
                        portNumber = serverSocket.getLocalPort(); // assign the found port number back to the variable
                        serverSocket.close();
                        System.out.println(String.format("Direct messages to port %d",portNumber));
                    } catch (IOException ex) {
                        java.util.logging.Logger.getLogger(Optimise.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
                    }
                }

                //System.out.println("Hit enter when the server side is up.");
                //Scanner scanner = new Scanner(System.in);
                //scanner.nextLine();

                RemoteEvaluator.Builder evaluatorBuilder = RemoteEvaluator
                        .builder(hostName, portNumber);
                if (cmd.hasOption("timeout")) {
                    evaluatorBuilder.setTimeout(Long.parseLong(cmd.getOptionValue("timeout")));
                }
                evaluator = evaluatorBuilder.build();
                fitnessFunction = RemoteEvaluator::eval;
                break;

            case "remote-multi":

                String portNums = cmd.getOptionValue("ports");
                String[] portsStr = portNums.split(",");
                int numEvals = portsStr.length;
                int[] ports = new int[numEvals];
                String[] hosts = new String[numEvals];
                for(int i=0; i<numEvals; i++){
                    ports[i] = Integer.parseInt(portsStr[i]);
                    hosts[i]="localhost"; // TODO leave this for user input
                }

                RemoteEvaluatorMulti.Builder builder = RemoteEvaluatorMulti
                        .builder(hosts, ports);
                if (cmd.hasOption("timeout")) {
                    builder.setTimeout(Long.parseLong(cmd.getOptionValue("timeout")));
                }

                evaluator = builder.build();
                fitnessFunction = RemoteEvaluatorMulti::eval;
                break;
            default:
                LOG.error(String.format("Invalid evaluator name of %s", cmd.getOptionValue("evaluator")));
                //return -1;
                return;
        }

        ///////////////////////
        // Create evolution engine
        ///////////////////////
        Engine.Builder engineBuilder;

        switch (cmd.getOptionValue("codec")) {
            case "bitvector":
                BitVectorChromosome.setLineSpacing(
                        Integer.parseInt(cmd.getOptionValue("ls"))
                ); //TODO refine
                engineBuilder = Engine.builder(
                        fitnessFunction,
                        Codecs.bitVectorCodec()
                );
                break;
            case "numeric":
                engineBuilder = Engine.builder(
                        fitnessFunction,
                        Codecs.numericCodec(Integer.parseInt(cmd.getOptionValue("ls")))
                );
                break;

            default:
                LOG.error(String.format("Invalid codec name of %s", cmd.getOptionValue("codec")));
                //return -2;
                return;
        }

        // pass the input arguments to the Spark driver
        evaluator.passOnArgs(args);

        // set the needed parameters for GA
        // required configurations
        final int populationSize = Integer.parseInt(cmd.getOptionValue("population_size"));
        engineBuilder
                .populationSize(populationSize)
                .offspringSelector(new RouletteWheelSelector<>()) // the probability of producing offspring is proportional to the individual's fitness
                .optimize(cmd.hasOption("reverse")? Optimize.MINIMUM : Optimize.MAXIMUM);

        // optional configurations
        if (cmd.hasOption("nr_survivors")) {
            engineBuilder.survivorsSelector(new TournamentSelector<>(Integer.parseInt(cmd.getOptionValue("nr_survivors"))));// the number of survivors to the next generation
        }
        else{
            engineBuilder.survivorsSelector(new EliteSelector<>());
        }
        // alterers
        switch (cmd.getOptionValue("codec")) {
            case "bitvector":

                if (cmd.hasOption("custom_bitvector_alterer")) {
                    System.out.println("Custom Bitvector mean alterer");
                    engineBuilder.alterers(
                            new Mutator<>(Double.parseDouble(cmd.getOptionValue("mutation_rate"))),
                            new CustomisedAlterer(Double.parseDouble(cmd.getOptionValue("crossover_rate")))// MultiPointCrossover<>(Double.parseDouble(cmd.getOptionValue("crossover_rate")))
                    );
                } else {
                    engineBuilder.alterers(
                            new Mutator<>(Double.parseDouble(cmd.getOptionValue("mutation_rate"))),
                            new MultiPointCrossover<>(Double.parseDouble(cmd.getOptionValue("crossover_rate")))
                    );
                }
                break;
            case "numeric":
                engineBuilder.alterers(
                        new Mutator<>(Double.parseDouble(cmd.getOptionValue("mutation_rate"))),
                        //new MeanAlterer(),
                        //new
                        new IntermediateCrossover<>(Double.parseDouble(cmd.getOptionValue("crossover_rate")))
                );
                break;

            default:
                LOG.error(String.format("Invalid codec name of %s", cmd.getOptionValue("codec")));
                //return -2;
                return;
        }

        // all configurations set, build the evolution engine
        Engine engine = engineBuilder.build();

        ///////////////////////
        // Start the evolution process
        ///////////////////////
        //configureLogger(cmd.getOptionValue("log"));

        // register this execution with the logger
        StringBuilder sB = new StringBuilder();
        sB.append(String.format(
                "\n+---------------------------------------------------------------------------+\n"));
        for (String arg : args) {
            sB.append(String.format(arg.startsWith("-") ? "\n%s " : "%s ", arg));
        }
        sB.append(("\n+---------------------------------------------------------------------------+\n"));

        LOG.info(sB.toString());

        final EvolutionStatistics<Double, ?> statistics = EvolutionStatistics.ofNumber();

        Phenotype bestPhenotype;

        //LOG.info("\ntimestamp\torientation\tshiftx\tshifty\tscore");

        if (cmd.hasOption("seed")) {
            // this ignores populationSize
            bestPhenotype = evolve(
                    cmd, engine, fitnessFunction, statistics
            );

        } else {
            bestPhenotype
                    = (Phenotype) engine.stream()
                    .limit(bySteadyFitness(Integer.parseInt(cmd.getOptionValue("steady_fitness_termination"))))
                    .limit(Integer.parseInt(cmd.getOptionValue("generations"))) // truncate the stream after some number of generations
                    .peek(statistics)
                    .collect(toBestPhenotype());
        }

        /////////////////
        // Clean up
        /////////////////
        LOG.info(String.format("\n%s\n%s\n", statistics.toString(), bestPhenotype.toString()));

        evaluator.requestToTerminateService();
    }



    /**
     * Perform controlled evolution.
     *
     * @param cmd
     * @param engine
     * @param fitnessFunction
     * @return
     */
    private static Phenotype evolve(
            final CommandLine cmd,
            final Engine engine,
            final Function<FlightGrid, Integer> fitnessFunction,
            final EvolutionStatistics statistics
    ) throws IOException {
        int seedNumber = Integer.parseInt(cmd.getOptionValue("seed"));
        //LOG.info("\ntimestamp\tgeneration_idx\torientation\tshiftx\tshifty\tscore");
        //throw new UnsupportedOperationException("No support for fix seed yet.");

        if (!cmd.getOptionValue("codec").equals("bitvector")) {
            throw new UnsupportedOperationException("Fixed seed mode is only possible with bitvector encoding.");
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(
                cmd.getOptionValue("log")
        ));

        final ISeq<Phenotype> firstPopulation = SeedGenerator.seedSetAsBitVectors(seedNumber, fitnessFunction, Codecs.bitVectorCodec());

        LOG.info(String.format("initial population: %d", firstPopulation.size()));

        StringBuilder builder = new StringBuilder();
        //builder.append(String.format("%d\t",generationIdx));
        for (int i = 1; i <= firstPopulation.size(); i++) {
            builder.append(String.format(i == 1 ? "%d" : "\t%d", i));
        }

        writer.write(builder.toString());
        writer.write("\n");

        EvolutionResult evolutionResult = null;
        Phenotype latestBestPhenotype = null;

        int nrSteadyGeneration = 0;
        double currentAvgFitness = 0.001;

        for (int generationIdx = 1; generationIdx <= Integer.parseInt(cmd.getOptionValue("generations")); generationIdx++) {
            boolean foundANewBest = false;

            if (generationIdx == 1) {
                evolutionResult = engine.evolve(firstPopulation, generationIdx);
                latestBestPhenotype = evolutionResult.getBestPhenotype();
            } else {
                evolutionResult = engine.evolve(evolutionResult.getPopulation(), generationIdx);
                Phenotype bestPhenotypeOfGeneration = evolutionResult.getBestPhenotype();

                if (bestPhenotypeOfGeneration.getFitness().compareTo(latestBestPhenotype.getFitness()) > 0) {
                    latestBestPhenotype = bestPhenotypeOfGeneration;
                    foundANewBest = true;
                }
            }
            statistics.accept(evolutionResult);

            ISeq<Phenotype> evolvedPopulation = evolutionResult.getPopulation();
            builder = new StringBuilder();
            //builder.append(String.format("%d\t",generationIdx));
            int i = 1;

            double avgFitness = 0;

            if(generationIdx == 1){
                for(Phenotype p : firstPopulation){
                    builder.append(String.format(i++ == 1 ? "%d" : "\t%d", p.getFitness()));
                }
                builder.append("\n");
            }


            i=1;
            for (Phenotype p : evolvedPopulation) {
                avgFitness += (Integer) p.getFitness();
                builder.append(String.format(i++ == 1 ? "%d" : "\t%d", p.getFitness()));
            }

            avgFitness = avgFitness / evolvedPopulation.size();

            double variation = generationIdx == 1 ? Double.MAX_VALUE : avgFitness / currentAvgFitness;

            if (foundANewBest || (variation > 1.05 || variation < 1f / 1.05)) { // there is signification variation
                currentAvgFitness = avgFitness;
                nrSteadyGeneration = 0;
            } else {
                //System.out.println("Steady " + generationIdx);
                nrSteadyGeneration++;
            }

            writer.write(builder.toString());
            writer.write("\n");

            writer.flush();

            if (nrSteadyGeneration > Integer.parseInt(cmd.getOptionValue("steady_fitness_termination"))) {
                break;
            }
        }

        writer.close();

        return latestBestPhenotype;
    }

}
