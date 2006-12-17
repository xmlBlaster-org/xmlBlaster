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

namespace org.xmlBlaster.client
{
   public class XmlBlasterAccessFactory
   {
      public static I_XmlBlasterAccess createInstance(String[] argv)
      {
         // Choose the PInvoke plugin:
#        if FORCE_PINVOKECE_PLUGIN

            return new PInvokeCE(argv); // Runs fine with WIN32 and WINCE (fails with MONO)

#        elif XMLBLASTER_MONO || FORCE_NATIVEC_PLUGIN
            
            return new NativeC(argv); // First try, runs fine with WIN32 and with Linux/MONO

#        else // WIN32 || WINCE || Smartphone || PocketPC || WindowsCE || FORCE_PINVOKECE
            
            return new PInvokeCE(argv); // Runs fine with WIN32 and WINCE (fails with MONO)

#        endif
      }
   }

   /// <summary>
   /// Log levels copied from xmlBlaster client C library
   /// See helper.h enum XMLBLASTER_LOG_LEVEL_ENUM
   /// </summary>
   public enum LogLevel {
      /*NOLOG=0,  don't use */
      ERROR=1,  // supported, use for programming errors
      WARN=2,   // supported, use for user errors and wrong configurations
      INFO=3,   // supported, use for success information only
      /*CALL=4,  don't use */
      /*TIME=5,  don't use */
      TRACE=6,  // supported, use for debugging purposes
      DUMP=7    // supported, use for debugging purposes
      /*PLAIN=8  don't use */
   }

   public interface I_Callback
   {
      string OnUpdate(string cbSessionId, MsgUnit msgUnit);
   }

   public interface I_LoggingCallback
   {
      void OnLogging(LogLevel logLevel, string location, string message);
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

      string getVersion();

      string getUsage();

      /// <summary>
      /// Register the given listener to receive all logging output
      /// of the C library and C# wrapper code.
      /// </summary>
      /// <param name="listener">The logging is redirected to this listener</param>
      void addLoggingListener(I_LoggingCallback listener);

      /// <summary>
      /// Returns the telephone EMEI id if available. 
      /// The International Mobile Equipment Identity (IMEI) is a 15 digit number
      /// and unique over time and space for your PDA.
      /// </summary>
      /// <returns>null if not found</returns>
      string getEmeiId();

      /// <summary>
      /// Returns the device unique id, to be used in favour of getEmeiId().
      /// Supported since Windows CE 5.0.1, please check
      /// the correct behaviour on your device as it may not
      /// be implemented properly by your vendor.
      /// </summary>
      /// <returns>return null if not found</returns>
      string getDeviceUniqueId();
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
      private string key;
      private byte[] content;
      private string qos;
      protected bool oneway;
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
      public bool isOneway()
      {
         return this.oneway;
      }
      public override string ToString()
      {
         return key + "\n" + getContentStr() + "\n" + qos;
      }
   }
}
