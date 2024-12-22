package cn.lihongjie;

import com.ibm.msg.client.jms.JmsConnectionFactory;
import com.ibm.msg.client.jms.JmsConstants;
import com.ibm.msg.client.jms.JmsFactoryFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;


@Testcontainers
public class BaseTest {

    public static final String QM = "QM1";
    public static final String DEV_APP_SVRCONN = "DEV.APP.SVRCONN";
    public static final String APP = "app";
    public static final String PASSWORD = "passw0rd";


    public static final String ADMIN = "admin";

    public GenericContainer mq;


    @BeforeAll
    static void beforeAll() {


    }

    @SneakyThrows
    public JmsConnectionFactory getConnectionFactory(String qm, String channel, String userId, String password) {


        JmsFactoryFactory ff = JmsFactoryFactory.getInstance(JmsConstants.WMQ_PROVIDER);
        JmsConnectionFactory factory = ff.createConnectionFactory();
        factory.setObjectProperty(WMQConstants.WMQ_CONNECTION_MODE, Integer.valueOf(WMQConstants.WMQ_CM_CLIENT));
        factory.setStringProperty(WMQConstants.WMQ_HOST_NAME, mq.getHost());
        factory.setObjectProperty(WMQConstants.WMQ_PORT, mq.getMappedPort(1414));
        factory.setStringProperty(WMQConstants.WMQ_QUEUE_MANAGER, qm);
        factory.setStringProperty(WMQConstants.WMQ_CHANNEL, channel);
        factory.setStringProperty(WMQConstants.USERID, userId);
        factory.setStringProperty(WMQConstants.PASSWORD, password);

        return
                factory;

    }


    @BeforeEach
    void setUp() {

        mq = new GenericContainer(DockerImageName.parse("ibmcom/mq"))

                .withEnv("LICENSE", "accept")
                .withEnv("MQ_QMGR_NAME", QM)
                .withExposedPorts(1414)
                .withEnv("MQ_APP_PASSWORD", PASSWORD)
                .withEnv("MQ_ADMIN_PASSWORD", PASSWORD)
                .withEnv("MQ_APP_USER", APP)
                .withEnv("MQ_ADMIN_USER", ADMIN)
                .withEnv("MQ_ENABLE_METRICS", "true")
                .withEnv("MQ_DEV", "true")
                .withEnv("MQ_ENABLE_EMBEDDED_WEB_SERVER", "false")

                .waitingFor(new LogMessageWaitStrategy().withRegEx(".*The listener 'SYSTEM.LISTENER.TCP.1' has started.*"));

        mq.start();

    }


    @AfterEach
    void tearDown() {

        mq.stop();
    }

    @SneakyThrows
    public org.testcontainers.containers.Container.ExecResult execShell(String command) {

        org.testcontainers.containers.Container.ExecResult execResult = mq.execInContainer("/bin/sh", "-c", command);


        if (execResult.getExitCode() != 0) {
            throw new RuntimeException("shell command failed: " + execResult.getStderr());
        }


        return execResult;
    }


    @SneakyThrows
    public org.testcontainers.containers.Container.ExecResult execMqsc(String command) {


        org.testcontainers.containers.Container.ExecResult execResult = mq.execInContainer( "/bin/bash", "-c", "echo \"" + command + "\" | runmqsc " + QM);


        if (execResult.getExitCode() != 0) {
            throw new RuntimeException("mqsc command failed: " + execResult.getStderr() + " " + execResult.getStdout());
        }

        return execResult;

    }

}
