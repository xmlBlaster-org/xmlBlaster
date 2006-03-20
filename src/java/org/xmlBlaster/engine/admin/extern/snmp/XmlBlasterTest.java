/*------------------------------------------------------------------------------
Name:      XmlBlasterTest.java
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
 * XmlBlasterTest
 * - contains a constructor and the main program.
 * - runs the xmlblaster subagent.
 *
 * @version @VERSION@
 * @author Udo Thalmann
 * @since 0.79g
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.snmp.html">admin.snmp requirement</a>
 */
public class XmlBlasterTest
{
    /**
     * XmlBlasterTest 
     * - opens an agentx connection to the master snmp agent.
     * - initializes snmp mib objects.
     * - sends traps, if trap conditions are true.
     *
     * @param String argv: contains optional host and port arguments.
     *               argv[0] = host, argv[1] = port.
     */
    public XmlBlasterTest(String argv[])
    {
	AgentXConnection connection;
	AgentXSession session;
	AgentXRegistration registration;
	String host;
	int port;
	long[] value = { 1, 3, 6, 1, 4, 1, 11662 };
        NodeScalarImpl nodeScalarImpl;
        NodeEntryImpl nodeEntryImpl;
        NodeEntryImplPeer nodeEntryImplPeer1;
        NodeEntryImplPeer nodeEntryImplPeer2;
        NodeEntryImplPeer nodeEntryImplPeer3;
        NodeEntryImplPeer nodeEntryImplPeer4;
        ConnectionEntryImplPeer connectionEntryImplPeer1;
        ConnectionEntryImplPeer connectionEntryImplPeer2;
        ClientEntryImplPeer clientEntryImplPeer1;
        ClientEntryImplPeer clientEntryImplPeer2;
        SessionEntryImplPeer sessionEntryImplPeer1;
        SessionEntryImplPeer sessionEntryImplPeer2;
        NodeTable nodeTable;
        NodeTableSubject nodeTableSubject;
        NodeTableObserver nodeTableObserver;
        ConnectionTableSubject connectionTableSubject;
        ConnectionTableObserver connectionTableObserver;
        ClientTableSubject clientTableSubject;
        ClientTableObserver clientTableObserver;
        SessionTableSubject sessionTableSubject;
        SessionTableObserver sessionTableObserver;
        boolean sleep = true;

        //System.setProperty("jax.debug", "true");

	if (argv.length >= 1) {
	    host = argv[0];
	} else {
	    host = "localhost";
	}
	if (argv.length >= 2) {
	    port = Integer.parseInt(argv[1]);
	} else {
	    port = 0;
	}
        if (argv.length >= 3) {
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

        nodeEntryImplPeer1 = new NodeEntryImplPeer("node11", "host11", 111, 1161, 80, "err1.log", 1);
        nodeEntryImplPeer2 = new NodeEntryImplPeer("node22", "host22", 222, 1162, 20, "err2.log", 2);
        nodeEntryImplPeer3 = new NodeEntryImplPeer("node33", "host33", 333, 3333, 33, "err3.log", 3);
        nodeEntryImplPeer4 = new NodeEntryImplPeer("node44", "host44", 444, 4444, 44, "err4.log", 4);

        connectionEntryImplPeer1 = new ConnectionEntryImplPeer("hostAAA", 4711, "192.47.11", 5);
        connectionEntryImplPeer2 = new ConnectionEntryImplPeer("hostBBB", 2222, "3.3.3.3.3", 335);

        clientEntryImplPeer1 = new ClientEntryImplPeer("client111", 1, 1, 1111, 11, 1, 111, 11);
        clientEntryImplPeer2 = new ClientEntryImplPeer("client222", 2, 2, 2222, 22, 2, 222, 22);

        sessionEntryImplPeer1 = new SessionEntryImplPeer("session1", 111, 50, 1, 1);
        sessionEntryImplPeer2 = new SessionEntryImplPeer("session2", 222, 60, 2, 2);

        // add entries to concrete subjects using the observer pattern
        nodeTableSubject.addEntry(nodeEntryImplPeer1);
        nodeTableSubject.addEntry(nodeEntryImplPeer2);
        nodeTableSubject.addEntry(nodeEntryImplPeer3);
        nodeTableSubject.addEntry(nodeEntryImplPeer4);
        connectionTableSubject.addEntry(nodeEntryImplPeer1.get_nodeName(), connectionEntryImplPeer1);
        connectionTableSubject.addEntry(nodeEntryImplPeer2.get_nodeName(), connectionEntryImplPeer2);
        clientTableSubject.addEntry(nodeEntryImplPeer3.get_nodeName(), clientEntryImplPeer1);
        clientTableSubject.addEntry(nodeEntryImplPeer2.get_nodeName(), clientEntryImplPeer1);
        clientTableSubject.addEntry(nodeEntryImplPeer2.get_nodeName(), clientEntryImplPeer2);
        sessionTableSubject.addEntry(nodeEntryImplPeer2.get_nodeName(), 
                                     clientEntryImplPeer1.get_clientName(),
                                     sessionEntryImplPeer1);
        sessionTableSubject.addEntry(nodeEntryImplPeer2.get_nodeName(), 
                                     clientEntryImplPeer1.get_clientName(),
                                     sessionEntryImplPeer2);

        // remove entries
        nodeTableSubject.removeEntry(nodeEntryImplPeer3);
        nodeTableSubject.removeEntry(nodeEntryImplPeer1);
        connectionTableSubject.removeEntry(nodeEntryImplPeer1.get_nodeName(), connectionEntryImplPeer1);

        if (sleep) {
           try {
               Thread.sleep(2000);
           } catch (InterruptedException e) {}
        }

        nodeTableObserver.sendTrap(session);

        if (sleep) {
           //while (true) {
              try {
                  Thread.sleep(90000);
              } catch (InterruptedException e) {}
           //}
        }
        else {
           return;
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
     * Main program 
     * - calls XmlBlasterTest subagent constructor.
     *
     * @param String argv: contains optional host and port arguments.
     *               argv[0] = host, argv[1] = port.
     */
    public static void main(String argv[])
    {
	new XmlBlasterTest(argv);
    }
}











