/** 
  ConnectionTableSubject holds onto connection entries. 
  The ConnectionTableSubject also allows Observers to add and remove themselves.
  In order to add a connection entry a nodeName must be given.
  Using the nodeName a nodeIndex is computed by getIndex.
  Only if a nodeIndex exists, a connection entry can be added using notifyObservers. 
 */
package org.xmlBlaster.engine.admin.extern.snmp;


import java.util.ArrayList;
import java.util.Iterator;

public class ConnectionTableSubject implements Subject {

    public ConnectionEntryImplPeer connectionEntryImplPeer;
    public NodeTableObserver nodeTableObserver;
    public ArrayList observers = new ArrayList();
    public Integer nodeIndex;
    public int opCode;
    public static final int INSERT = 0;
    public static final int REMOVE = 1;

    public ConnectionTableSubject(NodeTableObserver nodeTableObserver) {
	this.nodeTableObserver = nodeTableObserver;
    }

    public void addEntry(String nodeName, ConnectionEntryImplPeer connectionEntryImplPeer) {

	this.connectionEntryImplPeer = connectionEntryImplPeer;
	nodeIndex = nodeTableObserver.getIndex(nodeName);
	if (nodeIndex != null) {
	    opCode = INSERT;
	    notifyObservers();
	}
	else {
	    System.out.println("Cannot add connection entry. Node entry " + nodeName + " does not exist.");
	}
    }
 
    public void removeEntry(String nodeName, ConnectionEntryImplPeer connectionEntryImplPeer) {
	this.connectionEntryImplPeer = connectionEntryImplPeer;
	nodeIndex = nodeTableObserver.getIndex(nodeName);
	if (nodeIndex != null) {
	    opCode = REMOVE;
	    notifyObservers();
	}
	else {
	    System.out.println("Cannot remove connection entry. Node entry " + nodeName + " does not exist.");
	}
    }

    public void addObserver( Observer o ) {
	observers.add( o );
    }

    public void removeObserver( Observer o ) {
	observers.remove( o );
    }

    private void notifyObservers() {
        // loop through and notify each observer
	Iterator i = observers.iterator();
	while( i.hasNext() ) {
	    Observer o = ( Observer ) i.next();
	    o.update( this );
	}
    }
}










