package org.xmlBlaster.engine.admin.extern.snmp;


import java.util.ArrayList;
import java.util.Iterator;

/** 
 * SessionTableSubject holds onto session entries. 
 * The SessionTableSubject also allows Observers to add and remove themselves.
 * In order to add a session entry notifyObservers() is called. 
 * @version @VERSION@
 * @author Udo Thalmann
 */
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

    /**
     * Holds a reference to nodeTableObserver and clientTableObserver.
     * @param NodeTableObserver provides access to nodeIndex.
     * @param ClientTableObserver provides access to clientIndex.
     */
    public SessionTableSubject(NodeTableObserver nodeTableObserver,
			       ClientTableObserver clientTableObserver) {
	this.nodeTableObserver = nodeTableObserver;
	this.clientTableObserver = clientTableObserver;
    }

    /**
     * Calls notifyObservers() in order to add a new session entry to session table.
     * @param NodeName node to wich client entry belongs.
     * @param ClientName client to wich session entry belongs.
     * @param SessionEntryImplPeer session entry to be added.
     */
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
 
    /**
     * Calls notifyObservers() in order to remove a session entry from session table.
     * @param NodeName node to wich client entry belongs.
     * @param ClientName client to wich session entry belongs.
     * @param SessionEntryImplPeer session entry to be removed.
     */
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

    /**
     * Adds an observer to observer list.
     * @param Observer implements observer update method.
     */
    public void addObserver( Observer o ) {
	observers.add( o );
    }

    /**
     * Removes an observer from observer list.
     * @param Observer implements observer update method.
     */
    public void removeObserver( Observer o ) {
	observers.remove( o );
    }

    /**
     * Calls update method for all observers in observer list. 
     */
    private void notifyObservers() {
        // loop through and notify each observer
	Iterator i = observers.iterator();
	while( i.hasNext() ) {
	    Observer o = ( Observer ) i.next();
	    o.update( this );
	}
    }
}













