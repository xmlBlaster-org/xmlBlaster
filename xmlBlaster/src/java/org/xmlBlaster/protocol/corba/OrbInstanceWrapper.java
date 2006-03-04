/*------------------------------------------------------------------------------
Name:      OrbInstanceWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   OrbInstanceWrapper class to invoke the xmlBlaster server using CORBA.
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.corba;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.Constants;
import java.util.Properties;

/**
 * OrbInstanceWrapper wraps an org.omg.CORBA.ORB instance/singleton in Global scope. 
 * The first call to getOrb() creates an ORB and following calls increment a reference counter.
 * Calls to shutdown() reduce the reference counter. If the counter reaches 0 the
 * orb is destroyed. 
 */
public class OrbInstanceWrapper
{
   private String ME = "OrbInstanceWrapper";
   private Global glob;
   private org.omg.CORBA.ORB orb;
   private int referenceCounter;

   public OrbInstanceWrapper(Global glob) {
      this.glob = glob;
   }

   /**
    * On first call an orb is created, further calls return the same orb instance. 
    * @param glob
    * @param args command line args, see org.omg.CORBA.ORB.init(), use glob.getProperty().getProperties()
    * @param props application-specific properties; may be <code>null</code>, see org.omg.CORBA.ORB.init(String[], Properties)
    * @param address The configuration of the address
    * @return Access to a new created orb handle
    * @see org.omg.CORBA.ORB#init(String[], Properties)
    */
   public synchronized org.omg.CORBA.ORB getOrb(String[] args, Properties props, AddressBase address) {
      if (this.orb == null) {
         this.orb = OrbInstanceFactory.createOrbInstance(glob, args, (Properties)null, address);
      }
      this.referenceCounter++;
      return this.orb;
   }

   /**
    * When the same amount releasOrb() is called as getOrb(), the internal orb is shutdown. 
    */
   public synchronized void releaseOrb(boolean wait_for_completion) {
      //System.out.println("DEBUG ONLY: Current referenceCounter=" + this.referenceCounter);
      this.referenceCounter--;
      if (this.referenceCounter <= 0) {
         this.referenceCounter = 0;
         if (this.orb != null) {
            try {
               this.orb.shutdown(wait_for_completion);
               this.orb = null;
               //System.out.println("DEBUG ONLY: Destroyed ORB");
               return;
            }
            catch (Throwable ex) {
               System.err.println(ME+".releaseOrb: Exception occured during orb.shutdown("+wait_for_completion+"): " + ex.toString());
            }
         }
      }
   }
}
