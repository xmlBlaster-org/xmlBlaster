/*------------------------------------------------------------------------------
Name:      I_PersistenceDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: I_PersistenceDriver.java,v 1.10 2002/02/08 00:48:15 goetzger Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.I_Plugin;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.xml2java.PublishQoS;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.engine.helper.MessageUnit;

import java.util.Enumeration;

/**
 * This interface hides the real driver to manage the persistence of messages.
 * <p />
 * Interface for xmlBlaster to access a persistent store.
 * <br />
 * Implement this interface if you want your own persistence handling
 * and add your driver to the $HOME/xmlBlaster.properties configuration file.
 * <br />
 * FileDriver.java is a very simple reference implementation,
 * storing the messages to files.
 * <br />
 * TODO: Extend interface to support caching!<br />
 *
 * @version $Revision: 1.10 $
 * @author $Author: goetzger $
 */
public interface I_PersistenceDriver extends I_Plugin
{
     /**
    * initialises an instance of the persistence plugin
    * <p />
    * The protocol for storing is implemented in the derived class
    * @param param  aditional parameter for the persistence plugin
    */
//   public void init(String param) throws XmlBlasterException;


     /**
    * Closes the instance of the persistence plugin
    * <p />
    * The protocol for storing is implemented in the derived class
    */
   public void shutdown() throws XmlBlasterException;


   /**
    * Allows a message to be stored.
    * <p />
    * It only stores the xmlKey, content and qos.
    * The other store() method is called for following messages, to store only message-content.
    * <p />
    * The protocol for storing is implemented in the derived class
    * @param messageWrapper The container with all necessary message info.
    */
   public void store(MessageUnitWrapper messageWrapper) throws XmlBlasterException;


   /**
    * Allows a stored message content to be updated.
    * <p />
    * It only stores the content, so the other store() method needs to be called first if this message is new.
    * <p />
    * The protocol for storing is implemented in the derived class
    * @param xmlKey  To identify the message
    * @param content The data to store
    * @param qos The quality of service, may contain another publisher name
    */
   public void update(MessageUnitWrapper messageWrapper) throws XmlBlasterException;

   /**
    * Fetches one message by oid from the persistence.
    * <p />
    * This method needs the RequestBroker for recovery.
    * @param   oid   The messages name (key oid="...")
    * @return the MessageUnit, which is persistent
    */
   public MessageUnit fetch(String oid) throws XmlBlasterException;

   /**
    * Fetches all oid's of the messages from the persistence.
    * <p />
    * It is a helper method to invoke 'fetch(String oid)'.
    * @return a Enumeration of oids of all persistent MessageUnits. The oid is a String-Type.
    */
    public Enumeration fetchAllOids() throws XmlBlasterException;


     /**
    * Allows a stored message to be deleted.
    * <p />
    * The protocol for storing is implemented in the derived class
    * @param xmlKey  To identify the message
    */
   public void erase(XmlKey xmlKey) throws XmlBlasterException;


     /**
    * gives the name of the driver
    * <p />
    * @return the name of the driver
    */
   public String getName();

}

