package mdt.simulator;

import java.util.List;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface MDTSimulator {
	List<String> run(SimulationRequest request) throws Exception;
}
