package vo.av.fly.optimiser;

/**
 * Rectilinear flight grid
 */
public class FlightGrid {
    private final int orientation;
    private final int shiftX;
    private final int shiftY;

    /**
     * Private constructor.
     *
     * @param orientation
     * @param shiftX
     * @param shiftY
     */
    private FlightGrid(int orientation, int shiftX, int shiftY) {
        this.orientation = orientation;
        this.shiftX = shiftX;
        this.shiftY = shiftY;
    }

    /**
     * Create an immutable {@code FlightGrid}.
     * @param orientation
     * @param shiftX
     * @param shiftY
     * @return flight grid object
     */
    public static FlightGrid makeFlightGrid(int orientation, int shiftX, int shiftY){
        return new FlightGrid(orientation, shiftX, shiftY);
    }

    public int orientation(){
        return orientation;
    }

    public int shiftX(){
        return shiftX;
    }

    public int shiftY(){
        return shiftY;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FlightGrid)) {
            return false;
        }

        FlightGrid that = (FlightGrid) o;
        return this.orientation == that.orientation
                && this.shiftX == that.shiftX
                && this.shiftY == that.shiftY;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 73 * hash + this.orientation;
        hash = 73 * hash + this.shiftX;
        hash = 73 * hash + this.shiftY;
        return hash;
    }

    @Override
    public String toString() {
        return String.format("[%d %d %d]", orientation, shiftX, shiftY);
    }
}
