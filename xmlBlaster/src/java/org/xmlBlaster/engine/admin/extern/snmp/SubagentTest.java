/*------------------------------------------------------------------------------
Name:      SubagentTest.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   xmlBlaster to SNMP proxy class
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin.extern.snmp;

import java.io.*;
import java.util.Vector;
import java.util.Enumeration;
import java.net.*;
import java.lang.Integer;

import jax.*;

/** 
 * SubagentTest
 * - contains a constructor and the main program.
 * - runs the xmlblaster subagent.
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
     * SubagentTest 
     * - opens an agentx connection to the master snmp agent.
     * - initializes snmp mib objects.
     * - sends traps, if trap conditions are true.
     *
     * @param String argv: contains optional host, port and testcase arguments.
     *               argv[0] = testcase number.
     *               argv[1] = host, 
     *               argv[2] = port,
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
        NodeEntryImpl nodeEntryImpl;
        NodeTable nodeTable;
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
        NodeEntryImplPeer nodeEntryImplPeer1;
        nodeEntryImplPeer1 = new NodeEntryImplPeer("node1", "host1", 111, 1111, 11, "err1.log", 1);
        nodeTableSubject.addEntry(nodeEntryImplPeer1);
        nodeTableObserver.sendTrap(session);
    }

    /**
     * Tests valid addEntry sequence.
     * Teststeps: add(node1(connection1, client1(session1)))
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
     * Teststeps: add(node1(connection1, client1(session1, session2), client2(session1)))
     *            add(node2(connection1))
     */
    public void testProc2() {
	System.out.println("+++ testProc 2 +++");

        NodeEntryImplPeer nodeEntryImplPeer1;
        NodeEntryImplPeer nodeEntryImplPeer2;
        ConnectionEntryImplPeer connectionEntryImplPeer1;
        ConnectionEntryImplPeer connectionEntryImplPeer2;
        ClientEntryImplPeer clientEntryImplPeer1;
        ClientEntryImplPeer clientEntryImplPeer2;
        SessionEntryImplPeer sessionEntryImplPeer1;
        SessionEntryImplPeer sessionEntryImplPeer2;
        SessionEntryImplPeer sessionEntryImplPeer3;

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

    public void testProc3() {
	System.out.println("+++ testProc 3 +++");
    }

    public void testProc4() {
	System.out.println("+++ testProc 4 +++");
    }

    /**
     * Tests valid removeEntry sequence.
     * Teststeps: add(node1(connection1, client1(session1)))
     *            remove(connection1, session1, client1, node1)
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
     * Main program 
     * - calls SubagentTest subagent constructor.
     *
     * @param String argv: contains optional host and port arguments.
     *               argv[0] = host, argv[1] = port.
     */
    public static void main(String argv[])
    {
	new SubagentTest(argv);
    }
}











