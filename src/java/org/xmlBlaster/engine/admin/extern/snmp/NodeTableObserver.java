package org.xmlBlaster.engine.admin.extern.snmp;

import java.util.*;
import jax.*;

/**
 * NodeTableObserver represents the observer side of an observer pattern. 
 * When NodeTableObserver receives an update notification from NodeTableSubject, 
 * it adds or removes an entry to/from nodeTable. 
 *  
 * @version @VERSION@
 * @author Udo Thalmann
 */
public class NodeTableObserver implements Observer {

    private NodeTableSubject nodeTableSubject;
    private AgentXSession session;
    private NodeEntryImpl nodeEntryImpl;
    private NodeTable nodeTable;
    private Hashtable nodeHashtable;
    private BitSet indexSet;
    private Hashtable refCounts;
    private final static int MAXINDX = 100;

    /**
     * Adds itself to the nodeTableSubject as observer.
     * Creates a new nodeTable and adds it to the agentX session.
     * Creates a Hashtable for (node, index) entries.
     * Creates a BitSet for available indices.
     * Creates a Hashtable for (node, reference) entries,
     *  where reference is a counter of referenced client entries.
     * @param NodeTableSubject the subject, which calls the update method.
     * @param AgentXSession the actual agentX session between master agent and subagent.
     */
    public NodeTableObserver( NodeTableSubject nodeTableSubject,
			      AgentXSession session ) {
	this.nodeTableSubject = nodeTableSubject;             
	this.session = session;
	nodeTableSubject.addObserver( this );
	nodeTable = new NodeTable();
	session.addGroup(nodeTable);
	nodeHashtable = new Hashtable();
	refCounts = new Hashtable();
	indexSet = new BitSet();
	for (int i = 1; i <= MAXINDX; i++) {
	    indexSet.set(i);
	}
    }

    /**
     * Increments the referenced client entries of this node.
     * @param NodeIndex identifies a node entry in the node table.
     * @return int number of referenced client entries or -1,
     * if nodeIndex identifies no node in the node table.
     */
    public int increment(Integer nodeIndex) {
	// find nodeName with nodeIndex
	Enumeration e = nodeHashtable.keys();
	String nodeName = (String)e.nextElement();
	while ((nodeHashtable.get(nodeName) != nodeIndex) && e.hasMoreElements()) {
	    nodeName = (String)e.nextElement();
	}

	if (nodeHashtable.get(nodeName) != nodeIndex) {
	    return -1;
	}

	// increment refsCount of nodeName  
	Integer rc = (Integer)refCounts.get(nodeName);
	if (rc != null) {
            refCounts.put(nodeName, new Integer(rc.intValue() + 1));
            System.out.println("increment, " + nodeName + ", " + ((Integer)refCounts.get(nodeName)).intValue());
            return rc.intValue() + 1;
	}
	else {
            refCounts.put(nodeName, new Integer(1));
            System.out.println("increment, " + nodeName + ", 1");
            return 1;
	}
    } 

    /**
     * Decrements the referenced client entries of this node.
     * @param NodeIndex identifies a node entry in the node table.
     * @return int number of referenced client entries or -1,
     * if nodeIndex identifies no node in the node table.
     */
    public int decrement(Integer nodeIndex) {
	// find nodeName with nodeIndex
	Enumeration e = nodeHashtable.keys();
	String nodeName = (String)e.nextElement();
	while ((nodeHashtable.get(nodeName) != nodeIndex) && e.hasMoreElements()) {
	    nodeName = (String)e.nextElement();
	}

	if (nodeHashtable.get(nodeName) != nodeIndex) {
	    return -1;
	}

	Integer rc = (Integer)refCounts.get(nodeName);
	if (rc == null) {
	    return -1;
	}

	if (rc.intValue() > 1) {
            refCounts.put(nodeName, new Integer(rc.intValue() - 1));
            return rc.intValue() - 1;
	}
	else {
	    refCounts.remove(nodeName);
            return 0;
	}
    } 

    /**
     * For each node table entry sendTrap checks trap condition maxClients * clientThreshold < numClients.
     * Sends a ClientTableThresholdOverflow trap if the condition is fulfilled.
     * @param AgentXSession the actual agentX session between master agent and subagent.
     */
    public void sendTrap(AgentXSession session) {
	ClientTableThresholdOverflow clientTableNotify;
	long numClients;
	long maxClients;
	long clientThreshold;
	for (Enumeration nt=nodeTable.elements(); nt.hasMoreElements();) {
            nodeEntryImpl = (NodeEntryImpl)nt.nextElement();
            numClients = nodeEntryImpl.get_numClients();
            maxClients = nodeEntryImpl.get_maxClients();
            clientThreshold = nodeEntryImpl.get_clientThreshold();
            System.out.println("maxClients: " + maxClients + 
                               ", clientThreshold: " + clientThreshold +
                               ", numClients: " + numClients);
            if (maxClients * clientThreshold < numClients) {
		try {
		    clientTableNotify = new ClientTableThresholdOverflow(nodeEntryImpl, nodeEntryImpl,
									 nodeEntryImpl, nodeEntryImpl);
		    session.notify(clientTableNotify);
		} catch (Exception e) {
		    System.err.println(e);
		}
            } // end if
	} // end for
    }

    /**
     * Returns an index to nodeTable given a nodeName.
     * @param Key nodeName-key to nodeHashtable.
     * @return Integer index to nodeTable.
     */
    public Integer getIndex(String key) {
	return (Integer)nodeHashtable.get(key);
    }

    /**
     * Adds or removes a node entry to/from the node table.
     * Updates node indexSet.
     * Updates nodeHashtable.
     * @param Subject clientTableSubject which calls update.
     */
    public void update( Subject o ) {
	String nodeName;
	switch(nodeTableSubject.opCode) {
	    case NodeTableSubject.INSERT: 
		if( o == nodeTableSubject ) {
		    nodeName = nodeTableSubject.nodeEntryImplPeer.get_nodeName();
		    System.out.println("Insert a node with nodename = " + nodeName);
		    if (nodeHashtable.containsKey(nodeName)) {
			System.out.println("A node with nodename = " + nodeName + " already exists.");
		    }
		    else {
			System.out.println("Insert node = " + nodeName);
		        // new nodeIndex
			int insInd = 1;
			while (insInd <= MAXINDX && !indexSet.get(insInd)) {
			    insInd++;
			}

			if (insInd > MAXINDX) {
			    System.out.println("Error: insInd > " + MAXINDX);
			}
			else {
			    // remove insInd from indexSet
			    indexSet.clear(insInd);

                            // insert nodeName and insInd in nodeHashtable
			    nodeHashtable.put(nodeName, new Integer(insInd));
  
                            // insert new nodeEntry in nodeTable
			    nodeEntryImpl = new NodeEntryImpl(insInd, nodeTableSubject.nodeEntryImplPeer);
			    nodeTable.addEntry(nodeEntryImpl);
			} // end else
		    } // end else
		} // end if
		break;
         
	    case NodeTableSubject.REMOVE:
		if( o == nodeTableSubject ) {
		    nodeName = nodeTableSubject.nodeEntryImplPeer.get_nodeName();
		    System.out.println("Remove a node with nodename = " + nodeName);
		    if (!nodeHashtable.containsKey(nodeName)) {
			System.out.println("A node with nodename = " + nodeName + " does not exists.");
		    }
		    else {
			System.out.println("Remove node = " + nodeName);

                        // is node referenced by other table entries
			if (refCounts.get(nodeName) != null) {
			    System.out.println("node " + nodeName + " is referenced by ...");
			    break;
			}

                        // remove node from nodeHashtable
			int remInd = ((Integer)nodeHashtable.get(nodeName)).intValue();
			nodeHashtable.remove(nodeName);

                        // remove node from nodeTable
			for (Enumeration nt=nodeTable.elements(); nt.hasMoreElements();) {
			    nodeEntryImpl = (NodeEntryImpl)nt.nextElement();
			    long ind = nodeEntryImpl.nodeIndex;
			    if (ind == remInd) {
				nodeTable.removeEntry(nodeEntryImpl);
				break;
			    }
			} // end for

                        // insert index in indexSet
			indexSet.set(remInd);
		    } // end else
		} // end if
		break;

	    default:
		System.out.println("Unknown table operation code: " + nodeTableSubject.opCode);
		break;
          } // end switch   
    }
}














