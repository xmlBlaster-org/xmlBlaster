/** 
  SessionTableSubject holds onto connection entries. 
  The SessionTableSubject also allows Observers to add and remove themselves.
  In order to add a session entry a nodeName must be given.
  Using the nodeName a nodeIndex is computed by getIndex.
  Only if a nodeIndex exists, a session entry can be added using notifyObservers. 
 */
package org.xmlBlaster.engine.admin.extern.snmp;


import java.util.ArrayList;
import java.util.Iterator;

public class SessionTableSubject implements Subject {

    public SessionEntryImplPeer sessionEntryImplPeer;
    public NodeTableObserver nodeTableObserver;
    public ClientTableObserver clientTableObserver;
    public ArrayList observers = new ArrayList();
    public Integer nodeIndex;
    public Integer clientIndex;
    public String nodeName;
    public String clientName;
    public int opCode;
    public static final int INSERT = 0;
    public static final int REMOVE = 1;

    public SessionTableSubject(NodeTableObserver nodeTableObserver,
			       ClientTableObserver clientTableObserver) {
	this.nodeTableObserver = nodeTableObserver;
	this.clientTableObserver = clientTableObserver;
    }

    public void addEntry(String nodeName,
			 String clientName,
			 SessionEntryImplPeer sessionEntryImplPeer) {

	this.sessionEntryImplPeer = sessionEntryImplPeer;
	nodeIndex = nodeTableObserver.getIndex(nodeName);
	clientIndex = clientTableObserver.getIndex(nodeName + clientName);
	this.nodeName = nodeName;
	this.clientName = clientName;
	if (nodeIndex != null && clientIndex != null) {
	    opCode = INSERT;
	    notifyObservers();
	}
	else {
	    System.out.println("Cannot add session entry. Node " + nodeName + 
                               " or client " + clientName + " does not exist.");
	}
    }
 
    public void removeEntry(String nodeName,
			    String clientName,
			    SessionEntryImplPeer sessionEntryImplPeer) {
	this.sessionEntryImplPeer = sessionEntryImplPeer;
	nodeIndex = nodeTableObserver.getIndex(nodeName);
	clientIndex = clientTableObserver.getIndex(nodeName + clientName);
	this.nodeName = nodeName;
	this.clientName = clientName;
	if (nodeIndex != null && clientIndex != null) {
	    opCode = REMOVE;
	    notifyObservers();
	}
	else {
	    System.out.println("Cannot remove session entry. Node " + nodeName + 
                               " or client " + clientName + " does not exist.");
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













