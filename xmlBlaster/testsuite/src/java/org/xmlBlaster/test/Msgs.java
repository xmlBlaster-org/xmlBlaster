/*------------------------------------------------------------------------------
Name:      Msgs.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test;

import org.xmlBlaster.test.Msg;
import org.xmlBlaster.util.XmlBlasterException;
import java.util.Vector;

/**
 * Helper for testsuite to collect received messages with update()
 */
public class Msgs
{
   private Vector updateVec = new Vector();
   
   public void add(Msg msg) { updateVec.addElement(msg); }
   
   public void remove(Msg msg) { updateVec.removeElement(msg); }
   
   public void clear() { updateVec.clear(); }

   /**
    * Access the updated message filtered by the given oid and state. 
    * @param oid if null the oid is not checked
    * @param state if null the state is not checked
    */
   public Msg[] getMsgs(String oid, String state) {
      Vector ret = new Vector();
      for (int i=0; i<updateVec.size(); i++) {
         Msg msg = (Msg)updateVec.elementAt(i);
         if (
             (oid == null || oid.equals(msg.getOid())) &&
             (state == null || state.equals(msg.getState()))
            )
            ret.addElement(msg);
      }
      return (Msg[])ret.toArray(new Msg[ret.size()]);
   }

   public Msg[] getMsgs() {
      return getMsgs(null, null);
   }

   /**
    * Access the updated message filtered by the given oid and state. 
    * @return null or the message
    * @exception If more than one message is available
    */
   public Msg getMsg(String oid, String state) throws XmlBlasterException {
      Msg[] msgs = getMsgs(oid, state);
      if (msgs.length > 1)
         throw new XmlBlasterException("Msgs", "update(oid=" + oid + ", state=" + state + ") " + msgs.length + " arrived instead of zero or one");
      if (msgs.length == 0)
         return null;
      return msgs[0];
   }

   public int count() {
      return updateVec.size();
   }
}
