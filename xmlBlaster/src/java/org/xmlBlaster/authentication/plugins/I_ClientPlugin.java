package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;

/**
 * A client helper.
 * Class for java clients, decrypting messages which
 * came from the corresponding security plugin. 
 * <p />
 * If for example the server security plugin crypts
 * messages with rot13, we need to decrypt it on the
 * client side with the same algorithm. This is done here.
 * <p />
 * The reason is for clients to access xmlBlaster
 * transparently from the authentication method
 * <p />
 * For every plugin type you need, you need on instance of this class.
 */
public interface I_ClientPlugin {

   //public void init(String[] param) throws XmlBlasterException;

   /**
    * @return The plugin type, e.g. "simple"
    */
   public String getType();

   /**
    * @return The plugin version, e.g. "1.0"
    */
   public String getVersion();

   /**
    * Access a helper class to generate the xml string for authentication.
    * This string is used as part of the qos on connect.
    * <p />
    * Note that with each invocation a new instance is returned
    * which you have to initialize with your credentials (e.g. your 
    * login name and password).
    */
   public I_SecurityQos getSecurityQos();
   
   public void setSessionData(String sessionData);

   // --- message handling ----------------------------------------------------

   /**
    * decrypt, check, unseal ... an incomming message. 
    * <p/>
    * Use this to import (decrypt) the xmlKey or xmlQos
    * @param str The the received message (which is probably crypted)
    * @return The original message
    * @exception XmlBlasterException Thrown i.e. if the message has been modified
    * @see #exportMessage(str)
    */
   public String importMessage(String str) throws XmlBlasterException;
   /** Use this to import (decrypt) the content */
   public byte[] importMessage(byte[] content) throws XmlBlasterException;
   /** Use this to import (decrypt) separately the xmlKey,content,qos of MessageUnit */
   public MessageUnit importMessage(MessageUnit msg) throws XmlBlasterException;

   /**
    * encrypt, sign, seal ... an outgoing message. 
    * <p/>
    * Use this to export (encrypt) the xmlKey or xmlQos
    * @param str The source message
    * @return The probably more secure string
    * @exception XmlBlasterException Thrown if the message cannot be processed
    * @see #importMessage(String)
    */
   public String exportMessage(String xmlMsg) throws XmlBlasterException;
   /** Use this to export (encrypt) the content */
   public byte[] exportMessage(byte[] xmlMsg) throws XmlBlasterException;
   /** Use this to export (encrypt) separately the xmlKey,content,qos of MessageUnit */
   public MessageUnit exportMessage(MessageUnit msg) throws XmlBlasterException;
}
