/*------------------------------------------------------------------------------
Name:      I_Callback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.http.common;

import java.util.Hashtable;

/**
 * Here you receive callbacks from xmlBlaster in your applet. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public interface I_CallbackRaw
{
   /**
    * This is the callback method invoked from I_XmlBlasterRaw
    * informing the client in an asynchronous mode about a new message.
    * <p />
    * So you should implement in your applet the I_CallbackRaw interface -
    * suppling the update() method where you can do with the message whatever you want.
    *
    * @param cbSessionId The session ID specified by the client which registered the callback
    * @param updateKey   The arrived key in a flattened JXPath representation. 
    * <pre>
    *   /key/@oid                 -> Rugby
    *   /key/@contentMime         -> text/plain
    *   /key/@contentMimeExtended -> Version-1.0
    *   /key/child::node()        -> &lt;myTeam>Munich&lt;numPlayers>6&lt;/numPlayers>&lt;/myTeam>
    *   ...
    * </pre>
    * @param content The binary message pay load
    * @param updateQos The arrived key in a flattened JXPath representation. 
    * <pre>
    *   /qos/rcvTimestamp/@nanos                  -> 1042815836675000001
    *   /qos/methodName/text()                    -> update
    *   /qos/clientProperty[@name='myAge']/text() -> 12
    *   /qos/state/@id                            -> OK
    *   ...
    * </pre>
    * @see <a href="http://jakarta.apache.org/commons/jxpath/">Apache JXPath</a>
    * @see org.xmlBlaster.util.key.MsgKeyData#toJXPath
    * @see org.xmlBlaster.util.qos.MsgQosData#toJXPath
    */
   public String update(String cbSessionId, Hashtable updateKey, byte[] content, Hashtable updateQos) throws Exception;
}

