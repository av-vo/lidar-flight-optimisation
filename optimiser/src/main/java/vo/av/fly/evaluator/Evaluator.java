package vo.av.fly.evaluator;

public interface Evaluator {
    /**
     * Evaluate a {@code FlightGrid}.
     * @param flightGrid the flight grid to evaluate
     * @return fitness score of the given flight grid
     */
    //public int eval(FlightGrid flightGrid);

    /**
     * Pass input arguments on to evaluator
     * @param args the arguments to pass on
     */
    public void passOnArgs(String[] args);

    /**
     * Evaluation completed, do clean-up
     */
    public void requestToTerminateService();
}
