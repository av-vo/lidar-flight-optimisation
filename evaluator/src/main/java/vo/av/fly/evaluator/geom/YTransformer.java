package vo.av.fly.evaluator.geom;

/**
 * Transformer for flight line parallel to the X axis (i.e. constant y)
 */
public class YTransformer  implements Transformer {
    private final double y;

    /**
     * Constructor.
     *
     * @param y
     */
    public YTransformer(double y) {
        this.y = y;
    }

    @Override
    public double[] transform(double... xyz) {
        return new double[]{
                xyz[0], // u = x
                xyz[2] // v = z
        };
    }

    @Override
    public double[] inverse(double... uv) {
        //System.out.println(String.format("%.3f %.3f", uv[0], uv[1]));
        return new double[]{
                uv[0],
                y,
                uv[1]
        };
    }
}
