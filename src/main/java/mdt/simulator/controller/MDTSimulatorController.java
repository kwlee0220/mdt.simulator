package mdt.simulator.controller;

import static mdt.model.resource.SubmodelUtils.getPropertyValueByPath;
import static mdt.model.resource.SubmodelUtils.traverse;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.DeserializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;
import org.eclipse.digitaltwin.aas4j.v3.model.Property;
import org.eclipse.digitaltwin.aas4j.v3.model.ReferenceElement;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import utils.InternalException;
import utils.async.Execution;
import utils.async.StartableExecution;
import utils.async.op.AsyncExecutions;

import mdt.client.simulation.SimulationResponse;
import mdt.client.simulation.SimulationStatus;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.registry.ResourceNotFoundException;
import mdt.model.resource.SubmodelUtils;
import mdt.model.service.SubmodelService;
import mdt.simulator.MDTSimulator;
import mdt.simulator.SimulationRequest;
import mdt.simulator.SimulationSession;
import mdt.simulator.ThreadedMDTSimulation;
import mdt.simulator.subprocess.SubprocessSimulator;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@RestController
@RequestMapping("/simulator")
public class MDTSimulatorController implements InitializingBean {
	private static final Logger s_logger = LoggerFactory.getLogger(MDTSimulatorController.class);
	private static final String IDSHORT_PATH_ENDPOINT = "SimulationInfo.SimulationTool.SimulatorEndpoint";
	private static final String IDSHORT_PATH_TIMEOUT = "SimulationInfo.SimulationTool.SimulationTimeout";
	private static final String IDSHORT_PATH_SESSION_TIMEOUT = "SimulationInfo.SimulationTool.SessionRetainTimeout";
	private static final String IDSHORT_PATH_OUTPUTS = "SimulationInfo.Outputs";
	private static final Duration SESSION_RETAIN_TIMEOUT = Duration.ofMinutes(5);	// 5 minutes
	private static final JsonSerializer s_ser = new JsonSerializer();
	
	@Autowired private MDTInstanceManager m_mdtClient;
	@Autowired private MDTSimulatorConfiguration m_config;
	private MDTSimulator m_simulator;
	private Map<String,SimulationSession> m_sessions = Maps.newHashMap();

	@Override
	public void afterPropertiesSet() throws Exception {
		m_mdtClient.setPropertyValueByPath(m_config.getSubmodelId(), IDSHORT_PATH_ENDPOINT,
											m_config.getEndpoint());
		if ( m_config.getTimeout() != null ) {
			m_mdtClient.setPropertyValueByPath(m_config.getSubmodelId(), IDSHORT_PATH_TIMEOUT,
												m_config.getTimeout().toString());
		}
		if ( m_config.getSessionRetainTimeout() != null ) {
			m_mdtClient.setPropertyValueByPath(m_config.getSubmodelId(), IDSHORT_PATH_SESSION_TIMEOUT,
												m_config.getSessionRetainTimeout().toString());
		}
		
		SubmodelService svc = m_mdtClient.getSubmodelService(m_config.getSubmodelId());
		Duration timeout = SubmodelUtils.getPropertyValueByPath(svc.getSubmodel(), IDSHORT_PATH_TIMEOUT,
																Duration.class);
		Duration sessionRetainTimeout = SubmodelUtils.getPropertyValueByPath(svc.getSubmodel(),
																IDSHORT_PATH_SESSION_TIMEOUT, Duration.class);
		
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("Simulator Endpoint {}", m_config.getEndpoint());
			s_logger.info("Simulation Submodel: id={}", m_config.getSubmodelId());
			s_logger.info("Simulation Timeout: {}", timeout);
			s_logger.info("Simulation SessionRetainTimeout: {}", sessionRetainTimeout);
		}
		
		m_simulator = new SubprocessSimulator(m_config.getWorkingDirectory(), m_config.getCommandPrefix());
	}

    @GetMapping({"/start"})
    @ResponseStatus(HttpStatus.CREATED)
    public SimulationResponse start(@RequestParam("submodel") String submodelIdEncoded)
    	throws DeserializationException {
		String submodelId = new String(Base64.getDecoder().decode(submodelIdEncoded));

    	try {
    		Preconditions.checkArgument(submodelId != null);
    		
    		SubmodelService svc = m_mdtClient.getSubmodelService(submodelId);
    		Submodel submodel = svc.getSubmodel();
    		StartableExecution<List<String>> sim = startSimulation(submodel);
    		
    		Duration retainTimeout = SESSION_RETAIN_TIMEOUT;
    		try {
    			retainTimeout = SubmodelUtils.getPropertyValueByPath(submodel,
														"SimulationInfo.SimulationTool.SessionRetainTimeout",
														Duration.class);
			}
			catch ( ResourceNotFoundException e ) {}

//    		String sessionId = Integer.toHexString(sim.hashCode());
    		String sessionId = "12345";
    		SimulationSession session = SimulationSession.builder()
    													.sessionId(sessionId)
    													.simulation(sim)
    													.sessionRetainTimeout(retainTimeout)
    													.build();
    		m_sessions.put(sessionId, session);
    		sim.whenFinished(result -> {
    			result.ifSuccessful(outputs -> {
    				// 시뮬레이션이 성공한 경우.
        			updateOutputProperties(submodel, outputs);
    			});
    			
    			AsyncExecutions.delayed(() -> {
    				m_sessions.remove(sessionId);
    			}, session.getSessionRetainTimeout().toMillis(), TimeUnit.MILLISECONDS).start();
    		});
			
	    	return toResponse(session);
    	}
    	catch ( Exception e ) {
	    	return SimulationResponse.builder()
									.status(SimulationStatus.FAILED)
									.message("" + e)
									.build();
    	}
    }

    @GetMapping({"/status"})
    @ResponseStatus(HttpStatus.OK)
    public SimulationResponse status(@RequestParam("opHandle") String sessionId) {
    	SimulationSession session = m_sessions.get(sessionId);
    	if ( session == null ) {
    		String msg = String.format("SimulationSession is not found: handle=%s", sessionId);
    		return SimulationResponse.builder()
    									.status(SimulationStatus.FAILED)
    									.message(msg)
    									.build();
    	}
    	
    	return toResponse(session);
    }

    @GetMapping({"/cancel"})
    @ResponseStatus(HttpStatus.OK)
    public SimulationResponse cancel(@RequestParam("opHandle") String sessionId) {
    	SimulationSession session = m_sessions.remove(sessionId);
    	if ( session == null ) {
    		String msg = String.format("SimulationSession is not found: handle=%s", sessionId);
    		return SimulationResponse.builder()
    									.status(SimulationStatus.FAILED)
    									.message(msg)
    									.build();
    	}
    	
    	return toResponse(session);
    }

    private StartableExecution<List<String>> startSimulation(Submodel submodel) {
		Preconditions.checkArgument(submodel != null);
		
		SubmodelElement info = traverse(submodel, "SimulationInfo");
		
		Duration timeout = null;
		try {
			timeout = getPropertyValueByPath(info, "SimulationTool.SimulationTimeout", Duration.class);
		}
		catch ( ResourceNotFoundException e ) { }
		
		SimulationRequest req = SimulationRequest.builder()
												.modelId("1")
												.inputValues(loadInputs(info))
												.outputVariableNames(loadOutputVariableNames(info))
												.simulationTimeout(timeout)
												.build();
		StartableExecution<List<String>> simulation = new ThreadedMDTSimulation(m_simulator, req);
		if ( req.getSimulationTimeout() != null ) {
			long timeoutMillis = req.getSimulationTimeout().toMillis();
			simulation = AsyncExecutions.timed(simulation, timeoutMillis, TimeUnit.MILLISECONDS);
		}
		simulation.start();
		
		return simulation;
    }

	private Map<String,String> loadInputs(SubmodelElement simulationInfo) {
		Map<String,String> inputValues = Maps.newLinkedHashMap();
		for ( SubmodelElement inputSme: traverse(simulationInfo, "Inputs", SubmodelElementList.class).getValue() ) {
			String inputId = getPropertyValueByPath(inputSme, "InputID", String.class) ;
			SubmodelElement valueSme = traverse(inputSme, "InputValue");
			inputValues.put(inputId, derefSubmodelElementString(valueSme));
		}
		
		return inputValues;
    }
	
	private List<String> loadOutputVariableNames(SubmodelElement simulationInfo) {
		List<String> outputVariableNames = Lists.newArrayList();
		for ( SubmodelElement outputSme: traverse(simulationInfo, "Outputs", SubmodelElementList.class).getValue() ) {
			String outputId = getPropertyValueByPath(outputSme, "OutputID", String.class) ;
			outputVariableNames.add(outputId);
		}
		
		return outputVariableNames;
	}
	
	private String derefSubmodelElementString(SubmodelElement sme) {
		if ( sme instanceof Property prop ) {
			return prop.getValue();
		}
		else if ( sme instanceof ReferenceElement re ) {
			SubmodelElement sme2 = m_mdtClient.getSubmodelElementByReference(re.getValue());
			return derefSubmodelElementString(sme2);
		}
		else {
			try {
				return s_ser.write(sme);
			}
			catch ( SerializationException e ) {
				throw new InternalException("" + e);
			}
		}
	}
	
    
    private void updateOutputProperties(Submodel submodel, List<String> outputValues) {
    	List<SubmodelElement> outputSmeList = SubmodelUtils.traverse(submodel, IDSHORT_PATH_OUTPUTS,
																	SubmodelElementList.class).getValue();
    	for ( int i =0; i < outputSmeList.size(); ++i ) {
    		String path = String.format("%s.%d.OutputValue", IDSHORT_PATH_OUTPUTS, i);
    		m_mdtClient.setPropertyValueByPath(submodel.getId(), path, outputValues.get(i));
    	}
    }
    
    private SimulationResponse toResponse(SimulationSession session) {
    	Execution<List<String>> sim = session.getSimulation();
    	
    	SimulationStatus status = toSimulationStatus(sim);
    	String msg = switch ( sim.getState() ) {
    		case CANCELLING -> "Simulation is cancelling";
    		case CANCELLED -> "Simulation is cancelled";
    		case FAILED -> "" + sim.poll().getFailureCause();
    		default -> null;
    	};
    	return SimulationResponse.builder()
								.opHandle(session.getSessionId())
								.status(status)
								.message(msg)
								.build();
    }

	private SimulationStatus toSimulationStatus(Execution<?> exec) {
    	try {
    		exec.get(0, TimeUnit.MILLISECONDS);
			return SimulationStatus.COMPLETED;
		}
		catch ( CancellationException e ) {
			return SimulationStatus.CANCELLED;
		}
		catch ( TimeoutException e ) {
			return SimulationStatus.RUNNING;
		}
		catch ( InterruptedException e ) {
			throw new InternalException("" + e);
		}
		catch ( ExecutionException e ) {
			return SimulationStatus.FAILED;
		}
	}
}
