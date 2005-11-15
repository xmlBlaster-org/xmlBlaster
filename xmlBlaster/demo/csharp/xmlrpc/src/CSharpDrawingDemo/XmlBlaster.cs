/**
 * 
 * XML-RPC library :
 * http://xml-rpc.net/ by Charles Cook
 * I'm using version 0.9.2 (22nd May 2005)
 * 
 */
using System;
using System.Text ;
using System.Diagnostics ;
using System.Collections.Specialized ;
using System.Xml ;

using System.Windows.Forms ; // for MessageBox
using System.Net ; // for WebException

using System.Runtime.Remoting ;
using System.Runtime.Remoting.Channels ;
using System.Runtime.Remoting.Channels.Http ;

using CookComputing.XmlRpc;

namespace XmlBlaster
{
	/// <summary>
	/// Description résumée de Class1.
	/// </summary>
	public interface IXmlBlasterClient
	{
		[XmlRpcMethod("authenticate.connect")]
		string Connect( string connectQos );

		[XmlRpcMethod("authenticate.disconnect")]
		void Disconnect( string sessiondId, string qos );

		[XmlRpcMethod("xmlBlaster.publish")]
		string Publish( string sessiondId, string key, string xmlMessage, string qos );

		[XmlRpcMethod("xmlBlaster.subscribe")]
		string Subscribe( string sessiondId, string key, string qos );
	}


	public class XmlBlasterException : Exception
	{
		const string exceptionMessage = "XmlBlaster operation has failed.";

		public XmlBlasterException()
			: base( exceptionMessage )
		{
		}

		public XmlBlasterException( string auxMessage )
			: base( String.Format( "{0} - {1}", exceptionMessage, auxMessage ) )
		{
		}

		public XmlBlasterException( string auxMessage, Exception inner )
			: base( String.Format( "{0} - {1}", exceptionMessage, auxMessage ), inner )
		{
		}

		public XmlBlasterException( Exception inner )
			: base( String.Format( "{0} - {1}", exceptionMessage, inner.Message ), inner )
		{
		}

		internal static void HandleException( Exception ex )
		{
			//string msgBoxTitle = "Error" ;
			try
			{
				throw ex;
			}
			catch(XmlRpcFaultException fex)
			{
				//MessageBox.Show("Fault Response: " + fex.FaultCode + " " 
				//	+ fex.FaultString, msgBoxTitle,
				//	MessageBoxButtons.OK, MessageBoxIcon.Error);
				throw new XmlBlasterException( fex.FaultString, fex ) ;
			}
			catch(WebException webEx)
			{
				//MessageBox.Show("WebException: " + webEx.Message, msgBoxTitle,
				//	MessageBoxButtons.OK, MessageBoxIcon.Error);
				if (webEx.Response != null)
					webEx.Response.Close();
				throw new XmlBlasterException( webEx ) ;
			}
			catch(XmlRpcServerException xmlRpcEx)
			{
				//MessageBox.Show("XmlRpcServerException: " + xmlRpcEx.Message, 
				//	msgBoxTitle,
				//	MessageBoxButtons.OK, MessageBoxIcon.Error);
				throw new XmlBlasterException( xmlRpcEx ) ;
			}
			catch(Exception defEx)
			{
				//MessageBox.Show("Exception: " + defEx.Message, msgBoxTitle,
				//	MessageBoxButtons.OK, MessageBoxIcon.Error);
				throw new XmlBlasterException( defEx ) ;
			}
		}

	}


	public class XmlBlasterCallback : MarshalByRefObject 
	{
		public delegate void MessageArrivedDelegate( string key, string xmlMessage );
		public static MessageArrivedDelegate messageArrived ;


		[XmlRpcMethod("ping")] 
		public string Ping( string msg ) 
		{
			// [12-Nov-2005 11:18:15] string(144) "<?xml version="1.0" encoding="ISO-8859-1"?><methodCall><methodName>ping</methodName><params><param><value></value></param></params></methodCall>"

			Debug.WriteLine( "XmlBlasterCallback.Ping()" );

			//Debug.WriteLine( "\t msg: ", msg );

			return "<qos><state>OK</state></qos>";
		}

		[XmlRpcMethod("update")] 
		public string Update( string cbSessionId, string key, System.Byte[] msg, string qos ) 
		{
			/*
			[12-Nov-2005 11:18:15] string(811) "
			<?xml version="1.0" encoding="ISO-8859-1"?>
				<methodCall>
					<methodName>update</methodName>
					<params>
						<param><value>unknown</value></param>
						<param>
							<value>&lt;key oid='demo.csharp.drawing' contentMime='text/xml'&gt;
							&lt;sender&gt;0,803539002222726&lt;/sender&gt;
							&lt;/key&gt;</value>
						</param>
						<param>
							<value><base64>PGRyYXdpbmdNZXNzYWdlPgo8ZGF0YSB0eXBlPSJsaW5lIj4KIDxwb2ludCB4PSIxODciIHk9Ijg4IiAvPgogPHBvaW50IHg9IjI3MCIgeT0iOTciIC8+CjwvZGF0YT4KPC9kcmF3aW5nTWVzc2FnZT4=</base64></value>
						</param>
						<param><value>&lt;qos&gt;
							&lt;sender&gt;/node/xmlBlaster_127_0_0_1_3412/client/guest/-7&lt;/sender&gt;
							&lt;subscribe id='__subId:xmlBlaster_127_0_0_1_3412-1131790644308000000'/&gt;
							&lt;rcvTimestamp nanos='1131790693990000000'/&gt;
							&lt;queue index='0' size='1'/&gt;
							&lt;/qos&gt;</value>
						</param>
					</params>
				</methodCall>"
			*/

			Debug.WriteLine( "XmlBlasterCallback.Update()" );

			//Debug.WriteLine( "\t cbSessionId: ", cbSessionId );
			Debug.WriteLine( "\t key: ", key );
			//Debug.WriteLine( "\t messageUnit: ", messageUnit );

			if( messageArrived != null )
			{
				string xml = Encoding.Default.GetString( msg );
				messageArrived( key, xml );
			}

			return "<qos><state>OK</state></qos>";
		}
	}


	public class XmlBlasterClient
	{
		IXmlBlasterClient xmlBlasterClientProxy ;
		XmlRpcClientProtocol xmlBlasterClientProtocol ;

		Uri callbackServerUri ;

		string uniqueId ;
		string sessionId ;
		public string SessionId
		{
			get { return this.sessionId ; }
		}

		public string Url
		{
			get { return xmlBlasterClientProtocol.Url ; }
			set { xmlBlasterClientProtocol.Url = value ; }
		}


		public XmlBlasterClient()
		{
			// Client

			xmlBlasterClientProxy = (IXmlBlasterClient) XmlRpcProxyGen.Create(typeof(IXmlBlasterClient)) ;
			xmlBlasterClientProtocol = (XmlRpcClientProtocol) xmlBlasterClientProxy ;

			// Server for Callback

			//RemotingConfiguration.Configure("xmlrpc.exe.config"); 

			HttpChannel channel = null ;

			try 
			{
				//int port = FindFreeHttpPort( 9090 ) ;
				//Debug.WriteLine( "FindFreeHttpPort() found port "+port );
				int port = 0 ;

				ListDictionary channelProperties = new ListDictionary();
				channelProperties.Add( "port", port );

				channel = new HttpChannel(
					channelProperties,
					new CookComputing.XmlRpc.XmlRpcClientFormatterSinkProvider(),
					new CookComputing.XmlRpc.XmlRpcServerFormatterSinkProvider(null,null)
					//new SoapClientFormatterSinkProvider(),
					//new SoapServerFormatterSinkProvider()
					);
			}
			catch( Exception ex )
			{
				// Listener config failed : Une seule utilisation de chaque adresse de socket (protocole/adresse réseau/port) est habituellement autorisée

				Debug.WriteLine( "Listener config failed : " + ex.Message );

				XmlBlasterException.HandleException( new Exception("Listener config failed.", ex ) );
			}

			ChannelServices.RegisterChannel( channel );

			RemotingConfiguration.RegisterWellKnownServiceType( 
				typeof(XmlBlasterCallback), 
				"XmlBlasterCallback",
				WellKnownObjectMode.Singleton
			);

			// Print out the urls for HelloServer.
			string[] urls = channel.GetUrlsForUri("XmlBlasterCallback");
			foreach (string url in urls)
				System.Console.WriteLine("url: {0}", url);
			//url: http://127.0.0.1:1038/XmlBlasterCallback
			if( urls.Length != 1 )
			{
				XmlBlasterException.HandleException( new Exception("XmlBlasterCallback server, failed to retreive url." ) );
			}
			this.callbackServerUri = new Uri( urls[ 0 ] );

		}


		public void Connect( string url, string username, string password )
		{
			Debug.WriteLine( "XmlBlaster.Connect()" );

			this.Url = url ; // Set xml-rpc server url

			// construct authentification data
			QosConnect qos ;

			try
			{
				// try connect (login)
				qos = new QosConnect( username,password,this.callbackServerUri.ToString() );
				string result = xmlBlasterClientProxy.Connect( qos.ToString() );
				//Debug.Write( "Connect Result :" );
				//Debug.WriteLine( result );
				QosConnectResult qosRes = new QosConnectResult( result );
				this.sessionId = qosRes.SessionId ;

				// TODO: sessionId should be secret !
				// do something better ;o)
				Random rnd = new Random( );
				this.uniqueId = rnd.NextDouble().ToString() ;
			}
			catch( Exception ex )
			{
				XmlBlasterException.HandleException(ex);
			}
		}

		public void Disconnect()
		{
			Debug.WriteLine( "XmlBlaster.Disconnect()" );

			// construct authentification data
			string qos = @"
				<qos>
					<deleteSubjectQueue>true</deleteSubjectQueue>
					<clearSessions>true</clearSessions>
				</qos>";

			try
			{
				xmlBlasterClientProxy.Disconnect( this.sessionId, qos );
			}
			catch( Exception ex )
			{
				XmlBlasterException.HandleException(ex);
			}
		}

		public void Subscribe( string topic )
		{
			Debug.WriteLine( "XmlBlaster.Subscribe()" );

			try
			{
				string key = "<key oid=\""+topic+"\" />\n" ;
				string qos = @"<qos>
					<local>false</local>
					</qos>" ;

				string result = xmlBlasterClientProxy.Subscribe( this.sessionId, key, qos );

				Debug.Write( "Subscribe Result :" );
				Debug.WriteLine( result );

			}
			catch( Exception ex )
			{
				XmlBlasterException.HandleException(ex);
			}
		}

		public void Publish( string topic, string xmlMessage )
		{
			Debug.WriteLine( "XmlBlaster.Publish()" );

			try
			{
				string key = "<key oid=\""+topic+"\" contentMime=\"text/xml\" >\n" ;
				key += "<sender>"+uniqueId+"</sender>\n" ;
				key += "</key>" ;

				string qos = "<qos />" ;

				string result = xmlBlasterClientProxy.Publish( this.sessionId, key, xmlMessage, qos );

				//Debug.Write( "Publish Result :" );
				//Debug.WriteLine( result );
				/*
					XmlBlaster.Publish()
					Publish Result :
					<qos>
					<key oid='demo.csharp.drawing'/>
					<rcvTimestamp nanos='1131972554614000000'/>
					<isPublish/>
					</qos>
				 */

			}
			catch( Exception ex )
			{
				XmlBlasterException.HandleException(ex);
			}
		}

	}


	public class QosConnect
	{
		public string securityService_type = "htpasswd" ;
		public string securityService_version = "1.0" ;
		public string username ;
		public string password ;
		public string callbackUri = null ;

		public QosConnect()
		{
		}
		public QosConnect( string username, string password )
		{
			this.username = username ;
			this.password = password ;
			this.callbackUri = null ;
		}
		public QosConnect( string username, string password, string callbackUri )
		{
			this.username = username ;
			this.password = password ;
			this.callbackUri = callbackUri ;
		}

		public override string ToString()
		{
			StringBuilder sb = new StringBuilder( 255 );

			sb.Append( "<qos>\n" );

			sb.Append( " <securityService type=\""+this.securityService_type + "\" version=\""+this.securityService_version+"\">\n" );
			sb.Append( "  <user>"+ this.username +"</user>\n" );
			sb.Append( "  <passwd>"+ this.password +"</passwd>\n" );
			sb.Append( " </securityService>\n" );

			if( callbackUri != null ){
				sb.Append( "<callback type=\"XMLRPC\" retries=\"2\" delay=\"2000\" pingInterval=\"5000\" >" );
				//sb.Append( "http://192.168.0.151:9090/RPC2" );
				//sb.Append( "http://127.0.0.1:9090/RPC2" );

				//sb.Append( "http://127.0.0.1:8090/EssaisDivers/xmlBLaster.essais/demo.php/callback.php" );
				//sb.Append( "http://127.0.0.1:9090/XmlBlasterCallback" );
				sb.Append( this.callbackUri );

				sb.Append( "</callback>\n" );
			}

			// Subscribe Qos ?
			//sb.Append( "<local>false</local>\n" );

			sb.Append( "</qos>\n" );

			return sb.ToString();
		}

	}

	public class QosConnectResult
	{
		/*
		<qos>
			<securityService type="htpasswd" version="1.0">
				<user>guest</user>
				<passwd>guest</passwd>
			</securityService>
			<instanceId>/xmlBlaster/node/xmlBlaster_192_168_0_151_3412/instanceId/1131719045135</instanceId>
			<session name='/node/xmlBlaster_192_168_0_151_3412/client/guest/-4'
				timeout='86400000' maxSessions='10' clearSessions='false' reconnectSameClientOnly='false'
				sessionId='sessionId:192.168.0.151-null-1131720478106-2108614840-5'
			/>
			<queue relating='connection' maxEntries='10000000' maxEntriesCache='1000'>
				<address type='IOR' dispatchPlugin='undef'>
				</address>
			</queue>
			<queue relating='subject'/>
			<queue relating='callback' maxEntries='1000' maxEntriesCache='1000'/>
		</qos>
		 */
		protected XmlDocument xmlDoc ;
		public QosConnectResult( string xml )
		{
			this.xmlDoc = new XmlDocument();
			xmlDoc.LoadXml( xml );

		}

		public string SessionId
		{
			get {
				XmlNode root = xmlDoc.DocumentElement ;
				XmlNode node ;

				string s ;

				//node = root.SelectSingleNode("descendant::book[author/last-name='Austen']");
				node = root.SelectSingleNode("session");
				s = node.Attributes[ "sessionId" ].Value ;

				Debug.WriteLine("sessionId : "+s);
				return s ;
			}
		}
	}

}