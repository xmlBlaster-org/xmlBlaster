
package org.xmlBlaster.engine.admin.extern.snmp;


import java.util.ArrayList;
import java.util.Iterator;

/** 
 * ClientTableSubject holds onto client entries. 
 * The ClientTableSubject also allows Observers to add and remove themselves.
 * In order to add or remove a client entry notifyObservers() is called.
 * @version @VERSION@
 * @author Udo Thalmann
 */
public class ClientTableSubject implements Subject {

    public ClientEntryImplPeer clientEntryImplPeer;
    public NodeTableObserver nodeTableObserver;
    public ArrayList observers = new ArrayList();
    public Integer nodeIndex;
    public String nodeName;
    public int opCode;
    public static final int INSERT = 0;
    public static final int REMOVE = 1;

    /**
     * Holds a reference to nodeTableObserver.
     * @param NodeTableObserver provides access to nodeIndex.
     */
    public ClientTableSubject(NodeTableObserver nodeTableObserver) {
	this.nodeTableObserver = nodeTableObserver;
    }

    /**
     * Calls notifyObservers() in order to add a new client entry to client table.
     * @param NodeName identifies node to wich client entry belongs.
     * @param ClientEntryImplPeer the client entry to be added.
     */
    public void addEntry(String nodeName, ClientEntryImplPeer clientEntryImplPeer) {

	this.clientEntryImplPeer = clientEntryImplPeer;
	nodeIndex = nodeTableObserver.getIndex(nodeName);
	this.nodeName = nodeName;
	if (nodeIndex != null) {
	    opCode = INSERT;
	    notifyObservers();
	}
	else {
	    System.out.println("Cannot add client entry. Node entry " + nodeName + " does not exist.");
	}
    }
 
    /**
     * Calls notifyObservers in order to remove a client entry from client table.
     * @param NodeName identifies node to wich client entry belongs.
     * @param ClientEntryImplPeer the client entry to be removed.
     */
    public void removeEntry(String nodeName, ClientEntryImplPeer clientEntryImplPeer) {
	this.clientEntryImplPeer = clientEntryImplPeer;
	nodeIndex = nodeTableObserver.getIndex(nodeName);
	this.nodeName = nodeName;
	if (nodeIndex != null) {
	    opCode = REMOVE;
	    notifyObservers();
	}
	else {
	    System.out.println("Cannot remove client entry. Node entry " + nodeName + " does not exist.");
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















