/*------------------------------------------------------------------------------
Name:      DisconnectQosData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: DisconnectQosData.java,v 1.2 2003/03/24 16:13:30 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.property.PropBoolean;


/**
 * This class encapsulates the qos of a logout() or disconnect()
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.disconnect.html">The interface.disconnect requirement</a>
 * @see org.xmlBlaster.util.qos.DisconnectQosSaxFactory
 * @see org.xmlBlaster.test.classtest.DisconnectQosTest
 */
public class DisconnectQosData extends QosData implements java.io.Serializable, Cloneable
{
   private String ME = "DisconnectQosData";
   protected transient I_DisconnectQosFactory factory;
   private PropBoolean deleteSubjectQueue = new PropBoolean(true);
   private PropBoolean clearSessions = new PropBoolean(false);

   /**
    * Default constructor
    */
   public DisconnectQosData(Global glob) {
      this(glob, null, null);
   }

   /**
    * Parses the given ASCII logout QoS. 
    */
   public DisconnectQosData(Global glob, I_DisconnectQosFactory factory, String serialData) {
      super(glob, serialData);
      this.factory = (factory == null) ? this.glob.getDisconnectQosFactory() : factory;
   }

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString() {
      return toXml();
   }

   /**
    * Return true if subject queue shall be deleted with last user session
    * @return true;
    */
   public boolean deleteSubjectQueue() {
      return this.deleteSubjectQueue.getValue();
   }

   /**
    */
   public PropBoolean deleteSubjectQueueProp() {
      return this.deleteSubjectQueue;
   }

   /**
    * @param true if subject queue shall be deleted with last user session logout
    */
   public void deleteSubjectQueue(boolean del) {
      this.deleteSubjectQueue.setValue(del);
   }

   /**
    * Return true if we shall kill all other sessions of this user on logout (defaults to false). 
    * @return false
    */
   public boolean clearSessions() {
      return this.clearSessions.getValue();
   }

   /**
    */
   public PropBoolean clearSessionsProp() {
      return this.clearSessions;
   }

   /**
    * @param true if we shall kill all other sessions of this user on logout (defaults to false). 
    */
   public void clearSessions(boolean del) {
      this.clearSessions.setValue(del);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * The default is to include the security string
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml() {
      return toXml("");
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml(String extraOffset) {
      return this.factory.writeObject(this, extraOffset);
   }

   /**
    * Returns a deep clone, you can change safely all data. 
    */
   public Object clone() {
      DisconnectQosData newOne = null;
      newOne = (DisconnectQosData)super.clone();
      synchronized(this) {
         newOne.deleteSubjectQueue = (PropBoolean)this.deleteSubjectQueue.clone();
         newOne.clearSessions = (PropBoolean)this.clearSessions.clone();
      }
      return newOne;
   }

   /** For testing: java org.xmlBlaster.util.qos.DisconnectQosData */
   /*
   public static void main(String[] args) {
      try {
         Global glob = new Global(args);
         DisconnectQosData qos = new DisconnectQosData(glob);
         qos.clearSessions(true);
         qos.deleteSubjectQueue(false);
         System.out.println(qos.toXml());
      }
      catch(Throwable e) {
         System.err.println("TestFailed : " + e.toString());
      }
   }
   */
}
