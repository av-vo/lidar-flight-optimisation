package vo.av.fly.evaluator;

import org.apache.spark.api.java.function.FlatMapFunction;
import vo.av.fly.evaluator.geom.ImmutableBasePoint;

import java.util.Collections;
import java.util.Iterator;

public class Functions {

    /**
     * Parse points from text file
     * @return
     */
    public static FlatMapFunction<String, ImmutableBasePoint> parsePoints(final double[] offset){
        return new FlatMapFunction<String, ImmutableBasePoint>() {
            private static final long serialVersionUID = 4355382009407212231L;

            @Override
            public Iterator<ImmutableBasePoint> call(String line) throws Exception {
                final double[] coordinate = new double[3];
                //final float surfaceDensityIndex;

                final String[] tokens = line.split(",");
                for (int i = 0; i < 3; i++) {
                    coordinate[i] = Double.parseDouble(tokens[i]) - offset[i];
                }
                //surfaceDensityIndex = Float.parseFloat(tokens[3]);

                ImmutableBasePoint sample = ImmutableBasePoint.valueOf(coordinate);

                return Collections.singletonList(sample).iterator();
            }
        };
    }
}
