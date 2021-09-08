package vo.av.fly.evaluator.geom;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class RayCasting {
    static Logger LOGGER = Logger.getLogger(RayCasting.class);

    public static boolean DEBUG = false;

    final Transformer transformer;
    final GeometryFactory geomFactory = new GeometryFactory(new PrecisionModel(.001));
    final double[] aircraftPosition; // in UV

    Accumulator accummulator;

    /**
     * Constructor.
     *
     * @param aircraftPositionXYZ in XYZ
     * @param angularResolution
     * @param minAngle
     * @param maxAngle
     * @param uniqueVperU
     * @param transformer
     */
    public RayCasting(double[] aircraftPositionXYZ, double angularResolution, double minAngle, double maxAngle, boolean uniqueVperU, Transformer transformer) {
        aircraftPosition = transformer.transform(aircraftPositionXYZ);
        //System.out.println(String.format("Aircraft position %.1f %.1f", aircraftPosition[0],aircraftPosition[1]));

        this.transformer = transformer;

        accummulator = new Accumulator(
                minAngle, maxAngle, angularResolution,
                aircraftPosition,
                uniqueVperU,
                geomFactory);

        //DEBUG = aircraftPositionXYZ[0]==100 && aircraftPositionXYZ[1]==138;

        //if(DEBUG)
        //    System.out.print(String.format(
        //            "===============\n"
        //                    + "DEBUG ON for y of %.1f\n"
        //                    + "===============\n",
        //            aircraftPositionXYZ[1]));
    }

    /**
     * Add xyz point.
     * @param xyz
     */
    public void addXYZ(double[] xyz){
        if(DEBUG)
            System.out.println(String.format("%.3f %.3f %.3f", xyz[0],xyz[1],xyz[2]));

        double[] uv = transformer.transform(xyz);
        accummulator.add(uv);
    }

    /**
     * Compute and return visible points.
     * @return visible points
     */
    public List<EnrichedPoint2D> getVisiblePoints() {
        return accummulator.generateVisiblePoints();
    }

    /**
     *
     * @return
     */
    public List<LineSegment> generateInterpolatedPulses(){
        return accummulator.interpolateVerticalPulses();
    }

    public static void main(String[] args) throws IOException {
        Transformer transformer = new YTransformer(53009);

        double[] aircraftPos = new double[]{298400,53009, 300};

        RayCasting visibilityComputation = new RayCasting(
                aircraftPos,
                .05,
                -30,30,true,
                transformer
        );
        BufferedReader reader = new BufferedReader(new FileReader("/Users/av1966/tmp/coverage/pointcloud/y53009.txt"));

        String line;
        String[] tokens;
        double x, y, z;
        while ((line = reader.readLine()) != null) {
            tokens = line.split(",");
            x = Double.parseDouble(tokens[0]);
            y = Double.parseDouble(tokens[1]);
            z = Double.parseDouble(tokens[2]);

            visibilityComputation.addXYZ(new double[]{x, y, z});
        }

        List<EnrichedPoint2D> visiblePoints = visibilityComputation.getVisiblePoints();

        for(Point point : visiblePoints){
            double[] xyz = transformer.inverse(point.getX(), point.getY());
            LOGGER.debug(String.format("%.3f %.3f %.3f", xyz[0],xyz[1], xyz[2]));
        }

        reader.close();
    }
}
