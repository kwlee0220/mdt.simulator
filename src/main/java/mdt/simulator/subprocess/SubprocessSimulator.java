package mdt.simulator.subprocess;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.collect.Lists;

import utils.io.FileUtils;
import utils.io.IOUtils;

import mdt.simulator.MDTSimulator;
import mdt.simulator.SimulationRequest;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SubprocessSimulator implements MDTSimulator {
	private final File m_workingDirectory;
	private final List<String> m_commandPrefix;
	
	public SubprocessSimulator(File workingDir, List<String> commandPrefix) {
		m_commandPrefix = commandPrefix;
		m_workingDirectory = workingDir;
		m_workingDirectory.mkdirs();
	}

	@Override
	public List<String> run(SimulationRequest request) throws IOException, InterruptedException,
																TimeoutException, Exception {
		List<String> commandLine = Lists.newArrayList(m_commandPrefix);
		
		Map<String,String> inputs = request.getInputValues();
		for ( String inputId: inputs.keySet() ) {
			String argValue = inputs.get(inputId);
			File argFile = new File(m_workingDirectory, inputId + ".json");
			
			try ( FileWriter fw = new FileWriter(argFile) ) {
				fw.append(argValue);
			}
			commandLine.add(argFile.getName());
		}
		
		List<String> outputVars = request.getOutputVariableNames();
		for ( int i =0; i < outputVars.size(); ++i ) {
			String outputVar = outputVars.get(i);
			File outputFile = new File(m_workingDirectory, outputVar + ".json");
			FileUtils.touch(outputFile, false);
			
			commandLine.add(outputFile.getName());
		}

		ProcessBuilder builder = new ProcessBuilder(commandLine);
		builder.directory(m_workingDirectory);
		
		Process process = builder.start();
		if ( request.getSimulationTimeout() != null ) {
			if ( process.waitFor(request.getSimulationTimeout().toMillis(), TimeUnit.MILLISECONDS) ) {
				List<String> outputs = collectOutputs(request);
				cleanArgFiles(request);
				return outputs;
			}
			else {
				process.destroyForcibly();
				throw new TimeoutException(request.getSimulationTimeout().toString());
			}
		}
		else {
			int retCode = process.waitFor();
			if ( retCode == 0 ) {
				List<String> outputs = collectOutputs(request);
				cleanArgFiles(request);
				return outputs;
			}
			else {
				throw new Exception("Simulation failed");
			}
		}
	}
	
	private List<String> collectOutputs(SimulationRequest request) throws IOException {
		List<String> outputs = Lists.newArrayList();
		for ( String outputVar: request.getOutputVariableNames() ) {
			File outputFile = new File(m_workingDirectory, outputVar + ".json");
			outputs.add(IOUtils.toString(outputFile));
		}
		return outputs;
	}
	
	private void cleanArgFiles(SimulationRequest request) {
		for ( String inputId: request.getInputValues().keySet() ) {
			File inputFile = new File(m_workingDirectory, inputId + ".json");
			inputFile.delete();
		}
		for ( String outputVar: request.getOutputVariableNames() ) {
			File outputFile = new File(m_workingDirectory, outputVar + ".json");
			outputFile.delete();
		}
	}
}
