using System;
using System.Text ;
//using System.Diagnostics ;
using System.Xml ;
using System.Collections ;
using System.Collections.Specialized ; // for ListDictionary
using System.Net ; // for WebException
using System.Runtime.Remoting ;
using System.Runtime.Remoting.Channels ;
using System.Runtime.Remoting.Channels.Http ;

using SimpleLogLib ;
using CookComputing.XmlRpc ;

namespace XmlBlasterLib
{
	/// <summary>
	/// Implémentation du client XmlBlaster
	/// </summary>
	public class XmlBlasterClient
	{
		IXmlBlasterClient xmlBlasterClientProxy ;
		XmlRpcClientProtocol xmlBlasterClientProtocol ;

		// TODO: Ajouter un identifiant de thread ou autre
		SimpleLog logger = SimpleLogLib.SimpleLogManager.GetLog("XmlBlasterClient", LogLevel.Debug );

		/// <summary>
		/// A utiliser par les qos
		/// </summary>
		internal static SimpleLog loggerQos = SimpleLogLib.SimpleLogManager.GetLog("XmlBlasterQos", LogLevel.Debug );

		//string uniqueId ;
		string sessionId ;
		public string SessionId
		{
			get { return this.sessionId ; }
		}


		Uri callbackServerUri ;
		public string Url
		{
			get { return xmlBlasterClientProtocol.Url ; }
			set { xmlBlasterClientProtocol.Url = value ; }
		}

		HttpChannel httpChannel ;

		public XmlBlasterClient()
		{
			//
			// Client
			//

			xmlBlasterClientProxy = (IXmlBlasterClient) XmlRpcProxyGen.Create(typeof(IXmlBlasterClient)) ;
			xmlBlasterClientProtocol = (XmlRpcClientProtocol) xmlBlasterClientProxy ;

			//
			// Callback Server
			//

			// On peut le faire depuis un fichier
			//RemotingConfiguration.Configure("xmlrpc.exe.config"); 

			// Ou bien à la mano
			try 
			{
				//int port = FindFreeHttpPort( 9090 ) ;
				//logger.Debug( "FindFreeHttpPort() found port "+port );
				// port = 0 pour que le système assigne automatiquement un port non-utilisé.
				int port = 0 ;

				ListDictionary channelProperties = new ListDictionary();
				channelProperties.Add( "port", port );

				httpChannel = new HttpChannel(
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
				XmlBlasterException.HandleException( new Exception("XmlBlaster callback server, Listener config failed.", ex ) );
			}

			try
			{
				ChannelServices.RegisterChannel( httpChannel );
			}
			catch( System.Runtime.Remoting.RemotingException )
			{
				// Pas grave: The channel has already been registered.
			}
			catch( Exception ex )
			{
				XmlBlasterException.HandleException( new Exception("XmlBlasterCallback server, failed to RegisterChannel.", ex ) );
			}

			RemotingConfiguration.RegisterWellKnownServiceType( 
				typeof(XmlBlasterCallback), 
				"XmlBlasterCallback",
				WellKnownObjectMode.Singleton
				);


			// Comme on a pas indiqué de port, celui-ci a été assigné automatiquement.
			// Il nous faut donc retrouver l'url afin de pouvoir la transmettre dans le Qos de connexion.

			string[] urls = httpChannel.GetUrlsForUri("XmlBlasterCallback");
			foreach (string url in urls)
				logger.Debug("XmlBlasterCallback url: {0}", url);
			//url: http://192.168.0.151:4808/XmlBlasterCallback
			if( urls.Length != 1 )
			{
				XmlBlasterException.HandleException( new Exception("XmlBlasterCallback server, failed to retreive url." ) );
			}
			this.callbackServerUri = new Uri( urls[ 0 ] );

		}


		public void Connect( string url, string username, string password )
		{
			logger.Debug( "XmlBlaster.Connect()" );
			logger.Debug( "XmlBlaster.Connect() url:["+url+"]" );

			// reset
			this.sessionId = null ;

			this.Url = url ; // Set xml-rpc server url

			try
			{
				// try connect (login)
				// to construct authentification data
				QosConnect qos = new QosConnect( username,password,this.callbackServerUri.ToString() );

				XmlBlasterCallback.pingInterval = qos.PingInterval ;

				string result = xmlBlasterClientProxy.Connect( qos.ToString() );

				QosConnectResult qosConnectResult = new QosConnectResult( result );

				this.sessionId = qosConnectResult.SessionId ;

				// TODO: sessionId should be secret !
				// do something better ;o)
				//Random rnd = new Random( );
				//this.uniqueId = rnd.NextDouble().ToString() ;
			}
			catch( Exception ex )
			{
				// reset
				this.sessionId = null ;

				XmlBlasterException.HandleException(ex);
			}
		}

		public void Disconnect()
		{
			string me="Disconnect()";
			logger.Debug(me);

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
				//XmlBlasterException.HandleException(ex);
				logger.Error( "{0} Failed. Ex: {1}.", me, ex.Message );
			}
		}


		/// <summary>
		/// Make a subscribtion to messages that match the query.
		/// </summary>
		/// <param name="query">the query to subscribe to</param>
		/// <param name="xpath">is it an xpath query</param>
		/// <returns>the Subscribtion ID or null if failed</returns>
		public string Subscribe( string query, bool xpath )
		{
			string me="Subscribe()";
			logger.Debug(me);

			string key ;
			if( xpath )
			{
				key = "<key oid='' queryType='XPATH'>"+query+"</key>";
			}
			else
			{
				key = "<key oid=\""+query+"\" />\n" ;
			}

			StringBuilder qos = new StringBuilder();
			qos.Append("<qos>");
			//qos.Append("<initialUpdate>false</initialUpdate>");
			qos.Append("<local>false</local>");
			qos.Append("</qos>");

			return this.Subscribe( key, qos.ToString() );
		}

		public string Subscribe( string key, string qos )
		{
			string me="Subscribe()";
			logger.Debug(me);

			/* key exemples:
			 *  exact :
			 *	<key oid='MyMessage' />
			 * xpath :
			 *	<key oid='' queryType='XPATH'> /xmlBlaster/key[starts-with(@oid,'radar.')] </key>
			 */
			try
			{
				string result = xmlBlasterClientProxy.Subscribe( this.sessionId, key, qos.ToString() );

				XmlBlasterLib.QosSubscribeResult qsr = new QosSubscribeResult( result );

				return qsr.SubscribeId ;
			}
			catch( Exception ex )
			{
				XmlBlasterException.HandleException(ex);
				return null ;
			}
		}


		public bool Unsubscribe( string subscribtionId )
		{
			string me="Unsubscribe()";
			logger.Debug( me );

			try
			{
				string key = "<key oid=\""+subscribtionId+"\" />\n" ;
				QosUnsubscribe qos = new QosUnsubscribe();

				string[] results = xmlBlasterClientProxy.Unsubscribe( this.sessionId, key, qos.ToString() );

				logger.Debug( "{0} results count={1}", me, results.Length );
				foreach( string s in results )
				{
					XmlBlasterLib.QosUnsubscribeResult qur = new QosUnsubscribeResult( s );
					logger.Debug( qur.ToString() );
				}
			}
			catch( Exception ex )
			{
				XmlBlasterException.HandleException(ex);
				return false ;
			}

			return true ;
		}


		public bool Publish( string topic, string xmlContent )
		{
			string me="Publish()";
			logger.Debug(me);

			string key = "<key oid=\""+topic+"\" contentMime=\"text/xml\" >\n" ;
			//key += "<sender>"+uniqueId+"</sender>\n" ;
			key += "</key>" ;

			string qos = "<qos />" ;

			return this.Publish( key, qos, xmlContent );
		}

		public bool Publish( string key, string qos, string xmlContent )
		{
			string me="Publish";
			logger.Debug(me);
			try
			{
				string result = xmlBlasterClientProxy.Publish( this.sessionId, key, xmlContent, qos );

				// TODO: Publish Qos Result
				return true ;
			}
			catch( Exception ex )
			{
				XmlBlasterException.HandleException(ex);
			}
			return false ;
		}


		public MessageUnit[] Get( string query, bool xpath )
		{
			string me="Get()";
			logger.Debug(me);

			string key ;
			if( xpath )
			{
				// exemple: <key oid='' queryType='XPATH'> /xmlBlaster/key[starts-with(@oid,'radar.')] </key>
				key = "<key oid='' queryType='XPATH'>"+query+"</key>";
			}
			else
			{
				key = "<key oid=\""+query+"\" />\n" ;
			}
			string qos = "<qos><content>true</content></qos>";

			return this.Get( key, qos );
		}

		public MessageUnit[] Get( string key, string qos )
		{
			string me="Get()";
			logger.Debug(me);
			MessageUnit[] msgs = null ;
			try
			{
				object[][] results = xmlBlasterClientProxy.Get( this.sessionId, key, qos );

				int nbrMsgs = results.Length ;
				logger.Debug("{0} nbrMsgs= {1}", me, nbrMsgs);
				msgs = new MessageUnit[nbrMsgs];
				for( int i=0; i<nbrMsgs; i++ )
				{
					try
					{
						msgs[i] = new MessageUnit( (string) results[i][0], (byte[]) results[i][1], (string) results[i][2] );
					}
					catch(Exception ex)
					{
						XmlBlasterException.HandleException( new Exception( me+" received a malformed message. Ex: "+ex.Message, ex ) );
					}
				}

			}
			catch( Exception ex )
			{
				XmlBlasterException.HandleException(ex);
			}
			return msgs ;
		}


		public bool Erase( string query, bool xpath )
		{
			string me="Erase()";
			logger.Debug(me);

			string key ;
			if( xpath )
			{
				// exemple: <key oid='' queryType='XPATH'> /xmlBlaster/key[starts-with(@oid,'radar.')] </key>
				key = "<key oid='' queryType='XPATH'>"+query+"</key>";
			}
			else
			{
				key = "<key oid=\""+query+"\" />\n" ;
			}

			string qos = "<qos />" ;

			return this.Erase( key, qos );
		}

		public bool Erase( string key, string qos )
		{
			string me="Erase()";
			logger.Debug(me);
			try
			{
				string[] results = xmlBlasterClientProxy.Erase( this.sessionId, key, qos );

				if( logger.IsDebug )
				{
					logger.Debug( "{0} Results:", me);
					foreach( string s in results )
					{
						logger.Debug( "\tEraseQosResult: {0}", s);
					}
				}

				// TODO: Erase Qos Result
				return true ;
			}
			catch( Exception ex )
			{
				XmlBlasterException.HandleException(ex);
			}
			return false ;
		}

		/// <summary>
		/// L'interface IXmlBlasterClient représente les accès au serveur XmlBlaster
		/// via XmlRpc (CookComputing.XmlRpc).
		/// @see XmlBlasterClient.xmlBlasterClientProxy
		/// </summary>
		internal interface IXmlBlasterClient
		{
			[XmlRpcMethod("authenticate.connect")]
			string Connect( string connectQos );

			[XmlRpcMethod("authenticate.disconnect")]
			void Disconnect( string sessiondId, string qos );

			[XmlRpcMethod("xmlBlaster.publish")]
			string Publish( string sessiondId, string key, string xmlMessage, string qos );

			[XmlRpcMethod("xmlBlaster.subscribe")]
			string Subscribe( string sessiondId, string key, string qos );

			[XmlRpcMethod("xmlBlaster.unSubscribe")]
			string[] Unsubscribe( string sessiondId, string key, string qos );

			[XmlRpcMethod("xmlBlaster.get")]
			object[][] Get( string sessiondId, string key, string qos );

			[XmlRpcMethod("xmlBlaster.erase")]
			string[] Erase( string sessiondId, string key, string qos );

		}

	}

	/// <summary>
	/// La class XmlBlasterCallback représente les méthodes que nous exposons à l'extérieur via XmlRpc.
	/// C'est le serveur XmlBlaster qui appellera ces méthodes.
	/// Nous lui avons fourni notre adresse d'écoute au moment du Connect().
	/// </summary>
	public class XmlBlasterCallback : MarshalByRefObject 
	{
		// TODO: Ajouter un identifiant de thread ou autre
		static SimpleLog logger = SimpleLogLib.SimpleLogManager.GetLog("XmlBlasterCallback", LogLevel.Debug );

		//public delegate void MessageArrivedDelegate( string key, string xmlMessage, string sender, string subscribtionId );
		public delegate void MessageArrivedDelegate( XmlBlasterLib.MessageUnit msgUnit );
		public static /*event*/ MessageArrivedDelegate messageArrived ;

		//public delegate void PingArrivedDelegate();
		//public static PingArrivedDelegate pingArrived ;

		internal static int pingLastTime = 0 ;
		internal static int pingInterval = 0 ;

		public enum XmlBlasterServerHealthStatus
		{
			VERYGOOD, GOOD, BAD, VERY_BAD, DEAD
		}

		public static XmlBlasterServerHealthStatus XmlBlasterServerHealth
		{
			get
			{
				if( pingLastTime == 0 )
				{
					return XmlBlasterServerHealthStatus.DEAD ;
				}
				int lap = Environment.TickCount - pingLastTime ;
				if( lap <= pingInterval/2 )
				{
					return XmlBlasterServerHealthStatus.VERYGOOD ;
				}
				else if( lap <= pingInterval )
				{
					return XmlBlasterServerHealthStatus.GOOD ;
				}
				else if( lap <= (pingInterval+pingInterval/2) )
				{
					return XmlBlasterServerHealthStatus.BAD ;
				}
				else if( lap <= (pingInterval*2) )
				{
					return XmlBlasterServerHealthStatus.VERY_BAD ;
				}
				return XmlBlasterServerHealthStatus.DEAD ;
			}
		}


		[XmlRpcMethod("ping")] 
		public string Ping( string msg ) 
		{
			// XML-RPC message :
			// [12-Nov-2005 11:18:15] string(144) "<?xml version="1.0" encoding="ISO-8859-1"?><methodCall><methodName>ping</methodName><params><param><value></value></param></params></methodCall>"

			logger.Debug( "Ping()" );

			//logger.Debug( "\t msg: ", msg );

			// refresh last server heartbeat
			pingLastTime = Environment.TickCount ;

			//if( XmlBlasterCallback.pingArrived != null )
			//{
			//	//XmlBlasterCallback.pingArrived();
			//	// Asynchronously invoke the method.
			//	IAsyncResult ar = XmlBlasterCallback.pingArrived.BeginInvoke( null, null );
			//}

			return "<qos><state>OK</state></qos>" ;
		}


		[XmlRpcMethod("update")] 
		public string Update( string cbSessionId, string key, System.Byte[] content, string qos ) 
		{
			string me="Update()";
			logger.Debug(me);

			// refresh last server heartbeat
			pingLastTime = Environment.TickCount ;

			// Note (from requirements/interface.update.html)
			//
			// Be prepared to receive all sorts of messages on update,
			// like normal messages or internal xmlBlaster messages or administrative command messages.
			//

			/*
				XmlBlasterCallback.Update() cbSessionId: 
					unknown
				XmlBlasterCallback.Update() key: 
					<key oid='__sys__UserList'>
						<__sys__internal/>
					</key>
				XmlBlasterCallback.Update() qos: 
					<qos>
						<sender>/node/xmlBlaster_127_0_0_1_3412/client/__RequestBroker_internal[xmlBlaster_127_0_0_1_3412]/1</sender>
						<subscribe id='__subId:xmlBlaster_127_0_0_1_3412-XPATH1132587264334000000'/>
						<expiration lifeTime='-1'/>
						<rcvTimestamp nanos='1132587264254000001'/>
						<queue index='0' size='3'/>
						<forceUpdate/>
						<isPublish/>
					</qos>
				*/

			/*
			logger.Debug( "vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv" );
			logger.Debug( "{0} cbSessionId: {1}", me, cbSessionId ); // SecretSessionId, so should be 'Unknow'.
			logger.Debug( "{0} key:\n{1}", me, key );
			logger.Debug( "{0} qos:\n{1}", me, qos );
			logger.Debug( "^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^" );
			*/


			if( XmlBlasterCallback.messageArrived != null )
			{
				//string xml = Encoding.Default.GetString( msg );
				//XmlBlasterCallback.messageArrived( key, xml, qosUpdate.Sender, qosUpdate.SubscribeId );
				MessageUnit msg ;
				try
				{
					msg = new MessageUnit( key, content, qos );

					try
					{
						//XmlBlasterCallback.messageArrived( msg );

						//logger.Debug( "{0} InvocationList: {1}", me, XmlBlasterCallback.messageArrived.GetInvocationList().Length );
						// Asynchronously invoke the method.
						IAsyncResult ar = XmlBlasterCallback.messageArrived.BeginInvoke( msg, null, null );
						//XmlBlasterCallback.messageArrived.EndInvoke(ar);
					}
					catch( Exception ex )
					{
						logger.Error( "{0} Failed to fire messageArrived. Ex: {1}", me, ex.Message );
					}
				}
				catch( Exception ex )
				{
					logger.Error( "{0} Received a malformed message. Ex: {1}", me, ex.Message );
				}
			}

			return "<qos><state>OK</state></qos>";
		}
	}


	/// <summary>
	/// Transforme toutes les exceptions catchée dans le client XmlBlaster
	/// en exception du type XmlBlasterException afin de clarifier les erreurs.
	/// Le code utilisateur de XmlBlasterClient ne verra que des exceptions de ce type.
	/// </summary>
	public class XmlBlasterException : Exception
	{
		const string exceptionMessage = "XmlBlaster operation has failed.";

		internal XmlBlasterException()
			: base( exceptionMessage )
		{
		}

		internal XmlBlasterException( string auxMessage )
			: base( String.Format( "{0} - {1}", exceptionMessage, auxMessage ) )
		{
		}

		internal XmlBlasterException( string auxMessage, Exception inner )
			: base( String.Format( "{0} - {1}", exceptionMessage, auxMessage ), inner )
		{
		}

		internal XmlBlasterException( Exception inner )
			: base( String.Format( "{0} - {1}", exceptionMessage, inner.Message ), inner )
		{
		}

		protected static SimpleLog logger = SimpleLogLib.SimpleLogManager.GetLog("XmlBlasterException");

		/// <summary>
		/// Pour transformer toutes les exceptions en XmlBlasterException
		/// </summary>
		/// <param name="ex"></param>
		internal static void HandleException( Exception ex )
		{
			logger.Error( "HandleException() {0}", ex.Message );
			logger.Error( "HandleException() {0}", ex.StackTrace );

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


	public class MessageUnit
	{
		static SimpleLog logger = SimpleLogLib.SimpleLogManager.GetLog("XmlBlaster.MessageUnit", LogLevel.Debug );

		//string xml = Encoding.Default.GetString( msg );

		public MessageUnit( string key, byte[] content, string qos )
		{
			string me="MessageUnit()";
			logger.Debug("{0} message key: {1}", me, key );

			//this.key = new MessageKey( key );
			this.keyStr = key ;
			this.contentBytes = content ;
			this.qos = new QosMessageUnit( qos );
		}


		QosMessageUnit qos ;
		public QosMessageUnit Qos
		{
			get
			{
				return this.qos ;
			}
		}


		byte[] contentBytes ;
		string contentStr ;
		public string ContentStr
		{
			get
			{
				if( this.contentStr == null )
				{
					this.contentStr = Encoding.Default.GetString( this.contentBytes );
				}
				return this.contentStr ;
			}
		}
		public byte[] ContentBytes
		{
			get
			{
				return this.contentBytes ;
			}
		}


		string keyStr ;
		XmlDocument keyXmlDoc ;
		public string KeyStr
		{
			get
			{
				return this.keyStr ;
			}
		}
		public string KeyOid
		{
			get 
			{
				//return this.key.Oid ;
				if( this.keyStr != null )
				{
					try
					{
						if( this.keyXmlDoc == null )
						{
							this.keyXmlDoc = new XmlDocument();
							this.keyXmlDoc.LoadXml( this.keyStr );
						}
						//XmlNode node = this.keyXmlDoc.DocumentElement.SelectSingleNode("key");
						//return (string) node.Attributes[ "oid" ].Value ;
						return this.keyXmlDoc.DocumentElement.Attributes[ "oid" ].Value ;
					}
					catch(Exception ex )
					{
						logger.Error( "MessageUnit.KeyOid() Failed. Ex: "+ex.Message );
					}
					return null ;
				}
				return null ;
			}
		}

		public string KeyQuery
		{
			get
			{
				if( this.keyStr != null )
				{
					try
					{
						if( this.keyXmlDoc == null )
						{
							this.keyXmlDoc = new XmlDocument();
							this.keyXmlDoc.LoadXml( this.keyStr );
						}
						return this.keyXmlDoc.DocumentElement.InnerText.ToString();
					}
					catch(Exception ex )
					{
						logger.Error( "MessageUnit.KeyOid() Failed. Ex: "+ex.Message );
					}
					return null ;
				}
				return null ;
			}
		}

	}

	#region Les Qos (QosConnect, QosUpdate, ...

	public class QosConnect
	{
		const int DEFAULT_PINGINTERVAL = 5000 ;

		static SimpleLog logger = XmlBlasterClient.loggerQos ;


		public string securityService_type = "htpasswd" ;
		public string securityService_version = "1.0" ;
		public string username ;
		public string password ;
		public string callbackUri = null ;

		Hashtable options ;

		public int PingInterval 
		{
			get
			{
				try{ return Convert.ToInt32( this.options["pingInterval"] ); }catch{}
				return 0 ;
			}
		}

		internal QosConnect()
		{
			// Setting default options
			options = new Hashtable();
			options.Add( "pingInterval", DEFAULT_PINGINTERVAL );
		}
		public QosConnect( string username, string password )
			: this()
		{
			this.username = username ;
			this.password = password ;
			this.callbackUri = null ;
		}
		public QosConnect( string username, string password, string callbackUri )
			: this()
		{
			this.username = username ;
			this.password = password ;
			this.callbackUri = callbackUri ;
		}

		public QosConnect( string username, string password, string callbackUri, Hashtable options )
			: this()
		{
			this.username = username ;
			this.password = password ;
			this.callbackUri = callbackUri ;

			// Options Merge: user overload or add options
			try
			{
				foreach( object key in options )
				{
					if( this.options.ContainsKey( key ) )
					{
						this.options[ key ] = options[ key ];
					}
					else
					{
						this.options.Add( key, options[ key ] );
					}
				}
			}
			catch( Exception ex )
			{
				logger.Warn("Failed to understand options. Ex: "+ex.Message );
			}
		}

		public override string ToString()
		{
			string me="ToString()";

			StringBuilder sb = new StringBuilder( 255 );

			sb.Append( "<qos>\n" );

			sb.Append( " <securityService type=\""+this.securityService_type + "\" version=\""+this.securityService_version+"\">\n" );
			sb.Append( "  <user>"+ this.username +"</user>\n" );
			sb.Append( "  <passwd>"+ this.password +"</passwd>\n" );
			sb.Append( " </securityService>\n" );

			if( callbackUri != null )
			{
				sb.Append( "<callback type=\"XMLRPC\" retries=\"2\" delay=\"2000\" pingInterval=\""+this.options["pingInterval"].ToString()+"\" >" );
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

			string s = sb.ToString() ;
			logger.Debug("{0} XML:\n{1}", me, s );
			return s ;
		}

	}

	public class QosConnectResult
	{
		static SimpleLog logger = XmlBlasterClient.loggerQos ;

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
			logger.Debug("Create with XML:\n{0}", xml );

			this.xmlDoc = new XmlDocument();
			xmlDoc.LoadXml( xml );
		}

		public string SessionId
		{
			get 
			{
				string s = null ;
				try
				{
					XmlNode root = xmlDoc.DocumentElement ;
					XmlNode node ;
					//node = root.SelectSingleNode("descendant::book[author/last-name='Austen']");
					node = root.SelectSingleNode("session");
					s = node.Attributes[ "sessionId" ].Value ;
				}
				catch( Exception ex )
				{
					logger.Error("Failed to retreive SessionId. Ex: {0}", ex.Message );
				}
				logger.Debug("sessionId : {0}", s );
				return s ;
			}
		}
	}

	public class QosSubscribeResult : XmlDocument
	{
		static SimpleLog logger = XmlBlasterClient.loggerQos ;

		/*
			<qos>
			<subscribe id='__subId:xmlBlaster_127_0_0_1_3412-XPATH1132586655128000000'/>
			<isSubscribe/>
			</qos>
		*/
		protected string subscribeId ;
		public QosSubscribeResult( string xml )
		{
			logger.Debug("Create with XML:\n{0}", xml );

			this.LoadXml( xml );
		}
		public string SubscribeId
		{
			get 
			{
				if( subscribeId == null )
				{
					XmlNode root = this.DocumentElement ;
					XmlNode node = root.SelectSingleNode("subscribe");
					this.subscribeId = node.Attributes[ "id" ].Value ;
				}
				logger.Debug("QosSubscribeResult.SubscribeId : "+this.subscribeId);
				return this.subscribeId ;
			}
		}
	}

	public class QosUnsubscribe : XmlDocument
	{
		static SimpleLog logger = XmlBlasterClient.loggerQos ;

		public QosUnsubscribe()
		{
			string xml = "<qos/>";
			logger.Debug("Create with XML:\n{0}", xml );
			this.LoadXml( xml );
		}
		public override string ToString()
		{
			return base.OuterXml ;
		}
	}

	public class QosUnsubscribeResult : XmlDocument
	{
		static SimpleLog logger = XmlBlasterClient.loggerQos ;

		public QosUnsubscribeResult( string xml )
		{
			logger.Debug("Create with XML:\n{0}", xml );
			this.LoadXml( xml );
		}
		public override string ToString()
		{
			return base.OuterXml ;
		}
	}

	public class QosUpdate : XmlDocument
	{
		static SimpleLog logger = XmlBlasterClient.loggerQos ;
		/*
		XmlBlasterCallback.Update() cbSessionId: 
			unknown
		XmlBlasterCallback.Update() key: 
			<key oid='__sys__UserList'>
				<__sys__internal/>
			</key>
		XmlBlasterCallback.Update() qos: 
			<qos>
				<sender>/node/xmlBlaster_127_0_0_1_3412/client/__RequestBroker_internal[xmlBlaster_127_0_0_1_3412]/1</sender>
				<subscribe id='__subId:xmlBlaster_127_0_0_1_3412-XPATH1132587264334000000'/>
				<expiration lifeTime='-1'/>
				<rcvTimestamp nanos='1132587264254000001'/>
				<queue index='0' size='3'/>
				<forceUpdate/>
				<isPublish/>
			</qos>
		*/
		protected string sender ;
		protected string subscribeId ;

		public QosUpdate( string xml )
		{
			logger.Debug("Create with XML:\n{0}", xml );
			this.LoadXml( xml );
		}

		public string SubscribeId
		{
			get 
			{
				if( subscribeId == null )
				{
					XmlNode node = this.DocumentElement.SelectSingleNode("subscribe");
					this.subscribeId = node.Attributes[ "id" ].Value ;
				}
				logger.Debug("QosUpdate.SubscribeId: {0}", this.subscribeId );
				return this.subscribeId ;
			}
		}
		public string Sender
		{
			get 
			{
				if( sender == null )
				{
					XmlNode node = this.DocumentElement.SelectSingleNode("sender");
					this.sender = node.InnerText ;
				}
				logger.Debug("QosUpdate.Sender: {0} ", this.sender );
				return sender ;
			}
		}
	}
	public class QosErase
	{
		static SimpleLog logger = XmlBlasterClient.loggerQos ;

		bool forceDestroy = false ;
		public bool ForceDestroy
		{
			get
			{
				return this.forceDestroy ;
			}
			set 
			{
				this.forceDestroy = value ;
			}
		}
		int numEntries = -1 ;
		public int NumEntries 
		{
			get
			{
				return this.numEntries ;
			}
			set
			{
				this.numEntries = value ;
			}
		}
		public QosErase()
		{
		}
		public override string ToString()
		{
			StringBuilder sb = new StringBuilder( 256 );
			sb.Append( "<qos>\n" );
			sb.Append( "<erase forceDestroy=\""+(forceDestroy?"true":"false")+"\" >" );
			sb.Append( "<history numEntries=\""+numEntries.ToString()+"\" />" );
			sb.Append( "</erase>" );
			sb.Append( "</qos>" );

			if( logger.IsDebug )
			{
				logger.Debug("QosErase.ToString() XML:\n{0}", sb.ToString() );
			}
			return sb.ToString() ;
		}
	}

	public class QosMessageUnit : XmlDocument
	{
		static SimpleLog logger = XmlBlasterClient.loggerQos ;

		/*
		XmlBlasterCallback.Update() cbSessionId: 
			unknown
		XmlBlasterCallback.Update() key: 
			<key oid='__sys__UserList'>
				<__sys__internal/>
			</key>
		XmlBlasterCallback.Update() qos: 
			<qos>
				<sender>/node/xmlBlaster_127_0_0_1_3412/client/__RequestBroker_internal[xmlBlaster_127_0_0_1_3412]/1</sender>
				<subscribe id='__subId:xmlBlaster_127_0_0_1_3412-XPATH1132587264334000000'/>
				<expiration lifeTime='-1'/>
				<rcvTimestamp nanos='1132587264254000001'/>
				<queue index='0' size='3'/>
				<forceUpdate/>
				<isPublish/>
			</qos>
		*/

		protected string sender ;
		protected string subscribeId ;
		public QosMessageUnit( string xml )
		{
			string me="QosMessageUnit.QosMessageUnit()";
			logger.Debug("{0} XML:\n{1}", me, xml );

			this.LoadXml( xml );
		}

		public string SubscribeId
		{
			get 
			{
				if( subscribeId == null )
				{
					XmlNode node = this.DocumentElement.SelectSingleNode("subscribe");
					this.subscribeId = node.Attributes[ "id" ].Value ;
				}
				logger.Debug("QosMessageUnit.SubscribeId: {0}", this.subscribeId );
				return this.subscribeId ;
			}
		}
		public string Sender
		{
			get 
			{
				if( sender == null )
				{
					XmlNode node = this.DocumentElement.SelectSingleNode("sender");
					this.sender = node.InnerText ;
				}
				logger.Debug("QosMessageUnit.Sender: {0} ", this.sender );
				return sender ;
			}
		}
	}


	#endregion

}
