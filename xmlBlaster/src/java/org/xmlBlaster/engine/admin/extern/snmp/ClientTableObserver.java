/**
  ClientTableObserver adds itself to the integer bag as observer. 
  When ClientTableObserver receives an update, it .... 
 */
package org.xmlBlaster.engine.admin.extern.snmp;

import java.util.*;
import jax.*;

public class ClientTableObserver implements Observer {

      private ClientTableSubject clientTableSubject;
      private AgentXSession session;
      private ClientEntryImpl clientEntryImpl;
      private ClientTable clientTable;
      private Hashtable clientHashtable;
      private BitSet indexSet;
      private Hashtable refCounts;
      private final static int MAXINDX = 100;

      public ClientTableObserver( ClientTableSubject clientTableSubject,
                                AgentXSession session ) {
            this.clientTableSubject = clientTableSubject;               
            this.session = session;
            clientTableSubject.addObserver( this );
            clientTable = new ClientTable();
  	    session.addGroup(clientTable);
            clientHashtable = new Hashtable();
	    refCounts = new Hashtable();
            indexSet = new BitSet();
            for (int i = 1; i <= MAXINDX; i++) {
		indexSet.set(i);
            }
      }

      public int increment(Integer clientIndex) {
	 // find clientName with clientIndex
	 Enumeration e = clientHashtable.keys();
         String clientName = (String)e.nextElement();
         while ((clientHashtable.get(clientName) != clientIndex) && e.hasMoreElements()) {
	     clientName = (String)e.nextElement();
         }

         if (clientHashtable.get(clientName) != clientIndex) {
	     return -1;
         }

	 // increment refsCount of clientName  
         Integer rc = (Integer)refCounts.get(clientName);
         if (rc != null) {
            refCounts.put(clientName, new Integer(rc.intValue() + 1));
            System.out.println("increment, " + clientName + ", " + ((Integer)refCounts.get(clientName)).intValue());
            return rc.intValue() + 1;
         }
         else {
            refCounts.put(clientName, new Integer(1));
            System.out.println("increment, " + clientName + ", 1");
            return 1;
         }
      } 

      public int decrement(Integer clientIndex) {
	 // find clientName with clientIndex
	 Enumeration e = clientHashtable.keys();
         String clientName = (String)e.nextElement();
         while ((clientHashtable.get(clientName) != clientIndex) && e.hasMoreElements()) {
	     clientName = (String)e.nextElement();
         }

         if (clientHashtable.get(clientName) != clientIndex) {
	     return -1;
         }

         Integer rc = (Integer)refCounts.get(clientName);
         if (rc == null) {
	     return -1;
         }

         if (rc.intValue() > 1) {
            refCounts.put(clientName, new Integer(rc.intValue() - 1));
            return rc.intValue() - 1;
	 }
         else {
	    refCounts.remove(clientName);
            return 0;
	 }
      } 

      public Integer getIndex(String key) {
          return (Integer)clientHashtable.get(key);
      }

      public void update( Subject o ) {
          String clientName;
          String nodeName;
	  switch(clientTableSubject.opCode) {
	  case ClientTableSubject.INSERT:   
            if( o == clientTableSubject ) {
		  clientName = clientTableSubject.clientEntryImplPeer.get_clientName();
		  nodeName = clientTableSubject.nodeName;
                  if (clientHashtable.containsKey(nodeName + clientName)) {
                     System.out.println("A node/client  " + nodeName + clientName + " already exists.");
                  }
                  else {
                      System.out.println("Insert node/client " + nodeName + clientName);
		      // new clientIndex
                      int clientIndex = 1;
                      while (clientIndex <= MAXINDX && !indexSet.get(clientIndex)) {
			  clientIndex++;
                      }

                      if (clientIndex > MAXINDX) {
                         System.out.println("Error: clientIndex > " + MAXINDX);
                      }
                      else {
			  // remove clientIndex from indexSet
                          indexSet.clear(clientIndex);

                          // insert nodeName + clientName in clientHashtable
                          clientHashtable.put(nodeName + clientName, new Integer(clientIndex));
  
                          // insert new clientEntry in clientTable
                          clientEntryImpl = new ClientEntryImpl(clientTableSubject.nodeIndex.intValue(),
                              clientIndex, 
                              clientTableSubject.clientEntryImplPeer);
                          clientTable.addEntry(clientEntryImpl);

                          // increment node table reference count
                          clientTableSubject.nodeTableObserver.increment(clientTableSubject.nodeIndex);
                      }
                  }
            }
            break;
         
	  case ClientTableSubject.REMOVE:
            if( o == clientTableSubject ) {
                  System.out.println("Remove a client table entry.");
		  clientName = clientTableSubject.clientEntryImplPeer.get_clientName();
		  nodeName = clientTableSubject.nodeName;
                  if (!clientHashtable.containsKey(nodeName + clientName)) {
                     System.out.println("A node/client " + nodeName + clientName + " does not exist.");
                  }
                  else {
                      System.out.println("Remove node/client " + nodeName + clientName);

                      // is client referenced by other session table entries
                      if (refCounts.get(nodeName + clientName) != null) {
                          System.out.println("node/client " + nodeName + clientName + " is referenced by ...");
			  break;
                      }

                      // remove node from clientHashtable
                      int remInd = ((Integer)clientHashtable.get(nodeName + clientName)).intValue();
                      clientHashtable.remove(nodeName + clientName);

                      // remove client from clientTable
                      for (Enumeration ct=clientTable.elements(); ct.hasMoreElements();) {
                         clientEntryImpl = (ClientEntryImpl)ct.nextElement();
                         long nodeIndex = clientEntryImpl.nodeIndex;
                         long clientIndex = clientEntryImpl.clientIndex;
                         if ((clientIndex == remInd) && 
                             (nodeIndex == clientTableSubject.nodeIndex.intValue())) {
                             clientTable.removeEntry(clientEntryImpl);
			     break;
                         }
                      }
                  } // end else
            }
	    break;

	  default:
            System.out.println("Unknown table operation code: " + clientTableSubject.opCode);
            break;
          } // end switch   
      }

}











