package io.jenetics;

import vo.av.fly.optimiser.FlightGrid;

public interface FlightGridChromosome {
    /**
     * Convert a chromosome to a flight grid.
     * @return flight grid
     */
    public FlightGrid toFlightGrid();

    /**
     * Convert a flight grid to a chromosome. - NO, this is taken care by the constructor.
     * @param flightGrid
     * @return chromosome
     */
    //public FlightGridChromosome fromFlightGrid(FlightGrid flightGrid);

}
