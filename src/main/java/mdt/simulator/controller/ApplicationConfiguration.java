package mdt.simulator.controller;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import mdt.client.MDTClientConfig;
import mdt.model.instance.MDTInstanceManager;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Configuration
public class ApplicationConfiguration {
	@Bean
	@ConfigurationProperties(prefix = "mdt-server")
	MDTClientConfig getMDTClientConfig() {
		return new MDTClientConfig();
	}

	@Bean
	MDTInstanceManager getMDTInstanceManagerClient() throws KeyManagementException, NoSuchAlgorithmException {
		MDTClientConfig conf = getMDTClientConfig();
		return conf.createMDTInstanceManager();
	}
	
	@Bean
	@ConfigurationProperties(prefix = "simulator")
	MDTSimulatorConfiguration getSimulatorConfiguration() {
		return new MDTSimulatorConfiguration();
	}
}
