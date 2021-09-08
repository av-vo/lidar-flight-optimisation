package vo.av.fly.evaluator.geom;

/**
 * Geometric transform between the point cloud's 3D coordinate system (xyz) and
 * the 2D coordinate system of the scan line (uv).
 * @author av1966
 */
public interface Transformer {
    /**
     * Transform a point from xyz to uv.
     * @param xyz
     * @return
     */
    public double[] transform(double... xyz);

    /**
     * Inversely transform an uv point to xyz.
     * @param uv
     * @return
     */
    public double[] inverse(double... uv);
}
