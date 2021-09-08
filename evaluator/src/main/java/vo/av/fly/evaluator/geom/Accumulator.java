package vo.av.fly.evaluator.geom;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.Point;

import java.util.*;

/**
 * Accumulate range, angle, point counts for scan line analysis.
 * @author av1966
 */
public class Accumulator {
    static Logger LOGGER = Logger.getLogger(Accumulator.class);

    private final double[] aircraftPos; // in uv
    private final GeometryFactory geomFactory;
    private final double[] minRange;
    private final int[] pointCount;

    private final boolean uniqueVperU;

    final double minAngle, maxAngle, resolution;

    int actualMinAglIdx = Integer.MAX_VALUE, actualMaxAglIdx = -Integer.MAX_VALUE;

    final double MIN_SMEARING_ANGLE = 10;
    //final static double MIN_SLOPE = 60;
    //final static int MIN_INTERPOLATED_POINTS = 0;

    final private TreeSet<Point> uvPointData;

    /**
     *
     * @param minAngle
     * @param maxAngle
     * @param resolution
     * @param aircraftPos
     * @param uniqueVperU
     * @param geomFactory
     */
    public Accumulator(double minAngle, double maxAngle, double resolution, double[] aircraftPos, boolean uniqueVperU, GeometryFactory geomFactory) {
        this.minAngle = minAngle;
        this.maxAngle = maxAngle;
        this.resolution = resolution;
        this.aircraftPos = aircraftPos;
        this.geomFactory = geomFactory;

        this.uniqueVperU = uniqueVperU;

        int noAngularValues = (int) Math.round(Math.ceil((maxAngle - minAngle) / resolution));

        minRange = new double[noAngularValues];
        pointCount = new int[noAngularValues];

        for (int i = 0; i < minRange.length; i++) {
            minRange[i] = Double.NaN;
            pointCount[i] = 0;
        }

        uvPointData = new TreeSet(new UComparator());
    }

    /**
     * Add a point to the accumulator.
     *
     * @param angle
     * @param range
     * @return
     */
    //private boolean add(double angle, double range) {
    //    int angularIdx = (int) Math.round((angle - minAngle) / resolution);
    //    if ((angularIdx > 0) && (angularIdx < minRange.length)) { // this should reject any pulses outside the minAngle maxAngle range
    //        pointCount[angularIdx]++;
    //        if (Double.isNaN(minRange[angularIdx]) || minRange[angularIdx] > range) {
    //            minRange[angularIdx] = range;
    //            return true;
    //        }
    //    }
    //
    //   return false;
    //}

    /**
     * Add a point to the accumulator.
     *
     * @param uv
     * @return
     */
    public boolean add(double[] uv) {
        uvPointData.add(geomFactory.createPoint(new Coordinate(uv[0], uv[1])));

        if (uniqueVperU) {// just cache the point, do not need to add the data to keepers
            return true;
        }
        return addToKeepers(uv);
    }

    /**
     * Add the UV point to keepers (minRange, pointCount).
     *
     * @param uv
     * @return
     */
    private boolean addToKeepers(double[] uv) {
        LineSegment segment = new LineSegment(
                aircraftPos[0], aircraftPos[1],
                uv[0], uv[1]
        );

        //System.out.println(segment);
        double angle = (segment.angle() + Math.PI / 2) * 180 / Math.PI; // in degrees
        double range = segment.getLength();

        int angularIdx = (int) Math.round((angle - minAngle) / resolution);

        if ((angularIdx > 0) && (angularIdx < minRange.length)) { // this should reject any pulses outside the field of view
            pointCount[angularIdx]++;
            if (Double.isNaN(minRange[angularIdx]) || minRange[angularIdx] > range) {
                minRange[angularIdx] = range;
                return true;
            }

            if (angularIdx < actualMinAglIdx) {
                actualMinAglIdx = angularIdx;
            }
            if (angularIdx > actualMaxAglIdx) {
                actualMaxAglIdx = angularIdx;
            }
        }

        return false;
    }

    /**
     * Detect and resolve points layered above the same location (points with different Vs sharing the same U value).
     *
     */
    private void ensureUniqueness() {
        final double uResolution = .5;

        Map<Integer, List<Point>> map = new HashMap();

        Iterator<Point> iP = uvPointData.iterator();

        while (iP.hasNext()) {
            Point point = iP.next();
            // round u
            int uIdx = (int) Math.round(point.getX() / uResolution);
            if (map.containsKey(uIdx)) { // if the index is already in the map
                map.get(uIdx).add(point);
            } else {
                List<Point> pList = new LinkedList();
                pList.add(point);
                map.put(uIdx, pList);
            }

        }
        // now the points are binned by their u values

        //TreeSet<Point> newUVSet = new TreeSet(new UComparator());
        Iterator<Integer> iM = map.keySet().iterator();
        while (iM.hasNext()) {
            int uIdx = iM.next();

            if (RayCasting.DEBUG) {
                System.out.println(String.format("u=%.3f", uIdx * uResolution));
            }

            List<Point> pList = map.get(uIdx);// get the list of points

            if (pList.isEmpty()) {
                continue;
            }

            Point lowestPoint = pList.get(0), highestPoint = pList.get(0);
            for (Point point : pList) {
                if (point.getY() < lowestPoint.getY()) {
                    lowestPoint = point;
                }
                if (point.getY() > highestPoint.getY()) {
                    highestPoint = point;
                }
            }

            if (RayCasting.DEBUG) {
                System.out.println(String.format("Lowest point %s\nHighest point %s", lowestPoint, highestPoint));
            }

            if (highestPoint.getY() - lowestPoint.getY() < 1) { // insignificant elevation difference
                if (RayCasting.DEBUG) {
                    System.out.println(String.format("Insignificant elevation difference -> add all"));
                }
                for (Point point : pList) { // remove those from low point
                    addToKeepers(new double[]{point.getX(), point.getY()});
                }
            } else {

                // partition the set to the 2 halves base on their proximity to the highest/lowest point
                List<Point> lowPoints = new LinkedList(), highPoints = new LinkedList();
                for (Point point : pList) {
                    if (highestPoint.getY() - point.getY() < point.getY() - lowestPoint.getY()) // point is closer to the high group
                    {
                        if (RayCasting.DEBUG) {
                            System.out.println(String.format("Add %s to high group", point));
                        }
                        highPoints.add(point);
                    } else {
                        if (RayCasting.DEBUG) {
                            System.out.println(String.format("Add %s to low group", point));
                        }
                        lowPoints.add(point);
                    }
                }

                // add points in the dominant group to the keepers
                //if (highPoints.size() > lowPoints.size()) { // high group dominate
                //    if (VisibilityComputation.DEBUG) {
                //        System.out.println(String.format("High group dominates (%d vs. %d)", highPoints.size(), lowPoints.size()));
                //    }
                //    for (Point point : highPoints) { // add those high points to keepers
                //        if (VisibilityComputation.DEBUG) {
                //            System.out.println(String.format("Add %.3f %.3f to keepers", point.getX(), point.getY()));
                //        }
                //        addToKeepers(new double[]{point.getX(), point.getY()});
                //    }
                //} else {
                //    if (VisibilityComputation.DEBUG) {
                //        System.out.println(String.format("Low group dominates (%d vs. %d)", lowPoints.size(), highPoints.size()));
                //    }
                //    for (Point point : lowPoints) { // remove those from low point
                //        if (VisibilityComputation.DEBUG) {
                //            System.out.println(String.format("Add %.3f %.3f to keepers", point.getX(), point.getY()));
                //        }
                //        addToKeepers(new double[]{point.getX(), point.getY()});
                //    }
                //}

                if (highPoints.size() > lowPoints.size()) { // high group dominate
                    if (RayCasting.DEBUG) {
                        System.out.println(String.format("High group dominates (%d vs. %d)", highPoints.size(), lowPoints.size()));
                    }
                    for (Point point : lowPoints) { // remove low points from the set
                        if (RayCasting.DEBUG) {
                            System.out.println(String.format("Remove %.3f %.3f to keepers", point.getX(), point.getY()));
                        }
                        uvPointData.remove(point);
                    }
                } else {
                    if (RayCasting.DEBUG) {
                        System.out.println(String.format("Low group dominates (%d vs. %d)", lowPoints.size(), highPoints.size()));
                    }
                    for (Point point : highPoints) { // remove those from low point
                        if (RayCasting.DEBUG) {
                            System.out.println(String.format("Remove %.3f %.3f to keepers", point.getX(), point.getY()));
                        }
                        uvPointData.remove(point);
                    }
                }
            }
        }

        // add the remaining points to the keepers
        for(Point point : uvPointData){
            addToKeepers(new double[]{point.getX(), point.getY()});
        }
    }

    /**
     * Get the accumulator min range.
     *
     * @return
     */
    public double[] getMinRange() {
        return minRange;
    }

    /**
     * Get the accumulator latestKnownPoint count.
     *
     * @return
     */
    public int[] getPointCount() {
        return pointCount;
    }

    /**
     * @deprecated Generate uv points from the accumulated range, angle
     * minRange.
     *
     * @return list of points in uc coordinate system
     */
    public List<EnrichedPoint2D> generatePointsLegacy() {
        List<EnrichedPoint2D> result = new LinkedList();

        EnrichedPoint2D point = null;
        OccludedBuffer occludedBuffer = null;
        for (int angularIdx = 0; angularIdx < minRange.length; angularIdx++) {
            final double angle = idx2Angle(angularIdx);
            final double range = minRange[angularIdx];
            final int count = pointCount[angularIdx];

            if (Double.isNaN(range) && point == null) {
                continue;
            }

            if (Double.isNaN(range)) { // empty bin
                if (occludedBuffer == null) {
                    occludedBuffer = new OccludedBuffer(point);
                } else {
                    occludedBuffer.add(angle);
                }
            } else { // non-empty bin
                double[] uv = computePoint(angle, range);
                point = new EnrichedPoint2D(geomFactory.createPoint(new Coordinate(uv[0], uv[1])), count, false);

                //System.out.println(String.format("%.3f %.3f", uv[0], uv[1]));
                //System.out.println(latestKnownPoint);
                if (occludedBuffer != null) { // pending occluded buffer
                    List<EnrichedPoint2D> interpolatedPoints = occludedBuffer.finish(point);

                    if (interpolatedPoints == null) {
                        continue; // the known line is going backward
                    }
                    result.addAll(interpolatedPoints);
                    occludedBuffer = null;
                } else {
                    result.add(point);
                }
            }
        }

        return result;
    }

    /**
     * Generate visible points.
     *
     * @return
     */
    public List<EnrichedPoint2D> generateVisiblePoints() {
        if (uniqueVperU) {
            ensureUniqueness();
        }

        List<EnrichedPoint2D> result = new LinkedList();

        EnrichedPoint2D latestKnownPoint = null; // latest known latestKnownPoint
        OccludedBuffer occludedBuffer = null;
        for (int angularIdx = 0; angularIdx < minRange.length; angularIdx++) {
            final double angle = idx2Angle(angularIdx); // in degrees, to the -z, originated at the aircraft position
            final double range = minRange[angularIdx];
            final int count = pointCount[angularIdx];

            if (Double.isNaN(range) && latestKnownPoint == null) {
                continue; // reach emty bin but there is not a known point for interpolation
            }
            if (Double.isNaN(range)) { // empty bin (and there is a preceeding known point)

                if (occludedBuffer != null) {
                    occludedBuffer.add(angle);
                } else {
                    // pritotize vertical search
                    EnrichedPoint2D nextKnownPoint = searchVerticallyForNextKnownPoint(latestKnownPoint, angle > 0);
                    if (nextKnownPoint != null) {
                        double nextAngle = computeAngleRange(new double[]{nextKnownPoint.getX(), nextKnownPoint.getY()})[0];

                        if (nextAngle < minAngle) {
                            nextAngle = minAngle;
                        } else if (nextAngle > maxAngle) {
                            nextAngle = maxAngle;
                        }

                        int nextAngularIdx = angle2Idx(nextAngle);

                        //System.out.println(String.format("Next angle %.3f",nextAngle));
                        List<EnrichedPoint2D> interpolatedPoints = interpolate(latestKnownPoint, nextKnownPoint, angularIdx, nextAngularIdx);
                        result.addAll(interpolatedPoints);

                        //System.out.println(String.format("Angular jump %d %d",angularIdx,nextAngularIdx));
                        if (nextAngularIdx > angularIdx) {
                            angularIdx = nextAngularIdx - 1; // skip to nextAngularIdx
                        }
                    } else {
                        occludedBuffer = new OccludedBuffer(latestKnownPoint);
                    }
                }

            } else { // non-empty bin
                double[] uv = computePoint(angle, range);
                latestKnownPoint = new EnrichedPoint2D(geomFactory.createPoint(new Coordinate(uv[0], uv[1])), count, false);

                if (occludedBuffer != null) { // pending occluded buffer
                    List<EnrichedPoint2D> interpolatedPoints = occludedBuffer.finish(latestKnownPoint);

                    if (interpolatedPoints == null) {
                        continue; // the known line is going backward
                    }
                    result.addAll(interpolatedPoints);
                    occludedBuffer = null;
                } else {
                    result.add(latestKnownPoint);
                }
            }
        }

        return result;
    }

    /**
     * Interpolate vertical group.
     *
     * @param latestKnownPoint
     * @param nextKnownPoint
     * @param angularIdx
     * @param nextAngularIdx
     * @return
     */
    private List<EnrichedPoint2D> interpolate(Point latestKnownPoint, EnrichedPoint2D nextKnownPoint, int angularIdx, int nextAngularIdx) {

        LineSegment knownLine = new LineSegment(
                new Coordinate(latestKnownPoint.getX(), latestKnownPoint.getY()),
                new Coordinate(nextKnownPoint.getX(), nextKnownPoint.getY())
        );

        if (RayCasting.DEBUG) {
            System.out.println(String.format("Known line: %s", knownLine));
        }

        List<EnrichedPoint2D> result = new LinkedList();
        for (int idx = angularIdx; idx <= nextAngularIdx; idx++) {
            final double angle = idx2Angle(idx);
            final double[] target = computePoint(angle, 1000);
            final LineSegment pulse = new LineSegment(
                    new Coordinate(aircraftPos[0], aircraftPos[1]),
                    new Coordinate(target[0], target[1])
            );

            double smearingAngle = Math.abs(pulse.angle() - knownLine.angle()) * 180 / Math.PI; // in degrees
            if (smearingAngle > 90) {
                smearingAngle = smearingAngle - 90;
            }
            if (smearingAngle < MIN_SMEARING_ANGLE) {
                if (RayCasting.DEBUG) {
                    System.out.println(String.format("Skip smearing angle of %.1f", smearingAngle));
                }
                continue;
            }

            if( Math.abs(90-Math.abs(pulse.angle()*180/Math.PI ) ) <  MIN_SMEARING_ANGLE) continue;

            //System.out.println(String.format("Pulse %s angle %.1f aglIdx %d", pulse, angle, idx));
            final Point intersection
                    = geomFactory.createPoint(pulse.intersection(knownLine));

            if (RayCasting.DEBUG) {
                System.out.println(String.format("Interpolated point %s", intersection == null ? "null" : intersection));
            }

            //if (VisibilityComputation.DEBUG) {
            //if (intersection == null) {
            //    System.out.println(String.format("Null pulse %s", pulse));
            //} else {
            //    System.out.println(String.format("Add intersection %s", intersection));
            //}
            //}
            if (intersection != null && !intersection.isEmpty()) {
                result.add(new EnrichedPoint2D(intersection, nextKnownPoint.count(), true));
            }
        }

        return result;
    }

    /**
     *
     * @param latestKnownPoint
     * @param flank true - right flank; false - left flank
     * @return
     */
    private EnrichedPoint2D searchVerticallyForNextKnownPoint(Point latestKnownPoint, boolean flank) {
        final double d = 1.5; //TODO avoid hard code

        final Point fromPoint = geomFactory.createPoint(new Coordinate(latestKnownPoint.getX() - d, Double.NaN));
        final Point toPoint = geomFactory.createPoint(new Coordinate(latestKnownPoint.getX() + d, Double.NaN));

        // neighbours residing within a radius of d from latestKnownPoint
        Set<Point> range = uvPointData.subSet(fromPoint, toPoint);

        Point result = flank
                ? geomFactory.createPoint(new Coordinate(Double.NaN, -Double.MAX_VALUE)) // right
                : geomFactory.createPoint(new Coordinate(Double.NaN, Double.MAX_VALUE)); // left

        if (RayCasting.DEBUG) {
            System.out.println(flank ? "right flank" : "left flank");
        }

        // TODO to optimise
        for (Point point : range) {
            if (flank) { // right - need the highest
                if (point.getY() > result.getY()) {
                    if (point.getY() - result.getY() > 1) // elevation increament larger than 1
                    {
                        result = point;
                    } else { // if elevation increase is insignificatnt, need to consider horizontal changes
                        double currentDx = Math.abs(result.getX() - latestKnownPoint.getX());
                        double thisDx = Math.abs(point.getX() - latestKnownPoint.getX());
                        if (thisDx < currentDx) { // this horizontal change is less than the current solution
                            result = point;
                        }
                    }
                }
            } else { // left - need the lowest
                if (point.getY() < result.getY()) {
                    if (result.getY() - point.getY() > 1) // if the increase in elevation is larget than 1
                    {
                        result = point;
                    } else { // if elevation increase is insignificatnt, need to consider horizontal changes
                        double currentDx = Math.abs(result.getX() - latestKnownPoint.getX());
                        double thisDx = Math.abs(point.getX() - latestKnownPoint.getX());
                        if (thisDx < currentDx) { // this horizontal change is less than the current solution
                            result = point;
                        }
                    }
                }
            }
        }

        if (!Double.isNaN(result.getX()) && (Math.abs(latestKnownPoint.getY() - result.getY()) > 1)) {
            return new EnrichedPoint2D(result, range.size(), false);
        }
        return null;
    }

    private void printUV() {
        System.out.print("--------------------\nPrinting UV point data\n--------------------\n");
        for (Point uv : uvPointData) {
            System.out.printf("%.3f %.3f\n", uv.getX(), uv.getY());
        }
    }

    /**
     * Interpolate pulses hitting vertical structures.
     *
     * @return list of interpolated pulses
     */
    public List<LineSegment> interpolateVerticalPulses() {

        if (uniqueVperU) {
            ensureUniqueness();
        }

        List<LineSegment> result = new LinkedList();

        if (RayCasting.DEBUG) {
            printUV();
        }

        EnrichedPoint2D latestKnownPoint = null; // latest known point
        for (int angularIdx = actualMinAglIdx; angularIdx <= actualMaxAglIdx; angularIdx++) {
            final double angle = idx2Angle(angularIdx); // in degrees, to the -z, originated at the aircraft position
            final double range = minRange[angularIdx];
            final int count = pointCount[angularIdx];

            if (Double.isNaN(range) && latestKnownPoint == null) {
                continue; // reach empty bin but there is not a known point for interpolation
            }
            if (Double.isNaN(range)) { // empty bin (and there is a preceeding known point)

                if (RayCasting.DEBUG) {
                    System.out.println(String.format("Reaching to an empty bin with angle of %.1f", angle));
                }

                // pritotize vertical search
                EnrichedPoint2D nextKnownPoint = searchVerticallyForNextKnownPoint(latestKnownPoint, angle > 0);
                if (nextKnownPoint != null) {
                    //if (VisibilityComputation.DEBUG) {
                    //    System.out.println(String.format("Previous [%.1f %.1f] Next [%.1f %.1f] ",latestKnownPoint.getX(), latestKnownPoint.getY(), nextKnownPoint.getX(), nextKnownPoint.getY()));
                    //}

                    double nextAngle = computeAngleRange(new double[]{nextKnownPoint.getX(), nextKnownPoint.getY()})[0];

                    if (RayCasting.DEBUG) {
                        System.out.println(String.format("Next angle %.1f", nextAngle));
                    }

                    if (nextAngle < minAngle) {
                        nextAngle = minAngle;
                    } else if (nextAngle > maxAngle) {
                        nextAngle = maxAngle;
                    }

                    int nextAngularIdx = angle2Idx(nextAngle);

                    List<EnrichedPoint2D> interpolatedPoints = interpolate(latestKnownPoint, nextKnownPoint, angularIdx, nextAngularIdx);
                    if (interpolatedPoints == null) {
                        continue; // the known line is going backward
                    }

                    for (EnrichedPoint2D interpolatedPoint : interpolatedPoints) {
                        LineSegment interpolatedPulse = new LineSegment(
                                interpolatedPoint.getCoordinate(),
                                new Coordinate(aircraftPos[0], aircraftPos[1]));

                        double[] d = new double[]{
                                aircraftPos[0] - interpolatedPoint.getCoordinate().x,
                                aircraftPos[1] - interpolatedPoint.getCoordinate().y
                        };

                        interpolatedPulse = new LineSegment(
                                interpolatedPoint.getCoordinate(),
                                new Coordinate(
                                        interpolatedPoint.getCoordinate().x + d[0] / interpolatedPulse.getLength(),
                                        interpolatedPoint.getCoordinate().y + d[1] / interpolatedPulse.getLength()
                                )
                        );
                        result.add(interpolatedPulse);
                    }

                    if (nextAngularIdx > angularIdx) {
                        angularIdx = nextAngularIdx - 1; // skip to nextAngularIdx
                    }
                }

            } else { // non-empty bin
                double[] uv = computePoint(angle, range);
                latestKnownPoint = new EnrichedPoint2D(geomFactory.createPoint(new Coordinate(uv[0], uv[1])), count, false);
            }
        }

        return result;
    }

    /**
     * @deprecated Need to modify.
     *
     * @return list of points in uc coordinate system
     */
    public List<LineSegment> generateInterpolatedPulsesLegacy() {
        List<LineSegment> result = new LinkedList();

        EnrichedPoint2D point = null;
        OccludedBuffer occludedBuffer = null;
        for (int angularIdx = actualMinAglIdx; angularIdx <= actualMaxAglIdx; angularIdx++) {
            final double angle = idx2Angle(angularIdx);
            final double range = minRange[angularIdx];
            final int count = pointCount[angularIdx];

            if (Double.isNaN(range) && point == null) {
                continue;
            }

            if (Double.isNaN(range)) { // empty bin
                if (occludedBuffer == null) { // no active occluded buffer
                    occludedBuffer = new OccludedBuffer(point); // initiate one
                } else {
                    occludedBuffer.add(angle); // add latestKnownPoint to the occluded buffer
                }
            } else { // non-empty bin
                double[] uv = computePoint(angle, range);
                point = new EnrichedPoint2D(geomFactory.createPoint(new Coordinate(uv[0], uv[1])), count, false);

                //result.add(latestKnownPoint);
                if (occludedBuffer != null) { // there is an active occlusion
                    List<EnrichedPoint2D> interpolatedPoints = occludedBuffer.finish(point);

                    if (interpolatedPoints == null) {
                        continue; // the known line is going backward
                    }
                    for (EnrichedPoint2D interpolatedPoint : interpolatedPoints) {
                        LineSegment interpolatedPulse = new LineSegment(
                                interpolatedPoint.getCoordinate(),
                                new Coordinate(aircraftPos[0], aircraftPos[1]));

                        double[] d = new double[]{
                                aircraftPos[0] - interpolatedPoint.getCoordinate().x,
                                aircraftPos[1] - interpolatedPoint.getCoordinate().y
                        };

                        interpolatedPulse = new LineSegment(
                                interpolatedPoint.getCoordinate(),
                                new Coordinate(
                                        interpolatedPoint.getCoordinate().x + d[0] / interpolatedPulse.getLength(),
                                        interpolatedPoint.getCoordinate().y + d[1] / interpolatedPulse.getLength()
                                )
                        );
                        result.add(interpolatedPulse);
                    }

                    occludedBuffer = null;
                }
            }
        }

        return result;
    }

    private class OccludedBuffer {

        public EnrichedPoint2D lastVisiblePoint;
        public List<LineSegment> noReturnPulses;

        public OccludedBuffer(EnrichedPoint2D lastVisiblePoint) {
            this.lastVisiblePoint = lastVisiblePoint;
            noReturnPulses = new LinkedList();
        }

        public void add(double angle) {
            double[] uv = computePoint(angle, 10000);

            LineSegment pulse = new LineSegment(
                    new Coordinate(aircraftPos[0], aircraftPos[1]),
                    new Coordinate(uv[0], uv[1])
            );
            noReturnPulses.add(pulse);
        }

        /**
         * Finish the pending occluded buffer
         * @return
         */
        public List<EnrichedPoint2D> finish(EnrichedPoint2D nextVisiblePoint) {

            List<EnrichedPoint2D> result = new LinkedList();

            LineSegment knownLine = new LineSegment(
                    new Coordinate(lastVisiblePoint.getX(), lastVisiblePoint.getY()),
                    new Coordinate(nextVisiblePoint.getX(), nextVisiblePoint.getY())
            );

            for (LineSegment pulse : noReturnPulses) {
                final double angle = pulse.angle() * 180 / Math.PI + 90; // angle in degree to the vertical
                Point intersection
                        = geomFactory.createPoint(pulse.intersection(knownLine));
                result.add(new EnrichedPoint2D(intersection, 0, false)); // interpolated latestKnownPoint
            }

            return result;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < minRange.length; i++) {
            builder.append(String.format("[%d] %.3f\n", i, minRange[i]));
        }
        return builder.toString();
    }

    /**
     * Convert an angle value to index.
     *
     * @param angle
     * @return
     */
    private int angle2Idx(double angle) {
        return (int) Math.round(((angle - minAngle) / resolution) - .5);
    }

    /**
     * Convert index to angle.
     *
     * @param angularIdx
     * @return
     */
    private double idx2Angle(int angularIdx) {
        return (angularIdx + .5) * resolution + minAngle;
    }

    /**
     * Compute point coordinate given range and angle values.
     *
     * @param angle in degrees
     * @param range
     * @return
     */
    private double[] computePoint(double angle, double range) {
        double[] result = new double[2];

        //System.out.println(String.format("aircraft %.3f %.3f", aircraftPos[0], aircraftPos[1]));
        result[0] = aircraftPos[0] + range * Math.sin(angle * Math.PI / 180);
        result[1] = aircraftPos[1] - range * Math.cos(angle * Math.PI / 180);

        return result;
    }

    /**
     * Compute angle and range from point coordinate.
     *
     * @param uv
     * @return [range, angle in degrees]
     */
    private double[] computeAngleRange(double[] uv) {
        double[] d = new double[]{
                uv[0] - aircraftPos[0],
                uv[1] - aircraftPos[1]
        };

        double range = Math.sqrt(d[0] * d[0] + d[1] * d[1]);

        double angle = Math.asin(d[0] / range) * 180 / Math.PI;

        return new double[]{
                angle,
                range
        };
    }

    /**
     * tested
     */
    public static class UComparator implements Comparator<Point> {

        @Override
        public int compare(Point p1, Point p2) {
            final double difference = p1.getX() - p2.getX();
            if (difference < 0) {
                return -1;
            } else if (difference > 0) {
                return 1;
            }
            return 0;
        }

    }
}
