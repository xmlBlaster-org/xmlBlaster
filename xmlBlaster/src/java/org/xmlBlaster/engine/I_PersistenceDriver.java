/*------------------------------------------------------------------------------
Name:      I_PersistenceDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: I_PersistenceDriver.java,v 1.1 2000/06/25 18:26:47 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.xml2java.XmlKey;


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
 * @version $Revision: 1.1 $
 * @author $Author: ruff $
 */
public interface I_PersistenceDriver
{
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
    */
   public void store(XmlKey xmlKey, byte[] content) throws XmlBlasterException;


   /**
    * Gets all messages from the store.
    * <p />
    * The protocol for recovery is implemented in the derived class
    * @param clientInfo    Needed to publish
    * @param requestBroker Needed to publish
    */
   public void recover(ClientInfo clientInfo, RequestBroker requestBroker) throws XmlBlasterException;


   /**
    * Allows a message to be fetched from the store.
    * <p />
    * The protocol for recovery is implemented in the derived class
    * @param oid           The message name (key oid="...")
    * @param clientInfo    Needed to publish
    * @param requestBroker Needed to publish
    */
   public void recover(String oid, ClientInfo clientInfo, RequestBroker requestBroker) throws XmlBlasterException;


   /**
    * Allows a stored message to be deleted.
    * <p />
    * The protocol for storing is implemented in the derived class
    * @param xmlKey  To identify the message
    */
   public void erase(XmlKey xmlKey) throws XmlBlasterException;
}

