package vo.av.fly.optimiser;

import io.jenetics.*;
import io.jenetics.engine.Codec;

/**
 * Flight grid codecs (encoders)
 */
public class Codecs {
    public static Codec<FlightGrid, BitGene> bitVectorCodec() {
        return Codec.of(
                Genotype.of(// factory
                        BitVectorChromosome.of()
                ),
                gt -> gt.getChromosome().as(BitVectorChromosome.class).toFlightGrid() // decoding function
        );
    }

    public static Codec<FlightGrid, IntegerGene> numericCodec(int lineSpacing) {
        return Codec.of(
                Genotype.of( // factory
                        IntegerChromosome.of(0, 89), // orientation
                        IntegerChromosome.of((int) Math.round(-lineSpacing / 2f), (int) Math.round(lineSpacing / 2f), 2) // shiftX, shiftY
                ),
                gt -> numericFlightGridDecodingFunction(gt) // decoding function
        );
    }

    private static FlightGrid numericFlightGridDecodingFunction(Genotype<IntegerGene> genotype) {
        final int orientation = ((IntegerChromosome) genotype.getChromosome(0)).intValue();

        final IntegerChromosome shift = (IntegerChromosome) genotype.getChromosome(1);
        final int shiftX = shift.intValue(0);
        final int shiftY = shift.intValue(1);

        return FlightGrid.makeFlightGrid(orientation, shiftX, shiftY);
    }
}
