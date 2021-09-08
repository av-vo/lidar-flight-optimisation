package vo.av.fly.optimiser;

import io.jenetics.*;
import io.jenetics.internal.util.Hash;
import io.jenetics.util.ISeq;
import io.jenetics.util.MSeq;
import io.jenetics.util.RandomRegistry;

import java.util.Random;

import static java.lang.String.format;

/**
 * Customised alterer - for testing the default bit sequence cross over only
 *
 * @author vvo
 * @param <G>
 * @param <C>
 */

public final class CustomisedAlterer<
        G extends Gene<?, G>, C extends Comparable<? super C>>
        extends Recombinator<G, C> {

    /**
     * Constructs an alterer with a given recombination probability.
     *
     * @param probability the crossover probability.
     * @throws IllegalArgumentException if the {@code probability} is not in the
     * valid range of {@code [0, 1]}.
     */
    public CustomisedAlterer(final double probability) {
        super(probability, 2);
    }

    /**
     * Create a new alterer with alter probability of {@code 0.05}.
     */
    public CustomisedAlterer() {
        this(0.05);
    }

    @Override
    protected int recombine(
            final MSeq<Phenotype<G, C>> population,
            final int[] individuals,
            final long generation
    ) {
        final Random random = RandomRegistry.getRandom();

        final Phenotype<G, C> pt1 = population.get(individuals[0]);
        final Phenotype<G, C> pt2 = population.get(individuals[1]);
        final Genotype<G> gt1 = pt1.getGenotype();
        final Genotype<G> gt2 = pt2.getGenotype();

        //Choosing the Chromosome index for crossover.
        final int cindex = 0; // random.nextInt(min(gt1.length(), gt2.length()));

        final MSeq<Chromosome<G>> c1 = gt1.toSeq().copy();
        final ISeq<Chromosome<G>> c2 = gt2.toSeq();

        // Calculate the mean value of the gene array.
        BitVectorChromosome chromosome1 = (BitVectorChromosome) gt1.getChromosome();
        BitVectorChromosome chromosome2 = (BitVectorChromosome) gt2.getChromosome();

        final int segmentIdx = random.nextInt(3); //

        FlightGrid grid1 = chromosome1.toFlightGrid();
        FlightGrid grid2 = chromosome2.toFlightGrid();

        int meanSegmentValue;

        FlightGrid meanGrid;

        switch (segmentIdx) {
            case 0:
                meanGrid = FlightGrid.makeFlightGrid(
                        (int) Math.round((grid1.orientation() + grid2.orientation()) / 2f),
                        grid1.shiftX(),
                        grid1.shiftY());
                break;
            case 1:
                meanGrid = FlightGrid.makeFlightGrid(
                        grid1.orientation(),
                        (int) Math.round((grid1.shiftX() + grid2.shiftX()) / 2f),
                        grid1.shiftY());
                break;
            case 2:
                meanGrid = FlightGrid.makeFlightGrid(
                        grid1.orientation(),
                        grid1.shiftX(),
                        (int) Math.round((grid1.shiftY() + grid2.shiftY()) / 2f)
                );
                break;
            default:
                meanGrid = FlightGrid.makeFlightGrid(
                        (int) Math.round((grid1.orientation() + grid2.orientation()) / 2f),
                        grid1.shiftX(),
                        grid1.shiftY());
        }

        Genotype meanGenotype = Genotype.of(
                new BitVectorChromosome(meanGrid)
        );

        // create a phenotype
        population.set(
                individuals[0],
                pt1.newInstance(meanGenotype, generation)
        );

        return 1;
    }


    @Override
    public int hashCode() {
        return Hash.of(getClass()).and(super.hashCode()).value();
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof CustomisedAlterer && super.equals(obj);
    }

    @Override
    public String toString() {
        return format("%s[p=%f]", getClass().getSimpleName(), _probability);
    }

}