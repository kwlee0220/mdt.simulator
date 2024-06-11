package mdt.simulator.dummy;

import java.util.List;
import java.util.concurrent.TimeUnit;

import utils.func.Unchecked;

import mdt.simulator.MDTSimulator;
import mdt.simulator.SimulationRequest;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class DummyMDTSimulator implements MDTSimulator {
	private static final long IDLE_SECONDS = 30;
	
	private final List<String> m_outputValues;
	
	public DummyMDTSimulator(List<String> outputValues) {
		m_outputValues = outputValues;
	}

	@Override
	public List<String> run(SimulationRequest request) throws Exception {
		Unchecked.runOrIgnore(() -> TimeUnit.SECONDS.sleep(IDLE_SECONDS));
		return m_outputValues;
	}
}
