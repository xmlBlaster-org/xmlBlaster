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

      public String connectionHost;
      public long connectionPort;
      public String connectionAddress;
      public int connectionProtocol;
      public ArrayList observers = new ArrayList();
      public Integer nodeIndex;

      public void addEntry(NodeTableObserver nodeTableObserver,
                         String nodeName,
                         String connectionHostVal,
                         long connectionPortVal,
                         String connectionAddressVal,
                         int connectionProtocolVal) {

            connectionHost = connectionHostVal;
            connectionPort = connectionPortVal;
            connectionAddress = connectionAddressVal;
            connectionProtocol = connectionProtocolVal;
            nodeIndex = nodeTableObserver.getIndex(nodeName);
            if (nodeIndex != null) {
                notifyObservers();
            }
            else {
                System.out.println("Cannot add connection entry. Node entry " + nodeName + " does not exist.");
            }
      }
 
      public Integer removeEntry( int index ) {
            return null;
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










