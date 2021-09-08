package vo.av.fly.evaluator.geom;

import java.io.Serializable;

/**
 * Helicopter's position and orientation
 */
public class ImmutableSwathSegment implements Serializable {
    private static final long serialVersionUID = -486700320344758042L;

    private final boolean flyingDirection;
    private final double x;
    private final double y;
    private final double z;

    private ImmutableSwathSegment(boolean flyingDirection, double x, double y, double z){
        this.flyingDirection = flyingDirection;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean flyingDirection(){
        return flyingDirection;
    }

    public double x(){
        return x;
    }

    public double y(){
        return y;
    }

    public double z(){
        return z;
    }

    @Override
    public boolean equals(Object o){
        if(o==this)
            return true;
        if(!(o instanceof ImmutableSwathSegment))
            return false;
        ImmutableSwathSegment that = (ImmutableSwathSegment) o;
        return
                this.flyingDirection == that.flyingDirection
                        && Double.compare(this.x, that.x) == 0
                        && Double.compare(this.y, that.y) == 0
                        && Double.compare(this.z, that.z) == 0;

    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + (this.flyingDirection ? 1 : 0);
        hash = 97 * hash + (int) (Double.doubleToLongBits(this.x) ^ (Double.doubleToLongBits(this.x) >>> 32));
        hash = 97 * hash + (int) (Double.doubleToLongBits(this.y) ^ (Double.doubleToLongBits(this.y) >>> 32));
        hash = 97 * hash + (int) (Double.doubleToLongBits(this.z) ^ (Double.doubleToLongBits(this.z) >>> 32));
        return hash;
    }

    @Override
    public String toString(){
        return String.format("%s[%.3f,%.3f,%.3f]",flyingDirection? "X": "Y",x,y,z);
    }

    public static ImmutableSwathSegment valueOf(boolean flyingDirection, double x, double y, double z){
        return new ImmutableSwathSegment(flyingDirection, x, y, z);
    }
}
