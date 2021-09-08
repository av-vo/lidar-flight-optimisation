package io.jenetics;

import io.jenetics.internal.util.bit;
import io.jenetics.util.ISeq;
import io.jenetics.util.RandomRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import vo.av.fly.optimiser.FlightGrid;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Random;

import static java.util.Objects.requireNonNull;

/**
 * Binary chromosome encapsulate a {@code FlightGrid} object.
 *
 * must be placed in {@code io.jenetics} to access protected classes
 *
 * @author vvo
 */
public final class BitVectorChromosome implements
        Chromosome<BitGene>,
        FlightGridChromosome,
        Serializable {

    private static final Logger LOG = LogManager.getLogger(BitVectorChromosome.class);

    private static final long serialVersionUID = 2934562647243385263L;

    private static final int LENGTH = 24; // this chromosome consists of 3 bytes = 24 bits (BitGene)
    private static final int MAX_NR_TRIES = Integer.MAX_VALUE;

    // to compute the valid range of the shifting
    private static int lineSpacing = Integer.MIN_VALUE; // TODO: find a way to prevent people forget to initiate the line spacing value

    // the main content of the object
    private final byte[] genes; // this is composed of 3 bytes representing orientation, shiftX, shiftY

    /**
     * Construct a chromosome from a given flight grid.
     *
     * @param flightGrid
     */
    public BitVectorChromosome(FlightGrid flightGrid) {
        int orientation = flightGrid.orientation();
        int shiftX = flightGrid.shiftX();
        int shiftY = flightGrid.shiftY();

        if (orientation < Byte.MIN_VALUE || orientation > Byte.MAX_VALUE
                || shiftX < Byte.MIN_VALUE || shiftX > Byte.MAX_VALUE
                || shiftY < Byte.MIN_VALUE || shiftY > Byte.MAX_VALUE) {
            LOG.warn(String.format("orientation=%d, shiftX=%d, shiftY=%d is out of bound.",orientation,shiftX,shiftY ));
        }

        genes = new byte[]{
                (byte) orientation,
                (byte) shiftX,
                (byte) shiftY
        };
    }

    /**
     * Constructor
     *
     * @param genes
     */
    private BitVectorChromosome(final byte[] genes) {
        this.genes = genes;
    }

    public static void setLineSpacing(int lineSpacing) {
        BitVectorChromosome.lineSpacing = lineSpacing;
    }

    //factory method which creates a new {@link Chromosome} of specific type
    @Override
    public Chromosome newInstance(final ISeq<BitGene> genes) {
        requireNonNull(genes, "Genes");
        if (genes.size() != LENGTH) {
            throw new IllegalArgumentException(
                    String.format("The genes sequence must contain %d genes.", LENGTH)
            );
        }

        final BitVectorChromosome chromosome = new BitVectorChromosome(
                bit.newArray(genes.length())
        );

        if (genes instanceof BitGeneISeq) {
            final BitGeneISeq iseq = (BitGeneISeq) genes;
            iseq.copyTo(chromosome.genes);
        } else {
            for (int i = genes.length(); --i >= 0;) {
                if (genes.get(i).booleanValue()) {
                    bit.set(chromosome.genes, i);
                }
            }
        }

        return chromosome;
    }

    @Override
    public int length() {
        return LENGTH;
    }

    @Override
    public ISeq<BitGene> toSeq() {
        return BitGeneMSeq.of(this.genes, LENGTH).toISeq();
    }

    /**
     * Validate the chromosome.
     *
     * @return true if the chromosome is valid; or false otherwise
     */
    @Override
    public boolean isValid() {
        final byte orientation = genes[0];
        final byte shiftX = genes[1];
        final byte shiftY = genes[2];

        return validateOrientation(orientation) && validateShift(shiftX) && validateShift(shiftY);
    }

    /**
     * Validate orientation value.
     *
     * @param orientation
     * @return true if {@code orientation} is valid; false otherwise
     */
    private static boolean validateOrientation(int orientation) {
        return orientation >= 0 && orientation < 90;
    }

    /**
     * Validate shift value.
     *
     * @param shiftValue
     * @return true if {@code shiftValue} is valid; false otherwise
     */
    private static boolean validateShift(int shiftValue) {
        return shiftValue > -lineSpacing / 2 && shiftValue < lineSpacing / 2;
    }

    @Override
    public Iterator iterator() {
        return toSeq().iterator();
    }

    @Override
    public FlightGrid toFlightGrid() {
        return FlightGrid.makeFlightGrid(genes[0], genes[1], genes[2]);
    }

    @Override
    public BitGene getGene(int index) {
        return BitGene.of(bit.get(genes, index));
    }

    // create a new random instance of this chromosome
    @Override
    public Chromosome<BitGene> newInstance() {
        return createARandomInstance(MAX_NR_TRIES);
    }

    private static BitVectorChromosome createARandomInstance(int maxNrTries) {
        Random random = RandomRegistry.getRandom();

        int orientation = random.nextInt(90);

        int tryIdx = 0;
        while (!validateOrientation(orientation) && tryIdx++ < maxNrTries) {
            orientation = random.nextInt(90);
        }

        int shiftX = random.nextInt(lineSpacing);
        shiftX-=lineSpacing/2;
        tryIdx = 0;
        while (!validateShift(shiftX) && tryIdx++ < maxNrTries) {
            shiftX = random.nextInt(lineSpacing);
            shiftX-=lineSpacing/2;

        }

        int shiftY = random.nextInt(lineSpacing);
        shiftY-=lineSpacing/2;
        tryIdx = 0;
        while (!validateShift(shiftY) && tryIdx++ < maxNrTries) {
            shiftY = random.nextInt(lineSpacing);
            shiftY-=lineSpacing/2;
        }

        FlightGrid flightGrid = FlightGrid.makeFlightGrid(orientation, shiftX, shiftY);
        return new BitVectorChromosome(flightGrid);
    }

    /**
     * Construct a new random chromosome.
     * @return
     */
    public static BitVectorChromosome of() {
        return createARandomInstance(MAX_NR_TRIES);
    }

    @Override
    public String toString(){
        return toFlightGrid().toString();
    }
}
