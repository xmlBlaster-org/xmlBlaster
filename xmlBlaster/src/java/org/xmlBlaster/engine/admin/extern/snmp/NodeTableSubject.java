/** 
 *  NodeTableSubject holds onto node entries. 
 *  The NodeTableSubject also allows Observers to add and remove themselves.
 *  @version @VERSION@
 *  @author Udo Thalmann
 */
package org.xmlBlaster.engine.admin.extern.snmp;


import java.util.*;
import java.util.Iterator;

public class NodeTableSubject implements Subject {

      public NodeEntryImplPeer nodeEntryImplPeer;
      public ArrayList observers = new ArrayList();
      public int opCode;
      private Hashtable refCounts;
      public static final int INSERT = 0;
      public static final int REMOVE = 1;

      public NodeTableSubject() {
	  refCounts = new Hashtable();
      }

      public void increment(String nodeName) {
         Integer rc = (Integer)refCounts.get(nodeName);
         if (rc != null) {
            refCounts.put(nodeName, new Integer(rc.intValue() + 1));
            System.out.println("increment, " + nodeName + ", " + ((Integer)refCounts.get(nodeName)).intValue());
         }
         else {
            refCounts.put(nodeName, new Integer(1));
            System.out.println("increment, " + nodeName + ", 1");
         }
      } 

      public void decrement(String nodeName) {
         int rc = ((Integer)refCounts.get(nodeName)).intValue();
         if (rc > 1) {
            refCounts.put(nodeName, new Integer(rc - 1));
         }
         else {
	     refCounts.remove(nodeName);
	 }
      } 

      /**
       * addEntry
       * - initializes attributes of a new node table entry.
       * - sets add-flag to true.
       * - notifies a NodeTableObserver, in order to add the new node table entry.
       * @param NodeEntryImplPeer nodeEntryImplPeer: 
       */
      public void addEntry(NodeEntryImplPeer nodeEntryImplPeer) {

	  this.nodeEntryImplPeer = nodeEntryImplPeer;
          opCode = INSERT;
          notifyObservers();
      }
 
      /**
       * removeEntry
       * - sets add-flag to false.
       * - notifies a NodeTableObserver, in order to remove the node table entry.
       * @param String nodeNameVal:
       */ 
      public void removeEntry(NodeEntryImplPeer nodeEntryImplPeer) {

	  this.nodeEntryImplPeer = nodeEntryImplPeer;
          opCode = REMOVE;
          String nodeName = nodeEntryImplPeer.get_nodeName();
          Integer rc = (Integer)refCounts.get(nodeName);
          if (rc == null) {
             notifyObservers();
          }
          else {
	      System.out.println("Node table entry " + nodeName + " is referenced " + rc + " times.");
          }
      }

      /**
       * addObserver
       * - allows an observer to subscribe in order to be notified 
       * in case of node table entry updates.
       * @param Observer o:
       */
      public void addObserver( Observer o ) {
            observers.add( o );
      }

      /**
       * removeObserver
       * - allows an observer to unsubscribe in order not to be notified 
       * in case of node table entry updates.
       * @param Observer o:
       */
      public void removeObserver( Observer o ) {
            observers.remove( o );
      }

      /**
       * notifyObservers
       * - notifies each subscribed observer that node table has changed.
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










