package org.xmlBlaster.engine.admin.extern.snmp;


import java.util.ArrayList;
import java.util.Iterator;

/** 
 * ConnectionTableSubject holds onto connection entries. 
 * The ConnectionTableSubject also allows Observers to add and remove themselves.
 * In order to add or remove a connection entry notifyObservers() is called.
 * @version @VERSION@
 * @author Udo Thalmann
 */
public class ConnectionTableSubject implements Subject {

    public ConnectionEntryImplPeer connectionEntryImplPeer;
    public NodeTableObserver nodeTableObserver;
    public ArrayList observers = new ArrayList();
    public Integer nodeIndex;
    public int opCode;
    public static final int INSERT = 0;
    public static final int REMOVE = 1;

    /**
     * Holds a reference to nodeTableObserver.
     * @param NodeTableObserver provides access to nodeIndex.
     */
    public ConnectionTableSubject(NodeTableObserver nodeTableObserver) {
	this.nodeTableObserver = nodeTableObserver;
    }

    /**
     * Calls notifyObservers in order to add a new connection entry to connection table.
     * @param NodeName node to wich connection entry belongs.
     * @param ConnectionEntryImplPeer connection entry to be added.
     */
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
 
    /**
     * Calls notifyObservers in order to remove a connection entry from connection table.
     * @param NodeName  node to wich connection entry belongs.
     * @param ConnectionEntryImplPeer connection entry to be removed.
     */
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













