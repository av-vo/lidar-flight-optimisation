package vo.av.fly.optimiser;

import io.jenetics.BitVectorChromosome;
import io.jenetics.Genotype;
import io.jenetics.Phenotype;
import io.jenetics.util.ISeq;
import vo.av.fly.evaluator.RemoteEvaluator;

import static vo.av.fly.optimiser.SeedGenerator.*;

public class TestSeed {
    public static void main(String[] args){

        int seedNumber = 2;
        int count = 0;
        for (int orientation : ORIENTATION[seedNumber]) {
            for (int shiftX : SHIFTX[seedNumber]) {
                for (int shiftY : SHIFTY[seedNumber]) {
                    count++;
                }
            }
        }
        System.out.println(count);
    }
}
