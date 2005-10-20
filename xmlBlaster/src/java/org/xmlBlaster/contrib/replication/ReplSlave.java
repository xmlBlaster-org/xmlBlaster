/*------------------------------------------------------------------------------
Name:      ReplSlave.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.util.Map;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.mime.I_AccessFilter;
import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * ReplSlave
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class ReplSlave implements I_ReplSlave, I_AccessFilter, I_Callback {

   public ReplSlave() {
      
   }
   
   /**
    * This is the first step in the process of requesting the initial Data.
    * <ul>
    *   <li>It clears the callback queue of the real slave</li>
    *   <li>It sends a message to the real slave to inform him that 
    *       a new initial update has been initiated. This is a PtP 
    *       message with a well defined topic, so administrators can 
    *       subscribe to it too. It is designated ${repl}InitStart.
    *   </li>
    *   <li>It then deactivates the callback dispatcher of the real slave</li>
    *   <li>makes a persistent subscription on behalf of the real slave
    *       by passing as a mime access filter an identifier for himself.
    *   </li>
    * </ul>
    * @see org.xmlBlaster.contrib.replication.I_ReplSlave#prepareForRequest()
    */
   public void prepareForRequest() throws Exception {
      // TODO Auto-generated method stub
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.contrib.replication.I_ReplSlave#requestInitialData()
    */
   public void requestInitialData() throws Exception {
      // TODO Auto-generated method stub
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.contrib.replication.I_ReplSlave#reactivateDestination()
    */
   public void reactivateDestination() throws Exception {
      // TODO Auto-generated method stub
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.contrib.replication.I_ReplSlave#checkForDestroy(java.lang.String)
    */
   public boolean checkForDestroy(String replKey) throws Exception {
      // TODO Auto-generated method stub
      return false;
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.contrib.dbwriter.I_ContribPlugin#init(org.xmlBlaster.contrib.I_Info)
    */
   public void init(I_Info info) throws Exception {
      // TODO Auto-generated method stub
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.contrib.dbwriter.I_ContribPlugin#shutdown()
    */
   public void shutdown() {
      // TODO Auto-generated method stub
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.contrib.dbwriter.I_EventHandler#update(java.lang.String, byte[], java.util.Map)
    */
   public void update(String topic, byte[] content, Map attrMap) throws Exception {
      // TODO Auto-generated method stub
   }
   
   
   // for the I_AccessFilter

   public String[] getMimeExtended() {
      // TODO Auto-generated method stub
      return null;
   }

   public String[] getMimeTypes() {
      // TODO Auto-generated method stub
      return null;
   }

   public String getName() {
      // TODO Auto-generated method stub
      return null;
   }

   public void initialize(Global glob) {
      // TODO Auto-generated method stub
      
   }

   public boolean match(SessionInfo receiver, MsgUnit msgUnit, Query query) throws XmlBlasterException {
      // TODO Auto-generated method stub
      return false;
   }

   // I_Callback

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      // TODO Auto-generated method stub
      return null;
   }
   
}
