/*------------------------------------------------------------------------------
Name:      Main.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main class to invoke the xmlBlaster server
Version:   $Id: Main.java,v 1.24 2000/02/24 22:00:16 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.xmlBlaster.util.*;
import org.xmlBlaster.engine.*;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.authenticateIdl.AuthServerPOATie;
import org.xmlBlaster.protocol.corba.AuthServerImpl;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.HttpIORServer;
import java.io.*;
import org.omg.CosNaming.*;


/**
 * Main class to invoke the xmlBlaster server.
 * <p />
 * Start parameters supported
 * <p />
 * <ul>
 *    <li><code>-iorFile 'file name'   </code>default is no dumping of IOR<br />
 *        Specify a file where to dump the IOR of the AuthServer (for client access)
 *    </li>
 *    <li><code>-iorPort 'port number'   </code>default is port 7609<br />
 *        Specify a port number where the builtin http server publishes its AuthServer IOR<br />
 *        the port -1 switches this feature off
 *    </li>
 * </ul>
 * <p />
 * Examples how to start the xmlBlaster server:
 * <p />
 * <code>   ${JacORB_HOME}/bin/jaco org.xmlBlaster.Main -iorPort 8080</code>
 * <p />
 * <code>   ${JacORB_HOME}/bin/jaco org.xmlBlaster.Main -iorFile /tmp/NS_Ref</code>
 * <p />
 * <code>   jaco org.xmlBlaster.Main +trace +dump +calls +time</code>
 */
public class Main
{
   final private String ME = "Main";
   private org.omg.CORBA.ORB orb;
   private HttpIORServer httpIORServer = null;  // xmlBlaster publishes his AuthServer IOR

   /**
    * true: If instance created by control panel<br />
    * false: running without GUI
    */
   static MainGUI controlPanel = null;


   /**
    * Start xmlBlaster.
    * @param args The command line parameters
    */
   public Main(String[] args)
   {
      if (Args.getArg(args, "-?") == true || Args.getArg(args, "-h") == true) {
         usage();
         return;
      }
      Log.setLogLevel(args);

      orb = org.omg.CORBA.ORB.init(args, null);
      try {
         org.omg.PortableServer.POA rootPOA =
             org.omg.PortableServer.POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
         rootPOA.the_POAManager().activate();

         AuthServerImpl authServer = new AuthServerImpl(orb);

         // USING TIE:
         org.omg.PortableServer.Servant authServant = new AuthServerPOATie(authServer);
         org.omg.CORBA.Object authRef = new AuthServerPOATie(new AuthServerImpl(orb))._this(orb);
         // NOT TIE:
         // org.omg.PortableServer.Servant authServant = new AuthServerImpl(orb);
         // org.omg.CORBA.Object authRef = rootPOA.servant_to_reference(authServant);


         // There are three variants how xmlBlaster publishes its AuthServer IOR (object reference)

         // 1) Write IOR to given file
         String iorFile = Args.getArg(args, "-iorFile", (String)null);
         if(iorFile != null) {
            PrintWriter ps = new PrintWriter(new FileOutputStream(new File(iorFile)));
            ps.println(orb.object_to_string(authRef));
            ps.close();
            Log.info(ME, "Published AuthServer IOR to file " + iorFile);
         }

         // 2) Publish IOR on given port (switch off this feature with '-iorPort -1'
         int iorPort = Args.getArg(args, "-iorPort", 7609); // default xmlBlaster IOR publishing port is 7609 (HTTP_PORT)
         if (iorPort > 0) {
            HttpIORServer httpIORServer = new HttpIORServer(iorPort, orb.object_to_string(authRef));
            Log.info(ME, "Published AuthServer IOR on port " + iorPort);
         }

         // 3) Publish IOR to a naming service
         boolean useNameService = Args.getArg(args, "-ns", true);  // default is to publish myself to the naming service
         if (useNameService) {
            try {
               NamingContext nc = getNamingService();
               NameComponent [] name = new NameComponent[1];
               name[0] = new NameComponent(); // name[0] = new NameComponent("AuthenticationService", "service");
               name[0].id = "xmlBlaster-Authenticate";
               name[0].kind = "MOM";

               nc.bind(name, authRef);
               Log.info(ME, "Published AuthServer IOR to naming service");
            }
            catch (XmlBlasterException e) {
               Log.info(ME, "AuthServer IOR is not published to naming service");
            } catch (org.omg.CORBA.COMM_FAILURE e) {
               if (iorPort > 0) {
                  Log.info(ME, "Can't publish AuthServer to naming service, is your naming service really running?\n" +
                               e.toString() +
                               "\nYou don't need the naming service, i'll switch to builtin http IOR download");
               }
               else if (iorFile != null) {
                  Log.info(ME, "Can't publish AuthServer to naming service, is your naming service really running?\n" +
                               e.toString() +
                               "\nYou don't need the naming service, i'll switch to iorFile = " + iorFile);
               }
               else {
                  usage();
                  Log.panic(ME, "Can't publish AuthServer to naming service, is your naming service really running?\n" +
                               e.toString() +
                               "\n\nYou switched off the internal http server and you didn't specify a file name for IOR dump! Sorry - good bye.");
               }
            }
         }

         Log.info(ME, Memory.getStatistic());

         if (controlPanel == null) {
            Log.info(ME, "#####################################");
            Log.info(ME, "# xmlBlaster is ready for requests  #");
            Log.info(ME, "# press <?> and <enter> for options #");
            Log.info(ME, "#####################################");
         }
         else
            Log.info(ME, "xmlBlaster is ready for requests");
      } catch (Exception e) {
         e.printStackTrace();
         Log.panic(ME, e.toString());
      }

      checkForKeyboardInput();
      // orb.run();
   }


   /**
    * Check for keyboard entries from console.
    * <p />
    * Supported input is:
    * &lt;ul>
    *    &lt;li>'g' to pop up the control panel GUI&lt;/li>
    *    &lt;li>'d' to dump the internal state of xmlBlaster&lt;/li>
    *    &lt;li>'q' to quit xmlBlaster&lt;/li>
    * &lt;/ul>
    * <p />
    * NOTE: This method never returns, only on exit for 'q'
    */
   private void checkForKeyboardInput()
   {
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      while (true) {
         try {
            String line = in.readLine().trim();
            if (line.toLowerCase().equals("g")) {
               if (controlPanel == null) {
                  Log.info(ME, "Invoking control panel GUI ...");
                  controlPanel = new MainGUI(); // the constructor sets the variable controlPanel
                  controlPanel.run();
               }
               else
                  controlPanel.showWindow();
            }
            else if (line.toLowerCase().startsWith("d")) {
               try {
                  String fileName = null;
                  if (line.length() > 1) fileName = line.substring(1).trim();

                  Authenticate auth = Authenticate.getInstance();

                  if (fileName == null) {
                     Log.plain(ME, auth.printOn().toString());
                     Log.plain(ME, RequestBroker.getInstance(auth).printOn().toString());
                     Log.info(ME, "Dump done");
                  }
                  else {
                     FileUtil.writeFile(fileName, auth.printOn().toString());
                     FileUtil.appendToFile(fileName, RequestBroker.getInstance(auth).printOn().toString());
                     Log.info(ME, "Dumped internal state to '" + fileName + "'");
                  }
               }
               catch(XmlBlasterException e) {
                  Log.error(ME, "Sorry, dump failed: " + e.reason);
               }
            }
            else if (line.toLowerCase().equals("q")) {
               Log.exit(ME, "Good bye");
            }
            else // if (keyChar == '?' || Character.isLetter(keyChar) || Character.isDigit(keyChar))
               keyboardUsage();
         }
         catch (IOException e) {
            Log.warning(ME, e.toString());
         }
      }
   }


   /**
    * Locate the CORBA Naming Service.
    * <p />
    * The found naming service is cached, for better performance in subsequent calls
    * @return NamingContext, reference on name service<br />
    *         Note that this reference may be invalid, because the naming service is not running any more
    * @exception XmlBlasterException
    *                    CORBA error handling if no naming service is found
    */
   private NamingContext getNamingService() throws XmlBlasterException
   {
      NamingContext nameService = null;

      if (Log.CALLS) Log.calls(ME, "getNamingService() ...");
      if (nameService != null)
         return nameService;

      try {
         // Get a reference to the Name Service, CORBA compliant:
         org.omg.CORBA.Object nameServiceObj = orb.resolve_initial_references("NameService");
         if (nameServiceObj == null) {
            Log.warning(ME + ".NoNameService", "Can't access naming service, is there any running?");
            throw new XmlBlasterException(ME + ".NoNameService", "Can't access naming service, is there any running?");
         }
         if (Log.TRACE) Log.trace(ME, "Successfully accessed initial orb references for naming service (IOR)");

         nameService = org.omg.CosNaming.NamingContextHelper.narrow(nameServiceObj);
         if (nameService == null) {
            Log.error(ME + ".NoNameService", "Can't access naming service == null");
            throw new XmlBlasterException(ME + ".NoNameService", "Can't access naming service (narrow problem)");
         }
         if (Log.TRACE) Log.trace(ME, "Successfully narrowed handle for naming service");

         return nameService; // Note: the naming service IOR is successfully evaluated (from a IOR),
                             // but it is not sure that the naming service is really running
      }
      catch (Exception e) {
         Log.warning(ME + ".NoNameService", "Can't access naming service" + e.toString());
         throw new XmlBlasterException(ME + ".NoNameService", e.toString());
      }
   }


   /**
    * Keyboard input usage.
    */
   private void keyboardUsage()
   {
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "Following interactive keyboard input is recognized:");
      Log.plain(ME, "Key:");
      Log.plain(ME, "   -g             Popup the control panel GUI.");
      Log.plain(ME, "   -d <file name> Dump internal state of xmlBlaster to file.");
      Log.plain(ME, "   -q             Quit xmlBlaster.");
      Log.plain(ME, "----------------------------------------------------------");
   }


   /**
    * Command line usage.
    */
   private void usage()
   {
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "jaco org.xmlBlaster.Main <options>");
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "Options:");
      Log.plain(ME, "   -?                  Print this message.");
      Log.plain(ME, "   -iorFile            Specify a file where to dump the IOR of the AuthServer (for client access).");
      Log.plain(ME, "   -iorPort            Specify a port number where the builtin http server publishes its AuthServer IOR.");
      Log.plain(ME, "                       Default is port 7609, the port -1 switches this feature off.");
      Log.plain(ME, "   -ns false           Don't publish the IOR to a naming service.");
      Log.plain(ME, "                       Default is to publish the IOR to a naming service.");
      Log.usage();
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "Example:");
      Log.plain(ME, "   jaco org.xmlBlaster.Main -iorPort 8080");
      Log.plain(ME, "   jaco org.xmlBlaster.Main -iorFile /tmp/NS_Ref");
      Log.plain(ME, "   jaco org.xmlBlaster.Main +trace +dump +calls +time");
      Log.plain(ME, "");
   }


   /**
    *  Invoke: jaco org.xmlBlaster.Main
    */
   public static void main( String[] args )
   {
      new Main(args);
   }
}
