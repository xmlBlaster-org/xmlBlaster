/*------------------------------------------------------------------------------
Name:      MuDbDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a very simple, file based, persistence manager
Version:   $Id: MuDbDriver.java,v 1.2 2000/12/26 14:56:40 ruff Exp $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence.mudb;

import org.jutils.io.FileUtil;
import org.jutils.JUtilsException;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;

import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.xml2java.PublishQoS;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.RequestBroker;

import org.xmlBlaster.engine.persistence.PMessageUnit;
import org.xmlBlaster.engine.persistence.I_PersistenceDriver;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Enumeration;
import java.util.Vector;


/**
 *
 */
public class MuDbDriver implements I_PersistenceDriver
{
   private static final String ME = "MuDbDriver";
   private static MuDb persistence;

   /**
    * Constructs the FileDriver object (reflection constructor).
    */
   public MuDbDriver() throws XmlBlasterException
   {
      MuDb persistence = new MuDb();
   }


   /**
    * Allows a message to be stored.
    * <p />
    * It only stores the xmlKey, content and qos.
    * The other store() method is called for following messages, to store only message-content.
    * @param messageWrapper The container with all necessary message info.
    */
   public final void store(MessageUnitWrapper messageWrapper) throws XmlBlasterException
   {
      /* Get the MessageUnit */
      MessageUnit mu = messageWrapper.getMessageUnit();
      XmlKey key = messageWrapper.getXmlKey();
      PublishQoS qos = messageWrapper.getPublishQoS();

      PMessageUnit pmu = new PMessageUnit(mu, qos.isDurable(), key.getKeyOid());

      /* Write MessageUnit to MuDb */
      if(persistence.insert(pmu) == true) {
         if (Log.TRACE) Log.trace(ME, "Successfully stored " + pmu.oid);
      }else{
         if (Log.TRACE) Log.trace(ME, "MessageUnit exists! " + pmu.oid);
      }
   }

   /* content of existing messages changed */
   public final void update(MessageUnitWrapper messageWrapper) throws XmlBlasterException
   {
      store(messageWrapper);
   }



   /**
    * Allows to fetch one message by oid from the persistence.
    * <p />
    * @param   oid   The message oid (key oid="...")
    * @return the MessageUnit, which is persistent.
    */
   public MessageUnit fetch(String oid) throws XmlBlasterException
   {
      PMessageUnit pmu = persistence.get(oid);

      if (Log.TRACE) Log.trace(ME, "Successfully fetched message " + pmu.oid);
      return pmu.msgUnit;
   }


   /**
    * Fetches all oid's of the messages from the persistence.
    * <p />
    * It is a helper method to invoke 'fetch(String oid)'.
    * @return a Enumeration of oids of all persistent MessageUnits. The oid is a String-Type.
    */
    public Enumeration fetchAllOids() throws XmlBlasterException
    {
       Vector oidContainer = new Vector();
       Log.error(ME,"NOt implemented : fetchAllOids()");
       return oidContainer.elements();
    }



   /**
    * Allows a stored message to be deleted.
    * <p />
    * @param xmlKey  To identify the message
    */
   public void erase(XmlKey xmlKey) throws XmlBlasterException
   {
      persistence.delete(xmlKey.getKeyOid());
   }


   /** Invoke:  jaco org.xmlBlaster.engine.persistence.FileDriver */
   public static void main(String args[])
   {
      try {
         MuDbDriver driver = new MuDbDriver();
      } catch (Exception e) {
         Log.error(ME, e.toString());
         e.printStackTrace();
      }
      Log.exit(MuDbDriver.ME, "No test implemented - testsuite/org/xmlBlaster/TestMuDb.java");
   }
}

