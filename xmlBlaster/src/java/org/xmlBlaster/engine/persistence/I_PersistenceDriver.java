/*------------------------------------------------------------------------------
Name:      I_PersistenceDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: I_PersistenceDriver.java,v 1.7 2000/09/03 17:59:37 kron Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.xml2java.XmlKey;
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
 * @version $Revision: 1.7 $
 * @author $Author: kron $
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

}

