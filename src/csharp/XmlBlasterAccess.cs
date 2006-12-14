/*----------------------------------------------------------------------------
Name:      XmlBlasterAccess.cs
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Provides abstraction to xmlBlaster access from C#
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      07/2006
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.html
-----------------------------------------------------------------------------*/
using System;
using System.Text;
using System.Runtime.InteropServices;

namespace org.xmlBlaster
{
   public class XmlBlasterAccessFactory
   {
      public static I_XmlBlasterAccess createInstance(String[] argv)
      {
#        if WINCE || Smartphone || PocketPC || WindowsCE || FORCE_PINVOKECE
            return new PInvokeCE(argv);
#        else
         return new NativeC(argv);
#        endif
      }
   }

   public interface I_Callback
   {
      string OnUpdate(string cbSessionId, MsgUnit msgUnit);
   }

   /// <summary>
   /// Access xmlBlaster, for details see
   /// http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.html
   /// All methods can throw an XmlBlasterException
   /// </summary>
   public interface I_XmlBlasterAccess
   {
      string connect(string qos, I_Callback listener);
      
      /// After calling diconnect() this class is not usable anymore
      /// you need to create a new instance to connect again
      bool disconnect(string qos);

      /// <summary>
      /// Publish a message to xmlBlaster
      /// </summary>
      /// <param name="msgUnit">The message to send</param>
      /// <returns>The publish return qos</returns>
      string publish(MsgUnit msgUnit);

      /// <summary>
      /// Convenience method to publish a message 
      /// </summary>
      /// <param name="key">The topic description</param>
      /// <param name="content">Your data to send</param>
      /// <param name="qos">The required quality of service</param>
      /// <returns>The publish return qos</returns>
      string publish(string key, string content, string qos);

      void publishOneway(MsgUnit[] msgUnitArr);
      
      string subscribe(string key, string qos);
      
      string[] unSubscribe(string key, string qos);
      
      string[] erase(string key, string qos);
      
      MsgUnit[] get(string key, string qos);
      
      string ping(string qos);
      
      /// <summary>
      /// Check if we have a connection to the xmlBlaster server
      /// </summary>
      /// <returns>true if we are connected</returns>
      bool isConnected();

      void log(String str);
   }

   public class XmlBlasterException : ApplicationException
   {
      bool remote;
      public bool Remote
      {
         get { return remote; }
      }
      string errorCode;
      public string ErrorCode
      {
         get { return errorCode; }
      }
      //string message;
      public XmlBlasterException(string errorCode, string message)
         : base(message)
      {
         this.remote = false;
         this.errorCode = errorCode;
      }
      public XmlBlasterException(string errorCode, string message, Exception inner)
         : base(message, inner)
      {
         this.remote = false;
         this.errorCode = errorCode;
      }
      public XmlBlasterException(bool remote, string errorCode, string message)
         : base(message)
      {
         this.remote = remote;
         this.errorCode = errorCode;
      }
      public override string ToString()
      {
         string ret = "errorCode=" + this.errorCode;
         if (remote)
            ret += " thrownFromRemote";
         if (Message != null)
            ret += " message=" + Message;
         Exception inner = InnerException;
         if (inner != null)
            ret += inner.ToString();
         return ret;
      }
   }

   /// <summary>
   /// Holds a message unit
   /// </summary>
   public class MsgUnit
   {
      public string key;
      public byte[] content;
      public string qos;
      public MsgUnit() { }
      public MsgUnit(string key, byte[] content, string qos)
      {
         this.key = key;
         this.content = (content == null) ? new byte[0] : content;
         this.qos = qos;
      }
      public MsgUnit(string key, string contentStr, string qos)
      {
         this.key = key;
         setContentStr(contentStr);
         this.qos = qos;
      }
      public string getKey()
      {
         return this.key;
      }
      public string getQos()
      {
         return this.qos;
      }
      public byte[] getContent()
      {
         return this.content;
      }
      /// We return a string in the ASCII codeset
      public string getContentStr()
      {
         System.Text.ASCIIEncoding enc = new System.Text.ASCIIEncoding();
         return enc.GetString(this.content, 0, this.content.Length);
      }
      /// The binary string is ASCII encoded
      public void setContentStr(string contentStr)
      {
         System.Text.ASCIIEncoding enc = new System.Text.ASCIIEncoding();
         this.content = enc.GetBytes(contentStr);
      }
      public override string ToString()
      {
         return key + "\n" + getContentStr() + "\n" + qos;
      }
   }
}
