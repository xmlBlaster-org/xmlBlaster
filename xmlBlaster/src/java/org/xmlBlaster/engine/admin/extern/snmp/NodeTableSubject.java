/** 
 *  NodeTableSubject holds onto node entries. 
 *  The NodeTableSubject also allows Observers to add and remove themselves.
 *  @version @VERSION@
 *  @author Udo Thalmann
 */
package org.xmlBlaster.engine.admin.extern.snmp;


import java.util.ArrayList;
import java.util.Iterator;

public class NodeTableSubject implements Subject {

      public NodeEntryImplPeer nodeEntryImplPeer;
      public boolean add;
      public ArrayList observers = new ArrayList();

      /**
       * addEntry
       * - initializes attributes of a new node table entry.
       * - sets add-flag to true.
       * - notifies a NodeTableObserver, in order to add the new node table entry.
       * @param NodeEntryImplPeer nodeEntryImplPeer: 
       */
      public void addEntry(NodeEntryImplPeer nodeEntryImplPeer) {

	  this.nodeEntryImplPeer = nodeEntryImplPeer;
          add = true;
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
          add = false;
          notifyObservers();
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










