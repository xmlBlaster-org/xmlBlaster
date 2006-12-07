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
#        if WINCE || Smartphone || PocketPC 
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

   public interface I_XmlBlasterAccess
   {
      string connect(string qos, I_Callback listener);
      /// After calling diconnect() this class is not usable anymore
      /// you need to create a new instance to connect again
      bool disconnect(string qos);
      string publish(string key, string content, string qos);
      void publishOneway(MsgUnit[] msgUnitArr);
      string subscribe(string key, string qos);
      string[] unSubscribe(string key, string qos);
      string[] erase(string key, string qos);
      MsgUnit[] get(string key, string qos);
      string ping(string qos);
      bool isConnected();
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

   // SEE http://msdn2.microsoft.com/en-us/library/2k1k68kw.aspx
   // Declares a class member for each structure element.
   // Must match exactly the C struct MsgUnit (sequence!)
   [StructLayout(LayoutKind.Sequential/*, CharSet=CharSet.Unicode*/ )]
   public class MsgUnit
   {
      public string key;
      public int contentLen;
      // Without MarshalAs: ** ERROR **: Structure field of type Byte[] can't be marshalled as LPArray
      //[MarshalAs (UnmanagedType.ByValArray, SizeConst=100)] // does not work unlimited without SizeConst -> SIGSEGV
      // public byte[] content; // Ensure UTF8 encoding for strings
      public string content;
      public string qos;
      public string responseQos;
      public MsgUnit() { }
      public MsgUnit(string key, string contentStr, string qos)
      {
         this.key = key;
         this.contentLen = contentStr.Length;
         setContentStr(contentStr);
         this.qos = qos;
      }
      /// We return a string in the default codeset
      public string getContentStr()
      {
         //System.Text.ASCIIEncoding enc = new System.Text.ASCIIEncoding();
         //return enc.GetString(this.content);
         // How does this work? System.Text.Decoder d = System.Text.Encoding.UTF8.GetDecoder();
         return this.content;
      }
      /// The binary string is UTF8 encoded (xmlBlaster default)
      public void setContentStr(string contentStr)
      {
         //this.content = System.Text.Encoding.UTF8.GetBytes(contentStr);
         this.content = contentStr;
      }
      public override string ToString()
      {
         return key + "\n" + content + "\n" + qos;
      }
   }
}
