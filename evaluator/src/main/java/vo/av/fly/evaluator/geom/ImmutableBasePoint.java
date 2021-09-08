package vo.av.fly.evaluator.geom;

import javax.media.j3d.Transform3D;
import javax.vecmath.Point3d;
import java.io.Serializable;
import java.util.List;

public class ImmutableBasePoint implements Serializable  {
    private static final long serialVersionUID = 7397139002352221095L;

    private final double x;
    private final double y;
    private final double z;

    private ImmutableBasePoint(double x, double y, double z){
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Transform the sample using a list of {@code Transform3D}.
     * @param transformations
     * @return
     */
    public ImmutableBasePoint transform(final List<SerializableTransform3D> transformations){
        Point3d point = new Point3d(x,y,z);
        for (Transform3D transformer : transformations) {
            transformer.transform(point);
        }
        return new ImmutableBasePoint(point.x, point.y, point.z);
    }

    public double x(){return x;}
    public double y(){return y;}
    public double z(){return z;}

    @Override
    public boolean equals(Object o){
        if(o==this)
            return true;
        if(!(o instanceof ImmutableBasePoint))
            return false;
        ImmutableBasePoint that = (ImmutableBasePoint) o;
        return Double.compare(this.x, that.x) == 0
                && Double.compare(this.y, that.y) == 0
                && Double.compare(this.z, that.z) == 0;

    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + (int) (Double.doubleToLongBits(this.x) ^ (Double.doubleToLongBits(this.x) >>> 32));
        hash = 29 * hash + (int) (Double.doubleToLongBits(this.y) ^ (Double.doubleToLongBits(this.y) >>> 32));
        hash = 29 * hash + (int) (Double.doubleToLongBits(this.z) ^ (Double.doubleToLongBits(this.z) >>> 32));
        return hash;
    }

    @Override
    public String toString(){
        return String.format("[%.3f,%.3f,%.3f]",x,y,z);
    }

    public static ImmutableBasePoint valueOf(double x, double y, double z){
        return new ImmutableBasePoint(x, y, z);
    }

    public static ImmutableBasePoint valueOf(double[] coordinate){
        assert(coordinate.length==3);
        return new ImmutableBasePoint(coordinate[0],coordinate[1],coordinate[2]);
    }

    public static ImmutableBasePoint valueOf(ImmutableBasePoint sample){
        return new ImmutableBasePoint(sample.x(), sample.y(), sample.z());
    }
}
