/**
  NodeTableObserver adds itself to the integer bag as observer. 
  When NodeTableObserver receives an update, it .... 
 */
package org.xmlBlaster.engine.admin.extern.snmp;

import java.util.*;
import jax.*;

public class ConnectionTableObserver implements Observer {

      private ConnectionTableSubject connectionTableSubject;
      private AgentXSession session;
      private ConnectionEntry connectionEntry;
      private ConnectionTable connectionTable;
      private Hashtable connectionHashtable;
      private BitSet indexSet;
      private final static int MAXINDX = 100;

      public ConnectionTableObserver( ConnectionTableSubject connectionTableSubject,
                                AgentXSession session ) {
            this.connectionTableSubject = connectionTableSubject;               
            this.session = session;
            connectionTableSubject.addObserver( this );
            connectionTable = new ConnectionTable();
            session.addGroup(connectionTable);
            connectionHashtable = new Hashtable();
            indexSet = new BitSet();
            for (int i = 1; i <= MAXINDX; i++) {
                indexSet.set(i);
            }
      }

      public void update( Subject o ) {
            if( o == connectionTableSubject ) {
                  if (connectionHashtable.containsKey(connectionTableSubject.connectionHost)) {
                     System.out.println("A connection to " + connectionTableSubject.connectionHost + " already exists.");
                  }
                  else {
                      System.out.println("Insert connection = " + connectionTableSubject.connectionHost);
                      // new nodeIndex
                      int connectionIndex = 1;
                      while (connectionIndex <= MAXINDX && !indexSet.get(connectionIndex)) {
                          connectionIndex++;
                      }

                      if (connectionIndex > MAXINDX) {
                         System.out.println("Error: connectionIndex > " + MAXINDX);
                      }
                      else {
                          // remove connectionIndex from indexSet
                          indexSet.clear(connectionIndex);

                          // insert connectionHost and connectionIndex in connectionHashtable
                          connectionHashtable.put(connectionTableSubject.connectionHost, new Integer(connectionIndex));
  
                          // insert new connectionEntry in connectionTable
                          connectionEntry = new ConnectionEntryImpl(connectionTableSubject.nodeIndex.intValue(),
                              connectionIndex, 
                              connectionTableSubject.connectionHost,
                              connectionTableSubject.connectionPort,
                              connectionTableSubject.connectionAddress, 
                              connectionTableSubject.connectionProtocol);
                          connectionTable.addEntry(connectionEntry);
                      }
                  }
            }
      }

}








