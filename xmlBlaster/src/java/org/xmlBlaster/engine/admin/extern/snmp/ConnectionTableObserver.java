/**
  ConnectionTableObserver adds itself to the integer bag as observer. 
  When ConnectionTableObserver receives an update, it .... 
 */
package org.xmlBlaster.engine.admin.extern.snmp;

import java.util.*;
import jax.*;

public class ConnectionTableObserver implements Observer {

      private ConnectionTableSubject connectionTableSubject;
      private AgentXSession session;
      private ConnectionEntryImpl connectionEntryImpl;
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
	  switch(connectionTableSubject.opCode) {
	  case ConnectionTableSubject.INSERT:   
            if( o == connectionTableSubject ) {
		String connectionHost = connectionTableSubject.connectionEntryImplPeer.get_connectionHost();
		long connectionPort = connectionTableSubject.connectionEntryImplPeer.get_connectionPort();
                  if (connectionHashtable.containsKey(connectionHost + connectionPort)) {
                     System.out.println("A connection to " + connectionHost + connectionPort + " already exists.");
                  }
                  else {
                      System.out.println("Insert connection = " + connectionHost + connectionPort);
		      // new connectionIndex
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

                          // insert connectionHost + connectionPort and connectionIndex in connectionHashtable
                          connectionHashtable.put(connectionHost + connectionPort, new Integer(connectionIndex));
  
                          // insert new connectionEntry in connectionTable
                          connectionEntryImpl = new ConnectionEntryImpl(connectionTableSubject.nodeIndex.intValue(),
                              connectionIndex, 
                              connectionTableSubject.connectionEntryImplPeer);
                          connectionTable.addEntry(connectionEntryImpl);

                          // increment node table reference count
                          connectionTableSubject.nodeTableObserver.increment(connectionTableSubject.nodeIndex);
                      }
                  }
            }
            break;
         
	  case ConnectionTableSubject.REMOVE:
            if( o == connectionTableSubject ) {
                  System.out.println("Remove a connection table entry.");
            }
	    break;

	  default:
            System.out.println("Unknown table operation code: " + connectionTableSubject.opCode);
            break;
          } // end switch   
      }

}











