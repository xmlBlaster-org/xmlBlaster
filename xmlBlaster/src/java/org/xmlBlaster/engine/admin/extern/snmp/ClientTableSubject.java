/** 
  ClientTableSubject holds onto client entries. 
  The ClientTableSubject also allows Observers to add and remove themselves.
  In order to add a client entry a nodeName must be given.
  Using the nodeName a nodeIndex is computed by getIndex.
  Only if a nodeIndex exists, a client entry can be added using notifyObservers. 
 */
package org.xmlBlaster.engine.admin.extern.snmp;


import java.util.ArrayList;
import java.util.Iterator;

public class ClientTableSubject implements Subject {

      public ClientEntryImplPeer clientEntryImplPeer;
      public NodeTableObserver nodeTableObserver;
      public ArrayList observers = new ArrayList();
      public Integer nodeIndex;
      public String nodeName;
      public int opCode;
      public static final int INSERT = 0;
      public static final int REMOVE = 1;

      public ClientTableSubject(NodeTableObserver nodeTableObserver) {
          this.nodeTableObserver = nodeTableObserver;
      }

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










