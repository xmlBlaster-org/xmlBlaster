package org.xmlBlaster.engine.admin.extern.snmp;

import java.util.*;
import jax.*;

/**
 * ConnectionTableObserver represents the observer side of an observer pattern. 
 * When ConnectionTableObserver receives an update notification from ConnectionTableSubject, 
 * it adds or removes an entry to/from connectionTable. 
 *  
 * @version @VERSION@
 * @author Udo Thalmann
 */
public class ConnectionTableObserver implements Observer {

    private ConnectionTableSubject connectionTableSubject;
    private AgentXSession session;
    private ConnectionEntryImpl connectionEntryImpl;
    private ConnectionTable connectionTable;
    private Hashtable connectionHashtable;
    private BitSet indexSet;
    private final static int MAXINDX = 100;

    /**
     * Adds itself to the connectionTableSubject as observer.
     * Creates a new connectionTable and adds it to the agentX session.
     * Creates a Hashtable for connection (host/port, index) entries.
     * Creates a BitSet for available indices.
     * @param ConnectionTableSubject the subject, which calls the uptdate method.
     * @param AgentXSession the actual agentX session between master agent and subagent.
     */
    public ConnectionTableObserver( ConnectionTableSubject connectionTableSubject,
				    AgentXSession session ) {
	this.connectionTableSubject = connectionTableSubject;
	this.session = session;
	connectionTableSubject.addObserver( this );
	connectionTable = new ConnectionTable();
	session.addGroup(connectionTable);
	connectionHashtable = new Hashtable();
	indexSet = new BitSet();
	for (int i = 1; i <= MAXINDX; i++) {
	    indexSet.set(i);
	}
    }

    /**
     * Adds or removes a connection entry to/from the connection table.
     * Updates connection indexSet.
     * Updates connectionHashtable.
     * @param Subject connectionTableSubject which calls update.
     */
    public void update( Subject o ) {
	String connectionHost;
	long connectionPort;
	switch(connectionTableSubject.opCode) {
	    case ConnectionTableSubject.INSERT: 
		if( o == connectionTableSubject ) {
		    connectionHost = connectionTableSubject.connectionEntryImplPeer.get_connectionHost();
		    connectionPort = connectionTableSubject.connectionEntryImplPeer.get_connectionPort();
		    if (connectionHashtable.containsKey(connectionHost + connectionPort)) {
			System.out.println("A connection to " + connectionHost + connectionPort + " already exists.");
		    }
		    else {
			System.out.println("Insert connection = " + connectionHost + connectionPort);
		        // new connectionIndex
			int connectionIndex = 1;
			while (connectionIndex <= MAXINDX && !indexSet.get(connectionIndex)) {
			    connectionIndex++;
			}

			if (connectionIndex > MAXINDX) {
			    System.out.println("Error: connectionIndex > " + MAXINDX);
			}
			else {
			    // remove connectionIndex from indexSet
			    indexSet.clear(connectionIndex);

                            // insert connectionHost + connectionPort and connectionIndex in connectionHashtable
			    connectionHashtable.put(connectionHost + connectionPort, new Integer(connectionIndex));
  
                            // insert new connectionEntry in connectionTable
			    connectionEntryImpl = new ConnectionEntryImpl(connectionTableSubject.nodeIndex.intValue(),
									  connectionIndex,
									  connectionTableSubject.connectionEntryImplPeer);
			    connectionTable.addEntry(connectionEntryImpl);

                            // increment node table reference count
			    connectionTableSubject.nodeTableObserver.increment(connectionTableSubject.nodeIndex);
			} // end else
		    } // end else
		} // end if
		break;
         
	    case ConnectionTableSubject.REMOVE:
		if( o == connectionTableSubject ) {
		    System.out.println("Remove a connection table entry.");
		    connectionHost = connectionTableSubject.connectionEntryImplPeer.get_connectionHost();
		    connectionPort = connectionTableSubject.connectionEntryImplPeer.get_connectionPort();
		    if (!connectionHashtable.containsKey(connectionHost + connectionPort)) {
			System.out.println("A connection to " + connectionHost + connectionPort + " does not exist.");
		    }
		    else {
			System.out.println("Remove connection = " + connectionHost + connectionPort);
                        // remove node from connectionHashtable
			int remConInd = ((Integer)connectionHashtable.get(connectionHost + connectionPort)).intValue();
			connectionHashtable.remove(connectionHost + connectionPort);

                        // remove connection from connectionTable
			for (Enumeration ct=connectionTable.elements(); ct.hasMoreElements();) {
			    connectionEntryImpl = (ConnectionEntryImpl)ct.nextElement();
			    long nodeIndex = connectionEntryImpl.nodeIndex;
			    long connectionIndex = connectionEntryImpl.connectionIndex;
			    if ((connectionIndex == remConInd) && (nodeIndex == connectionTableSubject.nodeIndex.intValue())) {
				connectionTable.removeEntry(connectionEntryImpl);
                          
                                // decrement node table reference count
				connectionTableSubject.nodeTableObserver.decrement(connectionTableSubject.nodeIndex);
				break;
			    } // end if
			} // end for
                    } // end else
		} // end if
		break;

	    default:
		System.out.println("Unknown table operation code: " + connectionTableSubject.opCode);
		break;
          } // end switch   
    }
}











