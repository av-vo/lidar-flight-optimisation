package vo.av.fly.optimiser;

import io.jenetics.BitGene;
import io.jenetics.BitVectorChromosome;
import io.jenetics.Genotype;
import io.jenetics.Phenotype;
import io.jenetics.engine.Codec;
import io.jenetics.util.ISeq;
import io.jenetics.util.MSeq;
import io.jenetics.util.RandomRegistry;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * Fixed sets of seeds to ensure replicable results
 */
public class SeedGenerator {

    static Codec<FlightGrid, BitGene> codec;
    static Function<FlightGrid, Integer> f;

    protected static final int[][] ORIENTATION = new int[][]{
            new int[]{63, 5, 69, 60},
            new int[]{58, 13, 20, 40},//seed #1
            new int[]{16, 75, 25, 21},
            new int[]{67, 71, 52, 47},
            new int[]{84, 20, 32, 75},
            new int[]{10, 52, 6, 27},
            new int[]{86, 17, 53, 5},
            new int[]{56, 60, 74, 47},
            new int[]{47, 71, 57, 67},
            new int[]{24, 3, 72, 2},};

    /*
    protected static final int[][] SHIFTX = new int[][]{
            new int[]{4, 39},//, -38, 4},
            new int[]{33, -33},//, -6},//, 27},
            new int[]{10, -37},//, -16},//, -26},
            new int[]{-13, 0},//, 33},//, 30},
            new int[]{10, -36},//, 0},//, -24},
            new int[]{-37, -27},//, -4},//, 11},
            new int[]{5, -22},//, -33},//, 12},
            new int[]{-10, -25},//, 23},//, -2},
            new int[]{-25, 16},//, 27},//, -27},
            new int[]{-34, -3},//, -22},//, 1}
    };*/


    protected static final int[][] SHIFTX = new int[][]{
            new int[]{4, 39, -38},//, 4},
            new int[]{33, -33, -6},//, 27},
            new int[]{10, -37, -16},//, -26},
            new int[]{-13, 0, 33},//, 30},
            new int[]{10, -36, 0},//, -24},
            new int[]{-37, -27, -4},//, 11},
            new int[]{5, -22, -33},//, 12},
            new int[]{-10, -25, 23},//, -2},
            new int[]{-25, 16, 27},//, -27},
            new int[]{-34, -3, -22},//, 1}
    };

    /*
    protected static final int[][] SHIFTY = new int[][]{
            new int[]{-35, 34},//,33},//, -39},
            new int[]{19, -21},//, 38},//, -2},
            new int[]{-13, 0},//, 3},//, 10},
            new int[]{2, 21},//, 16},//, -21},
            new int[]{31, 4},//, 5},//, -11},
            new int[]{-14, 11},//, 35, 10},
            new int[]{4, -30},//, 30},//, 35},
            new int[]{-20, -15},//},//, -32, 19},
            new int[]{-9, 36},//, -7},//, -1},
            new int[]{30, 36},//, 17},//, 2}
    };*/


    protected static final int[][] SHIFTY = new int[][]{
            new int[]{-35, 34,33},//, -39},
            new int[]{19, -21, 38},//, -2},
            new int[]{-13, 0, 3},//, 10},
            new int[]{2, 21, 16},//, -21},
            new int[]{31, 4, 5},//, -11},
            new int[]{-14, 11},//, 35, 10},
            new int[]{4, -30, 30},//, 35},
            new int[]{-20, -15, -32},//, 19},
            new int[]{-9, 36, -7},//, -1},
            new int[]{30, 36, 17},//, 2}
    };

    /**
     * Encode seed set as bit vectors
     * @param seedNumber
     * @param fitnessFunction
     * @param codec
     * @return
     */
    public static ISeq<Phenotype> seedSetAsBitVectors(
            int seedNumber,
            Function<FlightGrid, Integer> fitnessFunction,
            Codec<FlightGrid, BitGene> codec
    ) {
        SeedGenerator.codec = codec;
        SeedGenerator.f = fitnessFunction;

        List<Phenotype> phenotypeList;

        phenotypeList = new LinkedList();

        if(seedNumber > ORIENTATION.length)
            throw new UnsupportedOperationException(String.format("Seed number must from the range of[0, %d]",ORIENTATION.length-1));

        for (int orientation : ORIENTATION[seedNumber]) {
            for (int shiftX : SHIFTX[seedNumber]) {
                for (int shiftY : SHIFTY[seedNumber]) {
                    FlightGrid flightGrid = FlightGrid.makeFlightGrid(orientation, shiftX, shiftY);

                    // convert flight grid to phenotype
                    Genotype genotype = Genotype.of(
                            new BitVectorChromosome(flightGrid)
                    );

                    Phenotype phenotype = Phenotype.of(
                            genotype,
                            0,
                            SeedGenerator::genotype2Integer);

                    phenotypeList.add(phenotype);
                }
            }
        }
        return MSeq.of(phenotypeList).toISeq();
    }

    /**
     * Convert genotype to integer
     * @param genotype
     * @return
     */
    public static Integer genotype2Integer(Genotype<BitGene> genotype) {
        FlightGrid flightGrid = codec.decode(genotype);
        return f.apply(flightGrid);
    }

    /**
     * Generate random seeds
     * @param args
     */
    public static void main(String[] args) {

        int lineSpacing = 80;

        Random random = RandomRegistry.getRandom();

        System.out.println(String.format("%d,%d,%d,%d", random.nextInt(90), random.nextInt(90), random.nextInt(90), random.nextInt(90)));
        System.out.println(String.format("%d,%d,%d,%d", random.nextInt(lineSpacing) - lineSpacing/2, random.nextInt(lineSpacing) - lineSpacing/2, random.nextInt(lineSpacing) - lineSpacing/2, random.nextInt(lineSpacing) - lineSpacing/2));
        System.out.println(String.format("%d,%d,%d,%d", random.nextInt(lineSpacing) - lineSpacing/2, random.nextInt(lineSpacing) - lineSpacing/2, random.nextInt(lineSpacing) - lineSpacing/2, random.nextInt(lineSpacing) - lineSpacing/2));
    }
}
