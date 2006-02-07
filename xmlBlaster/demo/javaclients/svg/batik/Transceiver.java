/*------------------------------------------------------------------------------
Name:      Transceiver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a svg client using batik
Version:   $Id$
------------------------------------------------------------------------------*/
package javaclients.svg.batik;

import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.bridge.BridgeContext;
import java.awt.Point;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlToDom;
import org.xmlBlaster.util.XmlNotPortable;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileInputStream;

import org.apache.batik.dom.svg.SVGDocumentFactory;

import java.util.StringTokenizer;

/**
 * @author $Author$ (laghi@swissinfo.org)
 */

public class Transceiver implements I_Callback
{

   private static final String ME = "Transceiver";
   private final Global glob;
   private final LogChannel log;
   private BridgeContext        bridgeContext        = null;
   private JSVGCanvasExtended   canvas               = null;
   private I_XmlBlasterAccess xmlBlasterConnection = null;
   private String               svgFileName          = null;

   private final static String SVG_PREFIX  = "<?xml version='1.0' standalone='no'?>\n<!DOCTYPE svg PUBLIC '-//W3C//DTD SVG 1.0//EN'\n'http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd'><svg>";
   private final static String SVG_POSTFIX = "</svg>";

   /**
    * ensures that elements are subscribed only one time
    */
   private boolean elementsSubscribed = false;


   protected static byte[] readFromFile (String filename)
      throws FileNotFoundException, IOException
   {
      FileInputStream fileInputStream = new FileInputStream(filename);
      int fileSize = fileInputStream.available();
      byte[] b = new byte[fileSize];
      fileInputStream.read(b);
      return b;
   }


   public void subscribeElements () throws XmlBlasterException
   {
      if ((this.svgFileName != null) && (this.elementsSubscribed == false)) {
         String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='' queryType='XPATH'>\n" +
                      "   //svg[@name='" + this.svgFileName + "' and @complete='false']" +
                      "</key>";
         String qos = "<qos></qos>";
         this.xmlBlasterConnection.subscribe(xmlKey, qos);
         this.elementsSubscribed = true;
      }
   }


   /**
    * In the argument list you should have either
    * -svgMaster filename
    * -svgSlave filename
    */
   public Transceiver (Global glob, JSVGCanvasExtended canvas)
   {
      this.glob = glob;
      this.log = glob.getLog("batik");
      log.trace(ME, "constructor with Global");
      this.canvas = canvas;
      try {
         String svgMaster = glob.getProperty().get("svgMaster", (String)null);
         String svgSlave  = glob.getProperty().get("svgSlave", (String)null);
         String svgUser   = glob.getProperty().get("svgUser", "dummyUser");

         this.xmlBlasterConnection = glob.getXmlBlasterAccess();
         ConnectQos connectQos = new ConnectQos(glob, svgUser, "secret");
         this.xmlBlasterConnection.connect(connectQos, this);


         if (svgMaster != null) {
            this.svgFileName = svgMaster;
            log.trace(ME, "constructor: running as Master: " + svgMaster);
            // read the file, create a publishKey and then publish the thing
            // then subscribe only to complete documents (not single elements)
            // this because the elements can olny be updated once the document
            // has been loaded.
            String xmlKey = "<key oid=''><svg name='" + this.svgFileName + "' complete='true'></svg></key>";
            String qos = "<qos></qos>";
            // retrieve the file content
            byte[] content = this.readFromFile(this.svgFileName);
            MsgUnit messageUnit = new MsgUnit(xmlKey, content, qos);
            PublishReturnQos ret = this.xmlBlasterConnection.publish(messageUnit);
            log.info(ME, "constructor: " + ret.getKeyOid());
         }
         else if (svgSlave != null) {
            this.svgFileName = svgSlave;
         }

         if (this.svgFileName != null) {
            String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='' queryType='XPATH'>\n" +
                      "   //svg[@name='" + this.svgFileName + "' and @complete='true']" +
                      "</key>";
            String qos = "<qos></qos>";
            this.xmlBlasterConnection.subscribe(xmlKey, qos);
         }
      }
      // don't know if this is the correct place where to catch this exception
      catch (XmlBlasterException ex) {
         log.error(ME, "Transceiver Constructor: " + ex.toString());
      }
      catch (FileNotFoundException ex) {
         log.error(ME, "Transceiver Constructor: " + ex.toString());
      }
      catch (IOException ex) {
         log.error(ME, "Transceiver Constructor: " + ex.toString());
      }
   }


   /**
    * Disconnects from the xmlBlaster Server
    */
   public void disconnect ()
   {
      this.xmlBlasterConnection.disconnect(new DisconnectQos(glob));
   }


   /**
    * This method is invoked when a whole document is sent (this should be the
    * first update this object should receive).
    */
   protected void updateDocument (byte[] content) throws XmlBlasterException
   {
      log.warn(ME, ".updateDocument()");
      try {
         this.canvas.loadDocumentFromByteArray(content);
      }
      catch (IOException ex)
      {
         log.error(ME, "io exception: " + ex.toString());
         throw new XmlBlasterException(ME, ".updateDocument: " + ex.getMessage());
      }
   }


   protected void updateElement (byte[] content, String id) throws XmlBlasterException
   {
      log.trace(ME, ".updateElement(): " + id);

      Element el = this.canvas.getElement(id);
      if (el == null) {
         log.warn(ME, "the element with id '" + id + "' is not registered");
         return;
      }

      this.bridgeContext.unbind(el);
      String completeSVG = SVG_PREFIX + new String(content) + SVG_POSTFIX;
      log.warn(ME, ".updateElement: " + completeSVG);

      try {
         Node tempNode = SvgUtility.createDocument(completeSVG, "file://dummy.svg").getDocumentElement().getFirstChild();

         Document doc = el.getOwnerDocument();
         tempNode = doc.importNode(tempNode, true);

         Node parentNode = el.getParentNode();
         parentNode.replaceChild(tempNode, el);

         GraphicsNode graphicsNode = this.bridgeContext.getGVTBuilder().build(this.bridgeContext, (Element)tempNode);
         this.bridgeContext.bind((Element)tempNode, graphicsNode);

         graphicsNode = this.bridgeContext.getGVTBuilder().build(this.bridgeContext, tempNode.getOwnerDocument());
         this.canvas.setGraphicsNode(graphicsNode);
         this.canvas.updateDocument();
      }
      catch (IOException ex) {
         log.error(ME, ".updateElement: IOException " + ex.getMessage());
         throw new XmlBlasterException(ME, ".updateElement: IOException " + ex.getMessage());
      }
   }



   public String update(java.lang.String loginName, UpdateKey updateKey,
                   byte[] content, UpdateQos updateQos) throws XmlBlasterException
   {
      log.trace(ME, "update called, content: " + new String(content));
      log.trace(ME, "update called, loginName: " + loginName);
      log.trace(ME, "update called, updateKey: " + updateKey);
      log.trace(ME, "update called, updateQos: " + updateQos);

      NodeList nodes = XmlToDom.parseToDomTree(glob, updateKey.toString()).getDocumentElement().getElementsByTagName("svg");
      int length = nodes.getLength();
      if ((nodes == null) || (length < 1)) {
         throw new XmlBlasterException(ME, ".update: no svg node found in the key");
      }

      Node keyNode = nodes.item(0);
      if (keyNode instanceof Element) {
         // if an id has been defined, then it is a single element otherwise the whole document
         String id = ((Element)keyNode).getAttribute("id");
         if ((id == null) || (id.length() < 1)) {
            updateDocument(content);
         }
         else updateElement(content, id);
      }
      else throw new XmlBlasterException(ME, "update: svg node is not an element");
      return "";
   }



   public void setBridgeContext (BridgeContext bridgeContext)
   {
      this.bridgeContext = bridgeContext;
   }


   /**
    * finds if the given element is in the idTable (i.e. if it does not
    * fullfill the 'dynamic' requirements). If not, its parent is searched and
    * so on. If the element nor one of its ancestors is dynamic, it returns
    * null, otherwise it returns the first dynamic element found.
    *
    * Currently the requirements for dynamic is that the element has an id
    * which has a prefix 'xmlBlaster:'
    */
   protected Element getClickedDynamicElement (Element el)
   {
      log.trace(ME, ".getClickedDynamicElement");
      while (el != null) {
         String id = el.getAttribute("id");
         if (SvgIdMapper.isDynamic(id)) return el;
         Node parent = el.getParentNode();
         if (parent instanceof Element) el = (Element)parent;
         else return null;
      }
      return null;
   }


   public static void moveElement (Element el, int x, int y)
   {
      //log.call(ME, ".moveElement");
      if (el == null) return;
      String transformString = el.getAttribute("transform");
      if ((transformString == null) || (transformString.length() < 1)) {
         transformString = "translate(" + x + "," + y + ")";
      }
      else {
         el.removeAttribute("transform");
         // check if 'translate' is defined
         int pos = transformString.indexOf("translate(");
         if (pos < 0) transformString += " translate(" + x + "," + y + ")";
         else { // get the position string
            String endString   = transformString.substring(pos + "translate(".length()).trim();
            String startString = transformString.substring(0, pos).trim();
            String coreString  = endString.substring(0, endString.indexOf(")")).trim();
            endString          = endString.substring(1 + endString.indexOf(")")).trim();
            StringTokenizer tokenizer = new StringTokenizer(coreString,",");

            double x0 = Double.parseDouble(tokenizer.nextToken().trim());
            double y0 = Double.parseDouble(tokenizer.nextToken().trim());

            transformString = startString + " translate(" + (x0 + x) + "," + (y0 + y) + ") " + endString;
         }
      }
      el.setAttribute("transform", transformString);
   }

   public void move (GraphicsNode graphicsNode, Point displacement)
   {
      log.info(ME, ".move " + displacement.x + " " + displacement.y);

      Element el = this.bridgeContext.getElement(graphicsNode);
      el = getClickedDynamicElement(el);
      if (el == null) return;

      String id = el.getAttribute("id");

      // later make the necessary transform ....
      this.moveElement(el, displacement.x, displacement.y);

   /*
      double x = graphicsNode.getBounds().getMinX() + displacement.x;
      double y = graphicsNode.getBounds().getMinY() + displacement.y;

      el.setAttributeNS(null, "x", Double.toString(x));
      el.setAttributeNS(null, "y", Double.toString(y));
*/
      log.info(ME,".move: el: " + el.getNodeName());


      // publish the node
      String xmlKey = "<key oid=''><svg name='" + this.svgFileName + "' id='" + id + "' complete='false'></svg></key>";
      String qos = "<qos></qos>";
      // retrieve the file content
      byte[] content = XmlUtility.write(el).getBytes();

      try {
         MsgUnit messageUnit = new MsgUnit(xmlKey, content, qos);
         PublishReturnQos ret = this.xmlBlasterConnection.publish(messageUnit);
         log.trace(ME, "move: " + ret.getKeyOid());
      }
      catch (XmlBlasterException ex) {
         log.error(ME, ".move exception when publishing: " + ex.getMessage());
      }
   }

}
