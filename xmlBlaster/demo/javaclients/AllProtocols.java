// xmlBlaster/demo/javaclients/AllProtocols.java
package javaclients;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.GetReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;


/**
 * This client connects to xmlBlaster and invokes all available methods with all available protocols. 
 * <p />
 * Invoke: java javaclients.AllProtocols
 * <p />
 * Invoke: java javaclients.AllProtocols -session.name joe -passwd secret
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class AllProtocols implements I_Callback
{
   private String ME = "";
   private final Global glob;
   private static Logger log = Logger.getLogger(AllProtocols.class.getName());
   private final String[] argsIOR = {
      "-protocol",
      "IOR",
   };
   private final String[] argsSocket = {
      "-protocol",
      "SOCKET",
   };
   private final String[] argsXmlRpc = {
      "-protocol",
      "XMLRPC",
   };
   private final String[] argsRmi = {
      "-protocol",
      "RMI",
   };
   private final Con[] conList = {
      new Con(argsIOR, "IOR connection"),
      new Con(argsSocket, "SOCKET connection"),
      new Con(argsXmlRpc, "XMLRPC connection"),
      new Con(argsRmi, "RMI connection")
   };

   public AllProtocols(Global glob) {
      this.glob = glob;

      for(int i=0; i<conList.length; i++) {
         ME = conList[i].helpText;
         conList[i].con = conList[i].glob.getXmlBlasterAccess();
         I_XmlBlasterAccess con = conList[i].con;
         try {

            // Check if other login name or password was given on command line:
            // (This is redundant as it is done by ConnectQos already)
            String name = con.getGlobal().getProperty().get("session.name", "AllProtocols");
            String passwd = con.getGlobal().getProperty().get("passwd", "secret");

            ConnectQos qos = new ConnectQos(con.getGlobal(), name, passwd);
            con.connect(qos, this);  // Login to xmlBlaster, register for updates


            PublishKey pk = new PublishKey(con.getGlobal(), "AllProtocols", "text/xml", "1.0");
            pk.setClientTags("<org.xmlBlaster><demo/></org.xmlBlaster>");
            PublishQos pq = new PublishQos(con.getGlobal());
            MsgUnit msgUnit = new MsgUnit(pk, "Hi", pq);
            con.publish(msgUnit);


            GetKey gk = new GetKey(con.getGlobal(), "AllProtocols");
            GetQos gq = new GetQos(con.getGlobal());
            MsgUnit[] msgs = con.get(gk.toXml(), gq.toXml());
            GetReturnQos grq = new GetReturnQos(con.getGlobal(), msgs[0].getQos());

            log.info("Accessed xmlBlaster message with content '" + new String(msgs[0].getContent()) +
                         "' and status=" + grq.getState());


            SubscribeKey sk = new SubscribeKey(con.getGlobal(), "AllProtocols");
            SubscribeQos sq = new SubscribeQos(con.getGlobal());
            SubscribeReturnQos subRet = con.subscribe(sk.toXml(), sq.toXml());


            msgUnit = new MsgUnit(pk, "Ho".getBytes(), pq);
            PublishReturnQos prq = con.publish(msgUnit);

            log.info("Got status='" + prq.getState() + "' for published message '" + prq.getKeyOid());

            try { Thread.sleep(1000); } 
            catch( InterruptedException ie) {} // wait a second to receive update()


            UnSubscribeKey uk = new UnSubscribeKey(con.getGlobal(), subRet.getSubscriptionId());
            UnSubscribeQos uq = new UnSubscribeQos(con.getGlobal());
            UnSubscribeReturnQos[] urq = con.unSubscribe(uk.toXml(), uq.toXml());

            EraseKey ek = new EraseKey(con.getGlobal(), "AllProtocols");
            EraseQos eq = new EraseQos(con.getGlobal());
            EraseReturnQos[] eraseArr = con.erase(ek.toXml(), eq.toXml());

            DisconnectQos dq = new DisconnectQos(con.getGlobal());
            con.disconnect(dq);
         }
         catch (XmlBlasterException e) {
            log.severe(e.getMessage());
         }
         catch (Throwable e) {
            e.printStackTrace();
            log.severe(e.toString());
         }
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                        UpdateQos updateQos)
   {
      if (updateKey.isInternal()) {
         log.info("Received unexpected internal message '" +
              updateKey.getOid() + " from xmlBlaster");
         return "";
      }

      log.info("Received asynchronous message '" + updateKey.getOid() +
                   "' state=" + updateQos.getState() +
                   " content=" + new String(content) + " from xmlBlaster");

      UpdateReturnQos uq = new UpdateReturnQos(updateKey.getGlobal());
      return uq.toXml();
   }

   /**
    * Try
    * <pre>
    *   java javaclients.AllProtocols -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.err.println("Example: java javaclients.AllProtocols -session.name Jeff\n");
         System.exit(1);
      }

      new AllProtocols(glob);
   }

class Con {
   public Con(String[] args, String helpText) {
      this.glob = new Global(args, true, false);
      this.helpText = helpText;
   }
   public String helpText;
   public Global glob;
   public I_XmlBlasterAccess con;
};
}

