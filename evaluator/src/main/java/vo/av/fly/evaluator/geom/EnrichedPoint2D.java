package vo.av.fly.evaluator.geom;

import org.locationtech.jts.geom.Point;

public class EnrichedPoint2D  extends Point {
    private static final long serialVersionUID = -7616367827214609245L;

    private final int count;

    private final boolean vertical;

    public EnrichedPoint2D(Point point, int count, boolean vertical){
        super(point.getCoordinate(), point.getPrecisionModel(), point.getSRID());
        this.count = count;
        this.vertical = vertical;
    }

    public int count(){
        return count;
    }

    public boolean vertical(){
        return vertical;
    }
}
