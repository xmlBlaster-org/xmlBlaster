/*------------------------------------------------------------------------------
Name:      RecorderBuffer.java
Project:   xmlBlaster.org
Comment:   Is responsible for the client request storage. Data is written
           on a DataOutputStream.
Author:    astelzl@avitech.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.recorder.file;

import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.IOException;

import org.xmlBlaster.engine.helper.MessageUnit;

/**
 * Is responsible for the client request storage. Data is written
 * on a DataOutputStream. This class is very similar to the 
 * FileConnection of Pavol (pixelpark).
 * @author pavol.hrnciarik@pixelpark.com
 * @author astelzl@avitech.de
 */
public class RecorderBuffer 
{
  private DataOutputStream dos = null;
  private DataInputStream dis = null;
  private final String fileName;

  private File f = null;
  private int size;

  private long skipBytes;
  private boolean emptyStorage = false;
   
  /**
   * Creating file and initialization
   */
  public RecorderBuffer(String fileName) throws IOException
  {
    this.fileName = fileName;
    f = new File(this.fileName);
    size = 0;
    initialize();
  }

  /**
   * initializations
   */
  public void initialize() throws IOException
  { if ((f.exists()) && (f.length() > 0))
      emptyStorage = false;
    else {
      emptyStorage = true;
      f.createNewFile();
    }
  }

  //File is read here. Objects are read out one after another. The position
  //of the last written item is remembered to start at that position at the 
  //next reading process. Depending on the client request the variables
  //of the Requestcontainer are stored in the sequence of definition
  private RequestContainer readObject()
  { String rqMethod="",rqCbSessionId="",rqXmlKey="",rqXmlQos="",rqKey="",rqQos="";
    MessageUnit[] rqMsgUnitArr=null;
    MessageUnit rqMsg=null;
    byte rcContent[] = null;
    int numbMessages;
    RequestContainer rc = new RequestContainer();
    try
    { //Closing outputstream
      if (dos != null)
      { dos.close();
        dos = null;
      }

      dis = new DataInputStream(new FileInputStream(f));

      if (skipBytes == f.length())
      { dis.close();
        dis = null;
        f.delete();
        emptyStorage = true;
        skipBytes = 0;
        return null;
      }

      dis.skip(skipBytes);
      
      rqMethod = dis.readUTF();
      skipBytes += rqMethod.getBytes().length + 2; //Overhead of DataOutputStream
     
      //Each request type just uses a subset of the variables of the Requestcontainer
      if (rqMethod.equals("update") || rqMethod.equals("updateOneway"))
      { rqCbSessionId = dis.readUTF();
        skipBytes += rqCbSessionId.getBytes().length + 2;
      }
      if (rqMethod.equals("subscribe") || 
          rqMethod.equals("unSubscribe") ||
          rqMethod.equals("erase") ||
          rqMethod.equals("get"))
      { rqXmlKey = dis.readUTF();
        skipBytes += rqXmlKey.getBytes().length + 2;
        rqXmlQos = dis.readUTF();
        skipBytes += rqXmlQos.getBytes().length + 2;
      }
      if (rqMethod.equals("publishArr") || rqMethod.equals("publishOneway") || rqMethod.equals("update") || rqMethod.equals("updateOneway"))
      { numbMessages = dis.readInt();
        skipBytes += 4; //int has 4 bytes
        rqMsgUnitArr = new MessageUnit[numbMessages];
        for (int i=0;i<numbMessages;i++)
        { rqKey = dis.readUTF();
          skipBytes += rqKey.getBytes().length + 2;
          rcContent = new byte[dis.readInt()];
          skipBytes += 4;
          dis.read(rcContent);
          skipBytes += rcContent.length;
          rqQos = dis.readUTF();
          skipBytes += rqQos.getBytes().length + 2;
          rqMsgUnitArr[i] = new MessageUnit(rqKey,rcContent,rqQos);
        }
      }
      if (rqMethod.equals("publish"))
      { rqKey = dis.readUTF();
        skipBytes += rqKey.getBytes().length + 2;
        rcContent = new byte[dis.readInt()];
        skipBytes += 4;
        dis.read(rcContent);
        skipBytes += rcContent.length;
        rqQos = dis.readUTF();
        skipBytes += rqQos.getBytes().length + 2;
        rqMsg = new MessageUnit(rqKey,rcContent,rqQos);
      }
      dis.close();
      rc.method = rqMethod;
      rc.cbSessionId = rqCbSessionId;
      rc.xmlKey = rqXmlKey;
      rc.xmlQos = rqXmlQos;
      rc.msgUnit = rqMsg;
      rc.msgUnitArr = rqMsgUnitArr;
      return rc;
    }
    catch(Exception ex)
    { ex.printStackTrace();
    }
    return null;  
  } 

  //Depending on the client request, certain variables of the Requestcontainer are written
  private void writeObject(RequestContainer rc)
  { 
    if (!f.exists())
    { try
      { f.createNewFile();
      }
      catch(Exception ex)
      { ex.printStackTrace();
      }
    }

    if (dos == null)
    { try
      { dos = new DataOutputStream(new FileOutputStream(f.toString(),true));
      }
      catch(Exception ex)
      { ex.printStackTrace();
      }
    }

    if (emptyStorage)
      emptyStorage = false;
    
    try
    { dos.writeUTF(rc.method);
      if (rc.method.equals("update") || rc.method.equals("updateOneway"))
        dos.writeUTF(rc.cbSessionId);
      if (rc.method.equals("subscribe") ||
          rc.method.equals("unSubscribe") ||
          rc.method.equals("erase") ||
          rc.method.equals("get"))
      { dos.writeUTF(rc.xmlKey);
        dos.writeUTF(rc.xmlQos);
      }
      if (rc.method.equals("publishArr") || rc.method.equals("publishOneway") || rc.method.equals("update") || rc.method.equals("updateOneway"))
      { dos.writeInt(rc.msgUnitArr.length);
        for (int i=0;i<rc.msgUnitArr.length;i++)
        { dos.writeUTF(rc.msgUnitArr[i].getXmlKey());
          dos.writeInt(rc.msgUnitArr[i].getContent().length);
          dos.write(rc.msgUnitArr[i].getContent());
          dos.writeUTF(rc.msgUnitArr[i].getQos());
        }
      }
      if (rc.method.equals("publish"))
      { dos.writeUTF(rc.msgUnit.getXmlKey());
        dos.writeInt(rc.msgUnit.getContent().length);
        dos.write(rc.msgUnit.getContent());
        dos.writeUTF(rc.msgUnit.getQos());
      }
      dos.flush();
    }
    catch(IOException ex)
    { ex.printStackTrace();
    }
  }

  /**
   * Reading RequestContainer
   */
  public RequestContainer readRequest() throws IOException
  { if (size == 0)
    { close();
      initialize();
      return null;
    }
    size--;

    return readObject();
  }

  /**
   * Writing request to file
   */
  public void writeRequest(RequestContainer rc) throws IOException
  { size++;
    writeObject(rc);
  }

  /**
   * returns the number of requests currently stored.
   */
  public int size()
  { return size;
  }

  /**
   * deleting file
   */
  public void close() throws IOException
  { 
    if (dis != null)
      dis.close();
    if (dos != null)
      dos.close();
    f.delete();
    size = 0;
  }
}
