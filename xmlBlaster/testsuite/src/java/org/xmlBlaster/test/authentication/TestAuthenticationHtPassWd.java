package org.xmlBlaster.test.authentication;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.jutils.io.FileUtil;

import org.xmlBlaster.test.Util;


import junit.framework.*;

public class TestAuthenticationHtPassWd extends TestCase
{
  private EmbeddedXmlBlaster serverThread = null;
  private final String RIGHT_USERNAME = "existingUser";
  private final String PARTIAL_USERNAME = "existingSomeThingElseStandsBehind";
  private final String RIGHT_PASSWORD = "existingUserPW";
  private final String WRONG_USERNAME = "notExistingUser";
  private final String WRONG_PASSWORD = "notExistingUserPW";
  private String userhome = "";
  private Global glob = null;
   private static Logger log = Logger.getLogger(TestAuthenticationHtPassWd.class.getName());
  private I_XmlBlasterAccess con = null;
  private int serverPort = 7604;

  public final String ME = "TestAuthenticationHtPassWd";



  public TestAuthenticationHtPassWd (String name)
  { super(name);
    this.glob = new Global();

    this.userhome = glob.getProperty().get("user.home","/home/astelzl")+java.io.File.separatorChar;
    try
    { FileUtil.writeFile(userhome+"test.htpasswd","existingUser:yZum5CYzDk.EE\n");
      FileUtil.writeFile(userhome+"test.htpasswd2","existing:yZum5CYzDk.EE\n");
      FileUtil.writeFile(userhome+"test.htpasswd1","*");
    }
    catch(Exception ex)
    { assertTrue("Could not create password files in directory '" + userhome + "'. Tests won't work!",false);
    }
  }                                                         

  protected void setUp()
  {
  }

  private void setupTestCase(int testcase)
  { 
    String[] ports = Util.getOtherServerPorts(serverPort);
    String[] args = new String[4+ports.length];
    switch (testcase)
    { case 1: args[0] = "-Security.Server.Plugin.htpasswd.secretfile";      
              args[1] = userhome+"test.htpasswd2";
              args[2] = "-Security.Server.Plugin.htpasswd.allowPartialUsername";
              args[3] = "true";
              break;
      case 2: args[0] = "-Security.Server.Plugin.htpasswd.secretfile";
              args[1] = userhome+"test.htpasswd";
              args[2] = "-Security.Server.Plugin.htpasswd.allowPartialUsername";
              args[3] = "false";
              break;  
      case 3: args[0] = "-Security.Server.Plugin.htpasswd.secretfile";
              args[1] = userhome+"test.htpasswd";
              args[2] = "-Security.Server.Plugin.htpasswd.allowPartialUsername";
              args[3] = "false";
              break;  
      case 4: args[0] = "-Security.Server.Plugin.htpasswd.secretfile";
              args[1] = userhome+"test.htpasswd1";
              args[2] = "-Security.Server.Plugin.htpasswd.allowPartialUsername";
              args[3] = "false";
              break;  
    }
    for (int i=0;i<ports.length ;i++ ) {
      args[i+4] = ports[i];
    }
    glob.init(args);
    serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
  }

  protected void tearDown() {
     try { Thread.sleep(1000);} catch(Exception ex) {} 
     if (serverThread != null)
       serverThread.stopServer(true);
     glob.init(Util.getDefaultServerPorts());
     Util.resetPorts(glob);
     this.glob = null;
    
     this.con = null;
     Global.instance().shutdown();
  }

  public void testAuthHtPassWordCase1()
  { log.info("Testcase1");
    setupTestCase(1);
    boolean isValue=true;
    try
    { con = glob.getXmlBlasterAccess();
    }
    catch(Exception ex)
    { log.severe("Could not initialize I_XmlBlasterAccess: " + ex.toString());
      ex.printStackTrace();
    }
    try
    { ConnectQos qos = new ConnectQos(glob,PARTIAL_USERNAME, RIGHT_PASSWORD);
      ConnectReturnQos conRetQos = con.connect(qos, null);
      con.disconnect(null);
    }
    catch(Exception ex)
    { log.info("Could not connect: " + ex.toString());
      ex.printStackTrace();
      isValue = false;
    }
    assertTrue("Could not connect although it should have been possible with the specified beginning of username and password",isValue);
                 
  }
  
  public void testAuthHtPassWordCase2()
  { log.info("Testcase2");
    setupTestCase(2);
    boolean isValue = true;
    try
    { con = glob.getXmlBlasterAccess();
    }
    catch(Exception ex)
    { log.severe("Could not initialize I_XmlBlasterAccess");
    }
    try
    { ConnectQos qos = new ConnectQos(glob,RIGHT_USERNAME, RIGHT_PASSWORD);
      ConnectReturnQos conRetQos = con.connect(qos, null);
      con.disconnect(null);
    }
    catch(Exception ex)
    { log.info("Could not connect");
      isValue = false;
      ex.printStackTrace();
    }
    assertTrue("Could not connect although it should have been possible with the specified username and password",isValue);

  }

  public void testAuthHtPassWordCaseWrongPassword()
  { log.info("Testcase3");
    setupTestCase(3);
    boolean isValue = false;
    try
    { con = glob.getXmlBlasterAccess();
    }
    catch(Exception ex)
    { log.severe("Could not initialize I_XmlBlasterAccess");
    }
    try
    { ConnectQos qos = new ConnectQos(glob,WRONG_USERNAME, WRONG_PASSWORD);
      ConnectReturnQos conRetQos = con.connect(qos, null);
      con.disconnect(null);
      assertTrue("Could connect although it should not have been possible with the specified username and password",isValue);
    }
    catch(Exception ex)
    { isValue = true;
      log.info("Could not connect");
    }
  }

  public void testAuthHtPassWordCase3()
  { log.info("Testcase4");
    setupTestCase(4);
    boolean isValue = true;
    try
    { con = glob.getXmlBlasterAccess();
    }
    catch(Exception ex)
    { log.severe("Could not initialize I_XmlBlasterAccess");
    }
    try
    { ConnectQos qos = new ConnectQos(glob,WRONG_USERNAME, WRONG_PASSWORD);
      ConnectReturnQos conRetQos = con.connect(qos, null);
      con.disconnect(null);
    }
    catch(Exception ex)
    { log.info("Could not connect");
      isValue = false;
    }
    assertTrue("Could not connect although it should have been possible as any username and password is authenticated",isValue);
  }
  
  
}
