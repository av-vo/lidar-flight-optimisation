package vo.av.fly.evaluator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import vo.av.fly.optimiser.FlightGrid;

/**
 * Dummy evaluator - for testing only
 * @author vvo
 */

public class DummyEvaluator implements Evaluator {

    private static final Logger LOG = LogManager.getLogger(DummyEvaluator.class);

    @Override
    public void passOnArgs(String[] args) {
        // nothing to do
    }

    @Override
    public void requestToTerminateService() {
        // do nothing
    }

    //@Override
    public static int eval(FlightGrid flightGrid) {
        int score = flightGrid.orientation() * flightGrid.shiftX() * flightGrid.shiftY();

        // log the result
        LOG.info(String.format(
                "%02d\t%03d\t%03d\t%d",
                flightGrid.orientation(), flightGrid.shiftX(), flightGrid.shiftY(), score
        ));

        return score;
    }

}

