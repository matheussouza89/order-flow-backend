package com.matheus.orderFlow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.stream.Collectors;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer(DockerImageName.parse("mysql:latest"));
	}

	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitContainer() {
		return new RabbitMQContainer(DockerImageName.parse("rabbitmq:latest"));
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:latest")).withExposedPorts(6379);
	}

	@Bean
	@ServiceConnection
	MongoDBContainer mongoContainer() {
		Logger log = LoggerFactory.getLogger("mongo-diag");
		MongoDBContainer container = new MongoDBContainer(DockerImageName.parse("mongo:8")) {
			@Override
			public String getReplicaSetUrl() {
				try {
					return super.getReplicaSetUrl();
				} catch (IllegalStateException e) {
					throw new IllegalStateException(e.getMessage() + " DIAG id=" + getContainerId() + " state=" + describeState(), e);
				}
			}

			private String describeState() {
				if (getContainerId() == null) {
					return "none";
				}
				try {
					var info = getCurrentContainerInfo();
					return info.getState() + " created=" + info.getCreated() + " restarts=" + info.getRestartCount() + " logs=" + tailOfLogs();
				} catch (RuntimeException inspectFailure) {
					return "inspect failed: " + inspectFailure;
				}
			}

			private String tailOfLogs() {
				try {
					String logs = getLogs();
					return logs.lines()
						.skip(Math.max(0, logs.lines().count() - 12))
						.map(line -> line.length() > 500 ? line.substring(0, 500) : line)
						.collect(Collectors.joining(" | "));
				} catch (RuntimeException logsFailure) {
					return "unavailable: " + logsFailure;
				}
			}
		}.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("mongo-container")));
		container.start();
		log.warn("DIAG after start: running={} state={}", container.isRunning(), container.getCurrentContainerInfo().getState());
		return container;
	}

}
