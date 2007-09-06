/*------------------------------------------------------------------------------
Name:      PtPDestination.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.test.util;

import java.util.logging.Logger;

import junit.framework.TestCase;

import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.test.MsgInterceptor;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.CallbackAddress;


/**
 * PtPDestination is a helper class when testing ptp destinations
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class PtPDestination {
   private Global global;
   private MsgInterceptor updateInterceptor;
   private SessionName sessionName;
   Logger log = Logger.getLogger(PtPDestination.class.getName());
   
   public PtPDestination(Global parentGlobal, String sessionName) {
      this.global = parentGlobal.getClone(null);
      this.sessionName = new SessionName(this.global, sessionName);
      this.updateInterceptor = new MsgInterceptor(this.global, log, null);
   }
      
   public void init(boolean wantsPtP, boolean shutdownCb, long cbMaxEntries, long cbMaxEntriesCache, long subjMaxEntries, long subjMaxEntriesCache) throws XmlBlasterException {
      this.updateInterceptor.clear();
      ConnectQos qos = new ConnectQos(this.global);
      qos.setSessionName(this.sessionName);
      qos.setPtpAllowed(wantsPtP);
      qos.getSessionCbQueueProperty().setMaxEntries(cbMaxEntries);
      qos.getSessionCbQueueProperty().setMaxEntriesCache(cbMaxEntriesCache);
         
      if (subjMaxEntries > 0L || subjMaxEntriesCache > 0L) {   
         if (subjMaxEntries > 0L) qos.getSubjectQueueProperty().setMaxEntries(subjMaxEntries);
         if (subjMaxEntriesCache > 0L) qos.getSubjectQueueProperty().setMaxEntriesCache(subjMaxEntriesCache);
      }
         
      CallbackAddress cbAddress = new CallbackAddress(this.global);
      cbAddress.setRetries(-1);
      cbAddress.setPingInterval(-1);
      cbAddress.setDelay(250L);
      qos.addCallbackAddress(cbAddress);
      
      Address clientAddress = qos.getAddress();
      clientAddress.setRetries(-1);
      clientAddress.setPingInterval(-1);
      clientAddress.setDelay(10000L);
      
      this.updateInterceptor.clear();
      this.global.getXmlBlasterAccess().connect(qos, updateInterceptor);
      if (shutdownCb) {
         try {
            Thread.sleep(250L);
         }
         catch (InterruptedException ex) {
            ex.printStackTrace();
            TestCase.assertTrue("An interrupted exception occured", false);
         }
         this.global.getXmlBlasterAccess().getCbServer().shutdown();
      }
   }

   public void shutdown(boolean doDisconnect) {
      DisconnectQos qos = new DisconnectQos(this.global);
      if (doDisconnect) 
         this.global.getXmlBlasterAccess().disconnect(qos);
      this.global.shutdown();
      this.global = null;
   }
      
   public SessionName getSessionName() {
      return this.sessionName;
   }
      
   public I_XmlBlasterAccess getConnection() {
      return this.global.getXmlBlasterAccess();
   }
   
   public MsgInterceptor getUpdateInterceptor() {
      return this.updateInterceptor;
   }
   
   public void check(long timeout, int expected) {
      TestCase.assertEquals(this.getSessionName().getRelativeName(), expected, this.updateInterceptor.waitOnUpdate(timeout, expected));
      this.updateInterceptor.clear();         
   }
}

