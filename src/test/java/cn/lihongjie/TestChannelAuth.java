package cn.lihongjie;

import com.ibm.mq.MQException;
import com.ibm.msg.client.jms.JmsConnectionFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;

import javax.jms.*;

import static org.assertj.core.api.Assertions.*;

@Slf4j
public class TestChannelAuth
        extends BaseTest {


    @Test
    @DisplayName("验证执行dspmq命令")
    void test1() {

        Container.ExecResult execResult = execShell("dspmq");
        assertThat(execResult.getStdout()).contains(QM);
        log.info("result: {}", execResult.getStdout());

    }


    @SneakyThrows
    @Test
    @DisplayName("默认的app用户可以连接到QM1")
    void test2() {


        assertThatCode(() -> {


            JmsConnectionFactory connectionFactory = getConnectionFactory(QM, DEV_APP_SVRCONN, APP, PASSWORD);

            Connection connection = connectionFactory.createConnection();

            assertThat(connection).isNotNull();

            connection.close();

        }).doesNotThrowAnyException();


    }


    @SneakyThrows
    @Test
    @DisplayName("默认的app用户可以访问DEV队列")
    void test3() {


        doInSession(session1 -> {
            Queue queue1 = session1.createQueue("DEV.QUEUE.1");
            assertThat(queue1).isNotNull();

            MessageConsumer consumer = session1.createConsumer(queue1);
            Message message = consumer.receiveNoWait();


            assertThat(message).isNull();
        });


    }

    private void doInSession(ThrowingConsumer<Session> throwingConsumer) {
        assertThatCode(() -> {


            JmsConnectionFactory connectionFactory = getConnectionFactory(QM, DEV_APP_SVRCONN, APP, "passw0rd");

            Connection connection = connectionFactory.createConnection();

            assertThat(connection).isNotNull();


            Session session = connection.createSession();

            throwingConsumer.acceptThrows(session);

            connection.close();

        }).doesNotThrowAnyException();
    }


    @Test
    @DisplayName("app 用户无法访问非DEV开头的队列")
    void test4() {


        execMqsc("define qlocal(QUEUE.1)");


        Exception exception = catchException((() -> {


            JmsConnectionFactory connectionFactory = getConnectionFactory(QM, DEV_APP_SVRCONN, APP, PASSWORD);

            Connection connection = connectionFactory.createConnection();

            assertThat(connection).isNotNull();


            Session session = connection.createSession();


            Queue queue = session.createQueue("QUEUE.1");

            MessageConsumer consumer = session.createConsumer(queue);

            Message message = consumer.receiveNoWait();


            connection.close();

        }));


        assertThat(exception).isInstanceOf(JMSException.class).hasCauseInstanceOf(MQException.class);


        assertThat(exception.getCause()).isInstanceOf(MQException.class).extracting("reasonCode").isEqualTo(2035);

    }


    @Nested
    @DisplayName("认证流程校验")
    public class AuthFlow {

        @Test
        @DisplayName("BLOCKADDR 的优先级最高")
        /**
         * Step 2: Is the address allowed to connect?
         * Before any data is read, IBM MQ checks the IP address of the partner against the CHLAUTH rules,
         * to see if the address is in the BLOCKADDR rule.
         * If the address is not found, and so not blocked, the flow proceeds to the next step.
         */
        void test1() {

            /**
             * If you use SET CHLAUTH TYPE(BLOCKADDR), it must have the generic channel name CHLAUTH(*) and nothing else.
             * You must block access from the specified addresses using any channel name.
             */

            // 定义一个匹配所有的规则
            execMqsc("set chlauth('*') type(blockaddr) ADDRLIST('*')  action(replace)");


            Exception exception = catchException((() -> {


                JmsConnectionFactory connectionFactory = getConnectionFactory(QM, DEV_APP_SVRCONN, APP, PASSWORD);

                Connection connection = connectionFactory.createConnection();

                assertThat(connection).isNotNull();

                connection.close();

            }));

            log.info("exception: {}", exception);


            assertThat(exception).isInstanceOf(JMSException.class).hasCauseInstanceOf(MQException.class);


            assertThat(exception.getCause()).isInstanceOf(MQException.class).extracting("reasonCode").isEqualTo(2009);

        }


        @Test
        @DisplayName("blockaddr的优先级高于channel定义验证")
        /**
         Step 3: Read data from the channel
         IBM MQ now reads the data into a buffer, and starts to process the sent information.
         Step 4: Look up the channel definition
         In the first data flow, IBM MQ sends, amongst other things, the name of the channel which the sending end is trying to start.
         The receiving queue manager can then look up the channel definition, which has all the settings that are specified for the channel.
         */
        void test2() {

            /**
             * If you use SET CHLAUTH TYPE(BLOCKADDR), it must have the generic channel name CHLAUTH(*) and nothing else.
             * You must block access from the specified addresses using any channel name.
             */

            // 定义一个匹配所有的规则
            execMqsc("set chlauth('*') type(blockaddr) ADDRLIST('*')  action(replace)");


            Exception exception = catchException((() -> {


                JmsConnectionFactory connectionFactory = getConnectionFactory(QM, "notEXIST", APP, PASSWORD);

                Connection connection = connectionFactory.createConnection();

                assertThat(connection).isNotNull();

                connection.close();

            }));

            log.info("exception: {}", exception);


            assertThat(exception).isInstanceOf(JMSException.class).hasCauseInstanceOf(MQException.class);


            assertThat(exception.getCause()).isInstanceOf(MQException.class).extracting("reasonCode").isEqualTo(2009);

        }

        @Test
        @DisplayName("用户认证， 提供一个不存在的userId")
        /**
         Step 7: Adopt MQCSP user (if ChlauthEarlyAdopt is Y and ADOPTCTX=YES)
         The user Id asserted by the client is authenticated.
         If CONNAUTH is using LDAP to map an asserted distinguished name to a short user Id, the mapping happens in this step.
         If authentication is successful, the user Id is adopted by the channel and is used by the CHLAUTH mapping step.         */
        void test3() {


            Exception exception = catchException((() -> {


                JmsConnectionFactory connectionFactory = getConnectionFactory(QM, DEV_APP_SVRCONN, APP + "xx", PASSWORD);

                Connection connection = connectionFactory.createConnection();

                assertThat(connection).isNotNull();

                connection.close();

            }));

            log.info("exception: {}", exception);


            assertThat(exception).isInstanceOf(JMSException.class).hasCauseInstanceOf(MQException.class);


            assertThat(exception.getCause()).isInstanceOf(MQException.class).extracting("reasonCode").isEqualTo(2035);

        }


        @Test
        @DisplayName("USERSRC(NOACCESS)验证")
        /**
         Step 8: CHLAUTH mapping
         The CHLAUTH cache is inspected again to look for the mapping rules SSLPEERMAP, USERMAP, QMGRMAP, and ADDRESSMAP.
         The rule that matches the incoming channel most specifically is used. If the rule has USERSRC(CHANNEL) or (MAP), the channel continues on binding.
         If the CHLAUTH rules evaluate to a rule with USERSRC(NOACCESS), the application is blocked from connecting to the channel, unless the credentials are subsequently overridden with a valid user ID and password in Step 9.
         */
        void test4() {


            // 定义一个匹配所有的规则
            execMqsc("SET CHLAUTH (" +
                     DEV_APP_SVRCONN +
                     "" +
                     ") TYPE(ADDRESSMAP) ADDRESS('*') USERSRC(NOACCESS) action(replace)");


            Exception exception = catchException((() -> {


                JmsConnectionFactory connectionFactory = getConnectionFactory(QM, DEV_APP_SVRCONN, APP, PASSWORD);

                Connection connection = connectionFactory.createConnection();

                assertThat(connection).isNotNull();

                connection.close();

            }));

            log.info("exception: {}", exception);


            assertThat(exception).isInstanceOf(JMSException.class).hasCauseInstanceOf(MQException.class);


            assertThat(exception.getCause()).isInstanceOf(MQException.class).extracting("reasonCode").isEqualTo(2035);

        }


        @Test
        @DisplayName("CHCKCLNT(OPTIONAL) 验证")
        /**

         Step 10: Authenticate the user
         The AUTHINFO TYPE(IDPWOS) has an attribute called CHCKCLNT. If the value is changed to REQUIRED all client applications have to supply a valid user ID and password.

         NONE
         Authentication credentials that are supplied by applications are not checked.
         OPTIONAL
         Ensures that any credentials that are provided by an application are valid. However, it is not mandatory for applications to provide authentication credentials. This option might be useful during migration, for example.
         If you:
         Provide the username and password, they are authenticated.
         Do not provide the username and password, connection is allowed.
         Provide the username, but not the password you receive an error.



         Step13: Validate CHLAUTH CHCKCLNT requirements
         If the CHLAUTH rule that was selected in step 8 additionally specifies a CHCKCLNT value of REQUIRED or REQDADM then validation is done to ensure that a valid CONNAUTH userid was provided to meet the requirement.
         If CHCKCLNT(REQUIRED) is set a user must have been authenticated in step 7 or 10. Otherwise the connection is rejected.
         If CHCKCLNT(REQDADM) is set a user must have been authenticated in step 7 or 10 if this connection is determined to be privileged. Otherwise the connection is rejected.
         If CHCKCLNT(ASQMGR) is set then this step is skipped.

         如果需要在 AUTHINFO TYPE(IDPWOS) 中忽略用户名和密码校验， 则需要把 CHLAUTH CHCKCLNT 也设置为 ASQMGR， 否则的话会提示 MQRC_SECURITY_ERROR (2063)， 表示你的安全策略有冲突

         */
        void test5() {


            execMqsc("ALTER AUTHINFO(DEV.AUTHINFO) AUTHTYPE(IDPWOS) CHCKCLNT(OPTIONAL)");

            execMqsc("SET CHLAUTH(DEV.APP.SVRCONN) TYPE(ADDRESSMAP) ADDRESS(*) USERSRC(CHANNEL) CHCKCLNT(ASQMGR) action(replace)");


            Exception exception = catchException((() -> {


                JmsConnectionFactory connectionFactory = getConnectionFactory(QM, DEV_APP_SVRCONN, null, null);

                Connection connection = connectionFactory.createConnection();

                assertThat(connection).isNotNull();

                connection.close();

            }));

            log.info("exception: {}", exception);


            assertThat(exception).isNull();


        }
    }
}



