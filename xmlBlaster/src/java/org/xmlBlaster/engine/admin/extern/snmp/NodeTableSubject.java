package org.xmlBlaster.engine.admin.extern.snmp;


import java.util.*;
import java.util.Iterator;

/** 
 * NodeTableSubject holds onto node entries. 
 * The NodeTableSubject also allows Observers to add and remove themselves.
 * In order to add or remove a node entry notifyObservers() is called.
 * @version @VERSION@
 * @author Udo Thalmann
 */
public class NodeTableSubject implements Subject {

    public NodeEntryImplPeer nodeEntryImplPeer;
    public ArrayList observers = new ArrayList();
    public int opCode;
    public static final int INSERT = 0;
    public static final int REMOVE = 1;

    /**
     * Calls notifyObservers() in order to add a new node entry to node table.
     * @param NodeEntryImplPeer node entry to be added.
     */
    public void addEntry(NodeEntryImplPeer nodeEntryImplPeer) {

	this.nodeEntryImplPeer = nodeEntryImplPeer;
	opCode = INSERT;
	notifyObservers();
    }
 
    /**
     * Calls notifyObservers() in order to remove a node entry from node table.
     * @param NodeEntryImplPeer node entry to be removed.
     */ 
    public void removeEntry(NodeEntryImplPeer nodeEntryImplPeer) {

	this.nodeEntryImplPeer = nodeEntryImplPeer;
	opCode = REMOVE;
	notifyObservers();
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

























