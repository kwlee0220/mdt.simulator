package mdt.simulator;

import java.time.Duration;
import java.util.List;

import utils.async.Execution;

import lombok.Builder;
import lombok.Getter;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Getter
@Builder
public class SimulationSession {
	private String sessionId;
	private Execution<List<String>> simulation;
	private String output;
	private Duration sessionRetainTimeout;
}
