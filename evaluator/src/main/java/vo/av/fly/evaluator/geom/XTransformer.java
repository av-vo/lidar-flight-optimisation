package vo.av.fly.evaluator.geom;

/**
 * Transformer for flight line parallel to the Y axis (i.e. constant x)
 */
public class XTransformer  implements Transformer {
    private final double x;

    /**
     * Constructor.
     * @param x
     */
    public XTransformer(double x){
        this.x = x;
    }

    @Override
    public double[] transform(double... xyz) {
        return new double[]{
                xyz[1], // u = y
                xyz[2] // v = z
        };
    }

    @Override
    public double[] inverse(double... uv) {
        return new double[]{
                x,
                uv[0],
                uv[1]
        };
    }
}
