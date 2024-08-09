package mdt.simulator.controller;

import java.io.File;
import java.util.Set;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.Sets;

import utils.InternalException;
import utils.func.KeyValue;

import mdt.client.simulation.OperationStatusResponse;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@RestController
@RequestMapping("")
public class MDTOperationServerController implements InitializingBean {
	private static final Logger s_logger = LoggerFactory.getLogger(MDTOperationServerController.class);
	private static final JsonMapper s_deser = new JsonMapper();
	private static final JsonSerializer s_ser = new JsonSerializer();
	
	@Value("${command}")
	private String m_command;
	
	@Value("file:${workingDirectory}")
	private File m_workingDir;
	
	private Set<String> m_outputParameters = Sets.newHashSet();
	
	private void shutdown() {
	}

	@Override
	public void afterPropertiesSet() throws Exception {
	}
	
	private KeyValue<String,String> toParameter(String paramName, JsonNode paramValue) {
		if ( paramValue.isObject() ) {
			try {
				return KeyValue.of(paramName, s_deser.writeValueAsString(paramValue));
			}
			catch ( JsonProcessingException e ) {
				throw new InternalException("" + e);
			}
		}
		else {
			return KeyValue.of(paramName, paramValue.asText());
		}
	}
	
//	private SubmodelService getSimulationSubmodelService(String parametersJson) {
//		Map<String,String> parameters = HttpOperationClient.parseParametersJson(parametersJson);
//		String submodelEndpoint = parameters.get("submodelEndpoint");
//		if ( submodelEndpoint != null ) {
//			return HttpSubmodelServiceClient.newTrustAllSubmodelServiceClient(submodelEndpoint);
//		}
//		
//		String submodelId = parameters.get("submodelId");
//		if ( submodelId != null ) {
//			MDTInstance inst = m_mdtClient.getInstanceBySubmodelId(submodelId);
//			return inst.getSubmodelServiceById(submodelId);
//		}
//		
//		throw new IllegalArgumentException("Invalid Simulation request: " + parameters);
//	}

    @PostMapping({""})
    public ResponseEntity<OperationStatusResponse<String>> run(@RequestBody String parametersJson) {
//    	Map<String,String> parameters = HttpOperationClient.parseParametersJson(parametersJson);
//    	
//    	Builder builder = ProcessBasedMDTOperation.builder()
//												.setCommand(m_command)
//												.setWorkingDirectory(m_workingDir);
    	
//		FStream.from(s_deser.readTree(parametersJson).properties())
//				.map(ent -> toParameter(ent.getKey(), ent.getValue()))
//				.forEach(kv -> {
//					builder.addFileArgument(kv.key(), kv.value(), m_outputParameters.contains(kv.key()));
//				});
//
//		return ResponseEntity.created(new URI(sessionId)).body(resp);
//    	
//    	try {
//        	SubmodelService simulationService = getSimulationSubmodelService(parameters);
//    		Submodel submodel = simulationService.getSubmodel();
//    		StartableExecution<List<String>> sim = startSimulation(submodel);
//    		
//    		Duration retainTimeout = SESSION_RETAIN_TIMEOUT;
//    		try {
//    			retainTimeout = SubmodelUtils.getPropertyValueByPath(submodel,
//    													Simulation.IDSHORT_PATH_SESSION_TIMEOUT,
//														Duration.class);
//			}
//			catch ( ResourceNotFoundException e ) {}
//
////    		String sessionId = Integer.toHexString(sim.hashCode());
//    		String sessionId = "ProcessOptimization";
//    		SimulationSession session = SimulationSession.builder()
//    													.sessionId(sessionId)
//    													.simulation(sim)
//    													.sessionRetainTimeout(retainTimeout)
//    													.build();
//    		m_sessions.put(sessionId, session);
//    		sim.whenFinished(result -> {
//    			result.ifSuccessful(outputs -> {
//    				// 시뮬레이션이 성공한 경우.
//    				// 수행 결과 인자를 변경시킨다.
//        			updateOutputProperties(simulationService, outputs);
//    			});
//    			
//    			StartableExecution<SimulationSession> delayedSessionClose
//		    			= AsyncExecutions.delayed(() -> m_sessions.remove(sessionId),
//			    									session.getSessionRetainTimeout().toMillis(),
//			    									TimeUnit.MILLISECONDS);
//    			delayedSessionClose.start();
//    		});
//    		
//    		OperationStatusResponse<Void> resp = toResponse(session);
//    		return ResponseEntity.created(new URI(sessionId)).body(resp);
//			
//    	}
//    	catch ( IllegalArgumentException e ) {
//    		OperationStatusResponse<Void> resp = OperationStatusResponse.<Void>builder()
//																		.status(OperationStatus.FAILED)
//																		.message("" + e)
//																		.build();
//    		return ResponseEntity.badRequest().body(resp);
//    	}
//    	catch ( Exception e ) {
//    		OperationStatusResponse<Void> resp = OperationStatusResponse.<Void>builder()
//																		.status(OperationStatus.FAILED)
//																		.message("" + e)
//																		.build();
//    		return ResponseEntity.internalServerError().body(resp);
//    	}
    	return null;
    }

//    @GetMapping({"/{opId}"})
//    @ResponseStatus(HttpStatus.OK)
//    public OperationStatusResponse status(@PathVariable("opId") String opId) {
//    	SimulationSession session = m_sessions.get(opId);
//    	if ( session == null ) {
//    		String msg = String.format("SimulationSession is not found: handle=%s", opId);
//    		return OperationStatusResponse.builder()
//    									.status(OperationStatus.FAILED)
//    									.message(msg)
//    									.build();
//    	}
//    	
//    	return toResponse(session);
//    }
//
//    @DeleteMapping({"/{opId}"})
//    @ResponseStatus(HttpStatus.OK)
//    public OperationStatusResponse delete(@PathVariable("opId") String opId) {
//    	SimulationSession session = m_sessions.remove(opId);
//    	if ( session == null ) {
//    		String msg = String.format("SimulationSession is not found: handle=%s", opId);
//    		return OperationStatusResponse.builder()
//    									.status(OperationStatus.FAILED)
//    									.message(msg)
//    									.build();
//    	}
//    	
//    	session.getSimulation().cancel(true);
//    	
//    	return toResponse(session);
//    }
//
//    private StartableExecution<List<String>> startSimulation(Submodel submodel) {
//		Preconditions.checkArgument(submodel != null);
//		
//		SubmodelElement info = SubmodelUtils.traverse(submodel, "SimulationInfo");
//		
//		Duration timeout = null;
//		try {
//			timeout = SubmodelUtils.getPropertyValueByPath(info, "SimulationTool.SimulationTimeout", Duration.class);
//		}
//		catch ( ResourceNotFoundException e ) { }
//		
//		SimulationRequest req = SimulationRequest.builder()
//												.modelId("1")
//												.inputValues(loadInputs(info))
//												.outputVariableNames(loadOutputVariableNames(info))
//												.simulationTimeout(timeout)
//												.build();
//		StartableExecution<List<String>> simulation = new ThreadedMDTSimulation(m_simulator, req);
//		if ( req.getSimulationTimeout() != null ) {
//			long timeoutMillis = req.getSimulationTimeout().toMillis();
//			var timed = AsyncExecutions.timed(simulation, timeoutMillis, TimeUnit.MILLISECONDS);
//			timed.start();
//		}
//		else {
//			simulation.start();
//		}
//		
//		return simulation;
//    }
//
//	private Map<String,String> loadInputs(SubmodelElement simulationInfo) {
//		Map<String,String> inputValues = Maps.newLinkedHashMap();
//		
//		SubmodelElementList inputs = cast(traverse(simulationInfo, "Inputs"), SubmodelElementList.class);
//		for ( SubmodelElement inputSme: inputs.getValue() ) {
//			String inputId = SubmodelUtils.getPropertyValueByPath(inputSme, "InputID", String.class) ;
//			SubmodelElement valueSme = SubmodelUtils.traverse(inputSme, "InputValue");
//			inputValues.put(inputId, derefSubmodelElementString(valueSme));
//		}
//		
//		return inputValues;
//    }
//	
//	private List<String> loadOutputVariableNames(SubmodelElement simulationInfo) {
//		List<String> outputVariableNames = Lists.newArrayList();
//		
//		SubmodelElementList outputs = cast(traverse(simulationInfo, "Outputs"), SubmodelElementList.class);
//		for ( SubmodelElement outputSme: outputs.getValue() ) {
//			String outputId = SubmodelUtils.getPropertyValueByPath(outputSme, "OutputID", String.class) ;
//			outputVariableNames.add(outputId);
//		}
//		
//		return outputVariableNames;
//	}
//	
//	private String derefSubmodelElementString(SubmodelElement sme) {
//		if ( sme instanceof Property prop ) {
//			return prop.getValue();
//		}
//		else if ( sme instanceof ReferenceElement re ) {
//			SubmodelElement sme2 = m_mdtClient.getSubmodelElementByReference(re.getValue());
//			return derefSubmodelElementString(sme2);
//		}
//		else {
//			try {
//				return s_ser.write(sme);
//			}
//			catch ( SerializationException e ) {
//				throw new InternalException("" + e);
//			}
//		}
//	}
//	
//    
//    private void updateOutputProperties(SubmodelService svc, List<String> outputValues) {
//    	Submodel submodel = svc.getSubmodel();
//    	List<SubmodelElement> outputSmeList = cast(traverse(submodel, Simulation.IDSHORT_PATH_OUTPUTS),
//													SubmodelElementList.class).getValue();
//    	for ( int i =0; i < outputSmeList.size(); ++i ) {
//    		ExtendedSubmodelService xsvc = ExtendedSubmodelService.from(svc);
//    		
//    		String path = String.format("%s.%d.OutputValue", Simulation.IDSHORT_PATH_OUTPUTS, i);
//    		xsvc.setPropertyValueByPath(path, outputValues.get(i));
//    	}
//    }
//    
//    private OperationStatusResponse<Void> toResponse(SimulationSession session) {
//    	Execution<List<String>> sim = session.getSimulation();
//    	
//    	OperationStatus status = toSimulationStatus(sim);
//    	String msg = switch ( sim.getState() ) {
//    		case STARTING -> "Simulation is starting";
//    		case RUNNING -> "Simulation is running";
//    		case COMPLETED -> "Simulation is completed";
//    		case CANCELLING -> "Simulation is cancelling";
//    		case CANCELLED -> "Simulation is cancelled";
//    		case FAILED -> "" + sim.poll().getFailureCause();
//    		default -> throw new IllegalStateException("Unexpected Simulation state: "
//    														+ sim.getState());
//    	};
//    	return OperationStatusResponse.<Void>builder()
//			    							.status(status)
//											.message(msg)
//											.build();
//    }
//
//	private OperationStatus toSimulationStatus(Execution<?> exec) {
//    	try {
//    		exec.get(0, TimeUnit.MILLISECONDS);
//			return OperationStatus.COMPLETED;
//		}
//		catch ( CancellationException e ) {
//			return OperationStatus.CANCELLED;
//		}
//		catch ( TimeoutException e ) {
//			return OperationStatus.RUNNING;
//		}
//		catch ( InterruptedException e ) {
//			throw new InternalException("" + e);
//		}
//		catch ( ExecutionException e ) {
//			return OperationStatus.FAILED;
//		}
//	}
}
