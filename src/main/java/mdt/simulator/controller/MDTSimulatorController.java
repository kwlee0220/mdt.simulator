package mdt.simulator.controller;

import static mdt.client.SubmodelUtils.cast;
import static mdt.client.SubmodelUtils.traverse;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import utils.InternalException;
import utils.async.Execution;
import utils.async.StartableExecution;
import utils.async.op.AsyncExecutions;
import utils.func.Funcs;
import utils.io.FileUtils;

import mdt.client.SubmodelUtils;
import mdt.client.instance.HttpMDTInstanceManagerClient;
import mdt.client.resource.ExtendedSubmodelService;
import mdt.client.resource.HttpSubmodelServiceClient;
import mdt.client.simulation.OperationStatus;
import mdt.client.simulation.OperationStatusResponse;
import mdt.ksx9101.simulation.Simulation;
import mdt.model.instance.MDTInstance;
import mdt.model.registry.ResourceNotFoundException;
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
	private static final Duration SESSION_RETAIN_TIMEOUT = Duration.ofMinutes(5);	// 5 minutes
	private static final JsonMapper s_deser = new JsonMapper();
	private static final JsonSerializer s_ser = new JsonSerializer();
	
	@Autowired private HttpMDTInstanceManagerClient m_mdtClient;
	@Autowired private MDTSimulatorConfiguration m_config;
	
	@Value("${simulation-endpoint}")
	private String m_simulationEndpoint;
	
	private MDTSimulator m_simulator;
	private Map<String,SimulationSession> m_sessions = Maps.newHashMap();
	private ExtendedSubmodelService m_xsvc;
	
	private void shutdown() {
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("Shutting down MDT Simulator...");
		}
		
		m_xsvc.setPropertyValueByPath(Simulation.IDSHORT_PATH_ENDPOINT, "");
		m_xsvc.setPropertyValueByPath(Simulation.IDSHORT_PATH_TIMEOUT, "");
		m_xsvc.setPropertyValueByPath(Simulation.IDSHORT_PATH_SESSION_TIMEOUT, "");
		m_xsvc = null;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Preconditions.checkState(m_config.getSubmodelId() != null, "Simulation Submodel is missing");
		
		SubmodelService svc;
		if ( m_simulationEndpoint.trim().length() > 0 ) {
			svc = HttpSubmodelServiceClient.newTrustAllSubmodelServiceClient(m_simulationEndpoint);
		}
		else {
			MDTInstance inst = m_mdtClient.getInstanceBySubmodelId(m_config.getSubmodelId());
			svc = inst.getSubmodelServiceById(m_config.getSubmodelId());
		}
		m_xsvc = ExtendedSubmodelService.from(svc);
		
		m_xsvc.setPropertyValueByPath(Simulation.IDSHORT_PATH_ENDPOINT, m_config.getEndpoint());
		if ( m_config.getTimeout() != null ) {
			m_xsvc.setPropertyValueByPath(Simulation.IDSHORT_PATH_TIMEOUT,
											m_config.getTimeout().toString());
		}
		if ( m_config.getSessionRetainTimeout() != null ) {
			m_xsvc.setPropertyValueByPath(Simulation.IDSHORT_PATH_SESSION_TIMEOUT,
											m_config.getSessionRetainTimeout().toString());
		}
		Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
		
//		Duration timeout = SubmodelUtils.getPropertyValueByPath(svc.getSubmodel(),
//																Simulation.IDSHORT_PATH_TIMEOUT,
//																Duration.class);
//		Duration sessionRetainTimeout = SubmodelUtils.getPropertyValueByPath(svc.getSubmodel(),
//																	Simulation.IDSHORT_PATH_SESSION_TIMEOUT,
//																	Duration.class);
		
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("Simulator Endpoint {}", m_config.getEndpoint());
			s_logger.info("Simulation Submodel: id={}", m_config.getSubmodelId());
			
			File workDir = m_config.getWorkingDirectory();
			if ( workDir == null ) {
				workDir = FileUtils.getWorkingDirectory();
			}
			s_logger.info("Simulation working directory: {}", workDir);
			
			s_logger.info("Simulation Timeout: {}", m_config.getTimeout());
			s_logger.info("Simulation SessionRetainTimeout: {}", m_config.getSessionRetainTimeout());
		}
		
		m_simulator = new SubprocessSimulator(m_config.getWorkingDirectory(), m_config.getCommandPrefix());
	}
	
	private SubmodelService getSimulationSubmodelService(String parameters) {
		try {
			JsonNode jnode = s_deser.readTree(parameters);

			Set<Map.Entry<String, JsonNode>> elements = jnode.properties();
			if ( elements.size() == 1 ) {
				Entry<String,JsonNode> first = Funcs.getFirstOrNull(elements);
				if ( first.getKey().equals("submodelId") ) {
					String submodelId = first.getValue().asText();
					MDTInstance inst = m_mdtClient.getInstanceBySubmodelId(submodelId);
					return inst.getSubmodelServiceById(submodelId);
				}
				else if ( first.getKey().equals("submodelEndpoint") ) {
					String submodelEndpoint = first.getValue().asText();
					return HttpSubmodelServiceClient.newTrustAllSubmodelServiceClient(submodelEndpoint);
				}
			}
		}
		catch ( Exception e ) {
			throw new IllegalArgumentException("Invalid Simulation request: " + parameters);
		}
		
		throw new IllegalArgumentException("Invalid Simulation request: " + parameters);
	}

    @PostMapping({""})
    public ResponseEntity<OperationStatusResponse<Void>> start(
    													@RequestParam(name="parameters") String parameters) {
    	try {
        	SubmodelService simulationService = getSimulationSubmodelService(parameters);
    		Submodel submodel = simulationService.getSubmodel();
    		StartableExecution<List<String>> sim = startSimulation(submodel);
    		
    		Duration retainTimeout = SESSION_RETAIN_TIMEOUT;
    		try {
    			retainTimeout = SubmodelUtils.getPropertyValueByPath(submodel,
    													Simulation.IDSHORT_PATH_SESSION_TIMEOUT,
														Duration.class);
			}
			catch ( ResourceNotFoundException e ) {}

//    		String sessionId = Integer.toHexString(sim.hashCode());
    		String sessionId = "ProcessOptimization";
    		SimulationSession session = SimulationSession.builder()
    													.sessionId(sessionId)
    													.simulation(sim)
    													.sessionRetainTimeout(retainTimeout)
    													.build();
    		m_sessions.put(sessionId, session);
    		sim.whenFinished(result -> {
    			result.ifSuccessful(outputs -> {
    				// 시뮬레이션이 성공한 경우.
    				// 수행 결과 인자를 변경시킨다.
        			updateOutputProperties(simulationService, outputs);
    			});
    			
    			StartableExecution<SimulationSession> delayedSessionClose
		    			= AsyncExecutions.delayed(() -> m_sessions.remove(sessionId),
			    									session.getSessionRetainTimeout().toMillis(),
			    									TimeUnit.MILLISECONDS);
    			delayedSessionClose.start();
    		});
    		
    		OperationStatusResponse<Void> resp = toResponse(session);
    		return ResponseEntity.created(new URI(sessionId)).body(resp);
			
    	}
    	catch ( IllegalArgumentException e ) {
    		OperationStatusResponse<Void> resp = OperationStatusResponse.<Void>builder()
																		.status(OperationStatus.FAILED)
																		.message("" + e)
																		.build();
    		return ResponseEntity.badRequest().body(resp);
    	}
    	catch ( Exception e ) {
    		OperationStatusResponse<Void> resp = OperationStatusResponse.<Void>builder()
																		.status(OperationStatus.FAILED)
																		.message("" + e)
																		.build();
    		return ResponseEntity.internalServerError().body(resp);
    	}
    }

    @GetMapping({"/{opId}"})
    @ResponseStatus(HttpStatus.OK)
    public OperationStatusResponse status(@PathVariable("opId") String opId) {
    	SimulationSession session = m_sessions.get(opId);
    	if ( session == null ) {
    		String msg = String.format("SimulationSession is not found: handle=%s", opId);
    		return OperationStatusResponse.builder()
    									.status(OperationStatus.FAILED)
    									.message(msg)
    									.build();
    	}
    	
    	return toResponse(session);
    }

    @DeleteMapping({"/{opId}"})
    @ResponseStatus(HttpStatus.OK)
    public OperationStatusResponse delete(@PathVariable("opId") String opId) {
    	SimulationSession session = m_sessions.remove(opId);
    	if ( session == null ) {
    		String msg = String.format("SimulationSession is not found: handle=%s", opId);
    		return OperationStatusResponse.builder()
    									.status(OperationStatus.FAILED)
    									.message(msg)
    									.build();
    	}
    	
    	session.getSimulation().cancel(true);
    	
    	return toResponse(session);
    }

    private StartableExecution<List<String>> startSimulation(Submodel submodel) {
		Preconditions.checkArgument(submodel != null);
		
		SubmodelElement info = SubmodelUtils.traverse(submodel, "SimulationInfo");
		
		Duration timeout = null;
		try {
			timeout = SubmodelUtils.getPropertyValueByPath(info, "SimulationTool.SimulationTimeout", Duration.class);
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
			var timed = AsyncExecutions.timed(simulation, timeoutMillis, TimeUnit.MILLISECONDS);
			timed.start();
		}
		else {
			simulation.start();
		}
		
		return simulation;
    }

	private Map<String,String> loadInputs(SubmodelElement simulationInfo) {
		Map<String,String> inputValues = Maps.newLinkedHashMap();
		
		SubmodelElementList inputs = cast(traverse(simulationInfo, "Inputs"), SubmodelElementList.class);
		for ( SubmodelElement inputSme: inputs.getValue() ) {
			String inputId = SubmodelUtils.getPropertyValueByPath(inputSme, "InputID", String.class) ;
			SubmodelElement valueSme = SubmodelUtils.traverse(inputSme, "InputValue");
			inputValues.put(inputId, derefSubmodelElementString(valueSme));
		}
		
		return inputValues;
    }
	
	private List<String> loadOutputVariableNames(SubmodelElement simulationInfo) {
		List<String> outputVariableNames = Lists.newArrayList();
		
		SubmodelElementList outputs = cast(traverse(simulationInfo, "Outputs"), SubmodelElementList.class);
		for ( SubmodelElement outputSme: outputs.getValue() ) {
			String outputId = SubmodelUtils.getPropertyValueByPath(outputSme, "OutputID", String.class) ;
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
	
    
    private void updateOutputProperties(SubmodelService svc, List<String> outputValues) {
    	Submodel submodel = svc.getSubmodel();
    	List<SubmodelElement> outputSmeList = cast(traverse(submodel, Simulation.IDSHORT_PATH_OUTPUTS),
													SubmodelElementList.class).getValue();
    	for ( int i =0; i < outputSmeList.size(); ++i ) {
    		ExtendedSubmodelService xsvc = ExtendedSubmodelService.from(svc);
    		
    		String path = String.format("%s.%d.OutputValue", Simulation.IDSHORT_PATH_OUTPUTS, i);
    		xsvc.setPropertyValueByPath(path, outputValues.get(i));
    	}
    }
    
    private OperationStatusResponse<Void> toResponse(SimulationSession session) {
    	Execution<List<String>> sim = session.getSimulation();
    	
    	OperationStatus status = toSimulationStatus(sim);
    	String msg = switch ( sim.getState() ) {
    		case STARTING -> "Simulation is starting";
    		case RUNNING -> "Simulation is running";
    		case COMPLETED -> "Simulation is completed";
    		case CANCELLING -> "Simulation is cancelling";
    		case CANCELLED -> "Simulation is cancelled";
    		case FAILED -> "" + sim.poll().getFailureCause();
    		default -> throw new IllegalStateException("Unexpected Simulation state: "
    														+ sim.getState());
    	};
    	return OperationStatusResponse.<Void>builder()
			    							.status(status)
											.message(msg)
											.build();
    }

	private OperationStatus toSimulationStatus(Execution<?> exec) {
    	try {
    		exec.get(0, TimeUnit.MILLISECONDS);
			return OperationStatus.COMPLETED;
		}
		catch ( CancellationException e ) {
			return OperationStatus.CANCELLED;
		}
		catch ( TimeoutException e ) {
			return OperationStatus.RUNNING;
		}
		catch ( InterruptedException e ) {
			throw new InternalException("" + e);
		}
		catch ( ExecutionException e ) {
			return OperationStatus.FAILED;
		}
	}
}
