package mdt.simulator;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Getter
@Builder
public class SimulationRequest {
	private String modelId;
	private Map<String,String> inputValues;
	private List<String> outputVariableNames;
	private Duration simulationTimeout;
}
