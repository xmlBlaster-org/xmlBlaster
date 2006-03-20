/*------------------------------------------------------------------------------
Name:      SubagentTest.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   xmlBlaster to SNMP proxy class
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin.extern.snmp;

import java.lang.Integer;

import jax.*;

/** 
 * SubagentTest contains a constructor and the main program.
 * Runs the xmlblaster subagent.
 *
 * @version @VERSION@
 * @author Udo Thalmann
 * @since 0.79g
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.snmp.html">admin.snmp requirement</a>
 */
public class SubagentTest
{
    AgentXSession session;
    NodeTableSubject nodeTableSubject;
    NodeTableObserver nodeTableObserver;
    ConnectionTableSubject connectionTableSubject;
    ConnectionTableObserver connectionTableObserver;
    ClientTableSubject clientTableSubject;
    ClientTableObserver clientTableObserver;
    SessionTableSubject sessionTableSubject;
    SessionTableObserver sessionTableObserver;

    /**
     * Opens an agentx connection to the master snmp agent.
     * Initializes snmp mib objects.
     * executes a testcase according to testcase number.
     *
     * @param argv contains optional testcase, host and port arguments.
     * argv[0] = testcase number, argv[1] = host, argv[2] = port.
     */
    public SubagentTest(String argv[])
    {
	AgentXConnection connection;
	AgentXRegistration registration;
	String host;
	int port;
        int testCase = 0;
	long[] value = { 1, 3, 6, 1, 4, 1, 11662 };
        NodeScalarImpl nodeScalarImpl;
        boolean sleep = true;

        //System.setProperty("jax.debug", "true");

        if (argv.length >= 1) {
	    testCase = Integer.parseInt(argv[0]);
        }
	if (argv.length >= 2) {
	    host = argv[1];
	} else {
	    host = "localhost";
	}
	if (argv.length >= 3) {
	    port = Integer.parseInt(argv[2]);
	} else {
	    port = 0;
	}
        if (argv.length >= 4) {
            sleep = false;
        }

	try {
	    System.out.print("connection to ");
            if (port != 0) {
	        System.out.print(host + ", " + port);
		connection = new AgentXConnection(host, port);
            }
	    else
		connection = new AgentXConnection(host);

	    System.out.println(" ... established");
	    session = new AgentXSession();
	    connection.openSession(session);

	    registration = new AgentXRegistration(new AgentXOID(value));
	    session.register(registration);

	} catch (Exception e) {
	    System.out.println(" ... not established");
	    System.err.println(e);
	    return;
	}

        nodeScalarImpl = new NodeScalarImpl();
      	session.addGroup(nodeScalarImpl);

        // create concrete subjects and observers (observer pattern)
        nodeTableSubject = new NodeTableSubject();
        nodeTableObserver = new NodeTableObserver(nodeTableSubject, session);
        connectionTableSubject = new ConnectionTableSubject(nodeTableObserver);
        connectionTableObserver = new ConnectionTableObserver(connectionTableSubject, session);
        clientTableSubject = new ClientTableSubject(nodeTableObserver);
        clientTableObserver = new ClientTableObserver(clientTableSubject, session);
        sessionTableSubject = new SessionTableSubject(nodeTableObserver, clientTableObserver);
        sessionTableObserver = new SessionTableObserver(sessionTableSubject, session);

        switch (testCase) {
	    case 0:
		testProc0();
		break;
	    case 1:
                testProc1();
	        break;
	    case 2:
                testProc2();
	        break;
	    case 3:
                testProc3();
		break;
	    case 4:
                testProc4();
		break;
	    case 5:
		testProc5();
		break;
	    case 6:
		testProc1();
		break;
	    default:
		System.out.println("No testcase argument");
        }

        if (sleep) {
           try {
               Thread.sleep(90000);
           } catch (InterruptedException e) {}
        }

	try {
	    session.unregister(registration);
	    session.close(AgentXSession.REASON_SHUTDOWN);
	    connection.close();
	} catch (Exception e) {
	    System.err.println(e);
	}

    }

    /**
     * Tests trap functionality.
     */
    public void testProc0() {
	System.out.println("+++ testProc 0 +++");
        System.setProperty("jax.debug", "true");

        // clientTableThresholdOverflow trap
        NodeEntryImplPeer nodeEntryImplPeer1;
        nodeEntryImplPeer1 = new NodeEntryImplPeer("node1", "host1", 111, 1111, 11, "err1.log", 1);
        nodeTableSubject.addEntry(nodeEntryImplPeer1);
        nodeTableObserver.sendTrap(session);

        // clientQueueThresholdOverflow & sessionTableThresholdOverflow trap
        ClientEntryImplPeer clientEntryImplPeer1;
        clientEntryImplPeer1 = new ClientEntryImplPeer("client1", 1, 1, 1111, 11, 1, 111, 11);
        clientTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), clientEntryImplPeer1);
        clientTableObserver.sendTrap(session);

        // cbQueueThresholdOverflow trap
        SessionEntryImplPeer sessionEntryImplPeer1;
        sessionEntryImplPeer1 = new SessionEntryImplPeer("session1", 111, 11, 1, 1);
        sessionTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), 
                                     clientEntryImplPeer1.get_clientName(),
                                     sessionEntryImplPeer1);
        sessionTableObserver.sendTrap(session);

    }

    /**
     * Tests valid addEntry sequence.
     */
    public void testProc1() {
	System.out.println("+++ testProc 1 +++");

        NodeEntryImplPeer nodeEntryImplPeer1;
        ConnectionEntryImplPeer connectionEntryImplPeer1;
        ClientEntryImplPeer clientEntryImplPeer1;
        SessionEntryImplPeer sessionEntryImplPeer1;

        nodeEntryImplPeer1 = new NodeEntryImplPeer("node1", "host1", 111, 1111, 11, "err1.log", 1);
        connectionEntryImplPeer1 = new ConnectionEntryImplPeer("host1", 1111, "1.1.1.1", 1);
        clientEntryImplPeer1 = new ClientEntryImplPeer("client1", 1, 1, 1111, 11, 1, 111, 11);
        sessionEntryImplPeer1 = new SessionEntryImplPeer("session1", 111, 11, 1, 1);

        // add entries to concrete subjects using the observer pattern
        nodeTableSubject.addEntry(nodeEntryImplPeer1);
        connectionTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), connectionEntryImplPeer1);
        clientTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), clientEntryImplPeer1);
        sessionTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), 
                                     clientEntryImplPeer1.get_clientName(),
                                     sessionEntryImplPeer1);
    }

    /**
     * Tests valid addEntry sequence.
     */
    public void testProc2() {
	System.out.println("+++ testProc 2 +++");

        NodeEntryImplPeer nodeEntryImplPeer1;
        NodeEntryImplPeer nodeEntryImplPeer2;
        ConnectionEntryImplPeer connectionEntryImplPeer1;
        ClientEntryImplPeer clientEntryImplPeer1;
        ClientEntryImplPeer clientEntryImplPeer2;
        SessionEntryImplPeer sessionEntryImplPeer1;
        SessionEntryImplPeer sessionEntryImplPeer2;

        nodeEntryImplPeer1 = new NodeEntryImplPeer("node1", "host1", 111, 1111, 11, "err1.log", 1);
        nodeEntryImplPeer2 = new NodeEntryImplPeer("node2", "host2", 222, 2222, 22, "err2.log", 2);
        connectionEntryImplPeer1 = new ConnectionEntryImplPeer("host1", 1111, "1.1.1.1", 1);
        clientEntryImplPeer1 = new ClientEntryImplPeer("client1", 1, 1, 1111, 11, 1, 111, 11);
        clientEntryImplPeer2 = new ClientEntryImplPeer("client2", 2, 2, 2222, 22, 2, 222, 22);
        sessionEntryImplPeer1 = new SessionEntryImplPeer("session1", 111, 11, 1, 1);
        sessionEntryImplPeer2 = new SessionEntryImplPeer("session2", 222, 22, 2, 2);

        // add entries to concrete subjects using the observer pattern
        nodeTableSubject.addEntry(nodeEntryImplPeer1);
        nodeTableSubject.addEntry(nodeEntryImplPeer2);
        connectionTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), connectionEntryImplPeer1);
        connectionTableSubject.addEntry(nodeEntryImplPeer2.get_nodeName(), connectionEntryImplPeer1);
        clientTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), clientEntryImplPeer1);
        clientTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), clientEntryImplPeer2);
        sessionTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), 
                                     clientEntryImplPeer1.get_clientName(),
                                     sessionEntryImplPeer1);
        sessionTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), 
                                     clientEntryImplPeer1.get_clientName(),
                                     sessionEntryImplPeer2);
        sessionTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), 
                                     clientEntryImplPeer2.get_clientName(),
                                     sessionEntryImplPeer1);
    }

    /**
     * Tests invalid addEntry sequence. 
     * Children nodes cannot be added, because parent nodes do not exist.
     */
    public void testProc3() {
	System.out.println("+++ testProc 3 +++");

        NodeEntryImplPeer nodeEntryImplPeer1;
        NodeEntryImplPeer nodeEntryImplPeer2;
        ConnectionEntryImplPeer connectionEntryImplPeer1;
        ClientEntryImplPeer clientEntryImplPeer1;
        ClientEntryImplPeer clientEntryImplPeer2;
        SessionEntryImplPeer sessionEntryImplPeer1;

        nodeEntryImplPeer1 = new NodeEntryImplPeer("node1", "host1", 111, 1111, 11, "err1.log", 1);
        nodeEntryImplPeer2 = new NodeEntryImplPeer("node2", "host2", 222, 2222, 22, "err2.log", 2);
        connectionEntryImplPeer1 = new ConnectionEntryImplPeer("host1", 1111, "1.1.1.1", 1);
        clientEntryImplPeer1 = new ClientEntryImplPeer("client1", 1, 1, 1111, 11, 1, 111, 11);
        clientEntryImplPeer2 = new ClientEntryImplPeer("client2", 2, 2, 2222, 22, 2, 222, 22);
        sessionEntryImplPeer1 = new SessionEntryImplPeer("session1", 111, 11, 1, 1);

        // add entries to concrete subjects using the observer pattern
        nodeTableSubject.addEntry(nodeEntryImplPeer1);
        connectionTableSubject.addEntry(nodeEntryImplPeer2.get_nodeName(), connectionEntryImplPeer1);
        clientTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), clientEntryImplPeer1);
        clientTableSubject.addEntry(nodeEntryImplPeer2.get_nodeName(), clientEntryImplPeer1);
        sessionTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), 
                                     clientEntryImplPeer2.get_clientName(),
                                     sessionEntryImplPeer1);
        sessionTableSubject.addEntry(nodeEntryImplPeer2.get_nodeName(), 
                                     clientEntryImplPeer1.get_clientName(),
                                     sessionEntryImplPeer1);
    }

    /**
     * Tests invalid removeEntry sequence. 
     * Parent nodes cannot be removed because children nodes exist.
     */
    public void testProc4() {
	System.out.println("+++ testProc 4 +++");

        NodeEntryImplPeer nodeEntryImplPeer1;
        ConnectionEntryImplPeer connectionEntryImplPeer1;
        ClientEntryImplPeer clientEntryImplPeer1;
        SessionEntryImplPeer sessionEntryImplPeer1;

        nodeEntryImplPeer1 = new NodeEntryImplPeer("node1", "host1", 111, 1111, 11, "err1.log", 1);
        connectionEntryImplPeer1 = new ConnectionEntryImplPeer("host1", 1111, "1.1.1.1", 1);
        clientEntryImplPeer1 = new ClientEntryImplPeer("client1", 1, 1, 1111, 11, 1, 111, 11);
        sessionEntryImplPeer1 = new SessionEntryImplPeer("session1", 111, 11, 1, 1);

        // add entries to concrete subjects using the observer pattern
        nodeTableSubject.addEntry(nodeEntryImplPeer1);
        connectionTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), connectionEntryImplPeer1);
        clientTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), clientEntryImplPeer1);
        sessionTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), 
                                     clientEntryImplPeer1.get_clientName(),
                                     sessionEntryImplPeer1);

        // remove entries using the observer pattern
        clientTableSubject.removeEntry(nodeEntryImplPeer1.get_nodeName(), clientEntryImplPeer1);
        nodeTableSubject.removeEntry(nodeEntryImplPeer1);
    }

    /**
     * Tests valid removeEntry sequence.
     */
    public void testProc5() {
	System.out.println("+++ testProc 5 +++");

        NodeEntryImplPeer nodeEntryImplPeer1;
        ConnectionEntryImplPeer connectionEntryImplPeer1;
        ClientEntryImplPeer clientEntryImplPeer1;
        SessionEntryImplPeer sessionEntryImplPeer1;

        nodeEntryImplPeer1 = new NodeEntryImplPeer("node1", "host1", 111, 1111, 11, "err1.log", 1);
        connectionEntryImplPeer1 = new ConnectionEntryImplPeer("host1", 1111, "1.1.1.1", 1);
        clientEntryImplPeer1 = new ClientEntryImplPeer("client1", 1, 1, 1111, 11, 1, 111, 11);
        sessionEntryImplPeer1 = new SessionEntryImplPeer("session1", 111, 11, 1, 1);

        // add entries to concrete subjects using the observer pattern
        nodeTableSubject.addEntry(nodeEntryImplPeer1);
        connectionTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), connectionEntryImplPeer1);
        clientTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), clientEntryImplPeer1);
        sessionTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), 
                                     clientEntryImplPeer1.get_clientName(),
                                     sessionEntryImplPeer1);

        // remove entries
        connectionTableSubject.removeEntry(nodeEntryImplPeer1.get_nodeName(), connectionEntryImplPeer1);
        sessionTableSubject.removeEntry(nodeEntryImplPeer1.get_nodeName(), 
                                     clientEntryImplPeer1.get_clientName(),
                                     sessionEntryImplPeer1);
        clientTableSubject.removeEntry(nodeEntryImplPeer1.get_nodeName(), clientEntryImplPeer1);
        nodeTableSubject.removeEntry(nodeEntryImplPeer1);
    }

    public void testProc6() {
	System.out.println("+++ testProc 6 +++");
    }

    /**
     * Calls SubagentTest subagent constructor.
     *
     * @param argv contains optional testcase, host and port arguments.
     * argv[0] = testcase, argv[1] = host, argv[1] = port.
     */
    public static void main(String argv[])
    {
	new SubagentTest(argv);
    }
}















