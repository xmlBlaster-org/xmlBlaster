using System;
using System.Drawing;
using System.Collections;
using System.ComponentModel;
using System.Windows.Forms;
using System.Data;
using System.Runtime.InteropServices; // DllImport pour autoscroll du richtextbox
using System.Threading ;
using System.Text ;

using XmlBlasterLib ;
using SimpleLogLib ;

namespace XmlBlasterMessagesManager
{
	/// <summary>
	/// Description résumée de XmlBlasterMessagesManagerFrm.
	/// </summary>
	public class XmlBlasterMessagesManagerFrm : System.Windows.Forms.Form
	{
		#region Code généré par le Concepteur Windows Form

		private System.Windows.Forms.GroupBox groupBox1;
		private System.Windows.Forms.Button connect;
		private System.Windows.Forms.Label label3;
		private System.Windows.Forms.TextBox password;
		private System.Windows.Forms.Label label2;
		private System.Windows.Forms.Label label1;
		private System.Windows.Forms.TextBox server;
		private System.Windows.Forms.TextBox user;
		private System.Windows.Forms.Button getMessages;
		private System.Windows.Forms.TreeView messages;
		private System.Windows.Forms.GroupBox groupBox2;
		private System.Windows.Forms.GroupBox groupBox3;
		private System.Windows.Forms.Label label4;
		private System.Windows.Forms.Label label5;
		private System.Windows.Forms.Label label6;
		private System.Windows.Forms.TextBox msgsend_key;
		private System.Windows.Forms.TextBox msgsend_qos;
		private System.Windows.Forms.TextBox msgsend_content;
		private System.Windows.Forms.Button msgsend_send;
		private System.Windows.Forms.Button msgsend_clear;
		private System.Windows.Forms.Label label8;
		private System.Windows.Forms.Label label9;
		private System.Windows.Forms.TextBox msgview_key;
		private System.Windows.Forms.TextBox msgview_qos;
		private System.Windows.Forms.TextBox msgview_content;
		private System.Windows.Forms.Button msgsend_sendTo;
		private System.Windows.Forms.Button msgsend_copySelected;
		private System.Windows.Forms.RichTextBox report;
		private System.Windows.Forms.ListBox users;
		private System.Windows.Forms.ToolTip message_toolTip;
		private System.Windows.Forms.Panel connectStatusLed;
		private System.Windows.Forms.Timer connectStatusTimer;
		private System.Windows.Forms.Button msgview_erase;
		private System.ComponentModel.IContainer components;

		/// <summary>
		/// Méthode requise pour la prise en charge du concepteur - ne modifiez pas
		/// le contenu de cette méthode avec l'éditeur de code.
		/// </summary>
		private void InitializeComponent()
		{
			this.components = new System.ComponentModel.Container();
			this.groupBox1 = new System.Windows.Forms.GroupBox();
			this.connect = new System.Windows.Forms.Button();
			this.server = new System.Windows.Forms.TextBox();
			this.label3 = new System.Windows.Forms.Label();
			this.password = new System.Windows.Forms.TextBox();
			this.label2 = new System.Windows.Forms.Label();
			this.user = new System.Windows.Forms.TextBox();
			this.label1 = new System.Windows.Forms.Label();
			this.messages = new System.Windows.Forms.TreeView();
			this.getMessages = new System.Windows.Forms.Button();
			this.groupBox2 = new System.Windows.Forms.GroupBox();
			this.msgview_content = new System.Windows.Forms.TextBox();
			this.msgview_qos = new System.Windows.Forms.TextBox();
			this.msgview_key = new System.Windows.Forms.TextBox();
			this.label8 = new System.Windows.Forms.Label();
			this.label9 = new System.Windows.Forms.Label();
			this.groupBox3 = new System.Windows.Forms.GroupBox();
			this.msgsend_copySelected = new System.Windows.Forms.Button();
			this.msgsend_sendTo = new System.Windows.Forms.Button();
			this.users = new System.Windows.Forms.ListBox();
			this.msgsend_clear = new System.Windows.Forms.Button();
			this.msgsend_send = new System.Windows.Forms.Button();
			this.msgsend_content = new System.Windows.Forms.TextBox();
			this.msgsend_qos = new System.Windows.Forms.TextBox();
			this.msgsend_key = new System.Windows.Forms.TextBox();
			this.label6 = new System.Windows.Forms.Label();
			this.label5 = new System.Windows.Forms.Label();
			this.label4 = new System.Windows.Forms.Label();
			this.report = new System.Windows.Forms.RichTextBox();
			this.message_toolTip = new System.Windows.Forms.ToolTip(this.components);
			this.connectStatusLed = new System.Windows.Forms.Panel();
			this.connectStatusTimer = new System.Windows.Forms.Timer(this.components);
			this.msgview_erase = new System.Windows.Forms.Button();
			this.groupBox1.SuspendLayout();
			this.groupBox2.SuspendLayout();
			this.groupBox3.SuspendLayout();
			this.SuspendLayout();
			// 
			// groupBox1
			// 
			this.groupBox1.Controls.Add(this.connectStatusLed);
			this.groupBox1.Controls.Add(this.connect);
			this.groupBox1.Controls.Add(this.server);
			this.groupBox1.Controls.Add(this.label3);
			this.groupBox1.Controls.Add(this.password);
			this.groupBox1.Controls.Add(this.label2);
			this.groupBox1.Controls.Add(this.user);
			this.groupBox1.Controls.Add(this.label1);
			this.groupBox1.Location = new System.Drawing.Point(8, 4);
			this.groupBox1.Name = "groupBox1";
			this.groupBox1.Size = new System.Drawing.Size(548, 40);
			this.groupBox1.TabIndex = 2;
			this.groupBox1.TabStop = false;
			// 
			// connect
			// 
			this.connect.Location = new System.Drawing.Point(404, 12);
			this.connect.Name = "connect";
			this.connect.Size = new System.Drawing.Size(84, 20);
			this.connect.TabIndex = 6;
			this.connect.Text = "Connect";
			this.connect.Click += new System.EventHandler(this.connect_Click);
			// 
			// server
			// 
			this.server.Location = new System.Drawing.Point(44, 12);
			this.server.Name = "server";
			this.server.Size = new System.Drawing.Size(84, 20);
			this.server.TabIndex = 5;
			this.server.Text = "127.0.0.1";
			// 
			// label3
			// 
			this.label3.AutoSize = true;
			this.label3.Location = new System.Drawing.Point(8, 16);
			this.label3.Name = "label3";
			this.label3.Size = new System.Drawing.Size(36, 16);
			this.label3.TabIndex = 4;
			this.label3.Text = "server";
			// 
			// password
			// 
			this.password.Location = new System.Drawing.Point(308, 12);
			this.password.Name = "password";
			this.password.Size = new System.Drawing.Size(84, 20);
			this.password.TabIndex = 3;
			this.password.Text = "guest";
			// 
			// label2
			// 
			this.label2.AutoSize = true;
			this.label2.Location = new System.Drawing.Point(256, 16);
			this.label2.Name = "label2";
			this.label2.Size = new System.Drawing.Size(53, 16);
			this.label2.TabIndex = 2;
			this.label2.Text = "password";
			// 
			// user
			// 
			this.user.Location = new System.Drawing.Point(164, 12);
			this.user.Name = "user";
			this.user.Size = new System.Drawing.Size(84, 20);
			this.user.TabIndex = 1;
			this.user.Text = "xbmMsgMng";
			// 
			// label1
			// 
			this.label1.AutoSize = true;
			this.label1.Location = new System.Drawing.Point(136, 16);
			this.label1.Name = "label1";
			this.label1.Size = new System.Drawing.Size(26, 16);
			this.label1.TabIndex = 0;
			this.label1.Text = "user";
			// 
			// messages
			// 
			this.messages.HideSelection = false;
			this.messages.ImageIndex = -1;
			this.messages.Location = new System.Drawing.Point(8, 56);
			this.messages.Name = "messages";
			this.messages.SelectedImageIndex = -1;
			this.messages.Size = new System.Drawing.Size(216, 172);
			this.messages.TabIndex = 3;
			this.messages.AfterSelect += new System.Windows.Forms.TreeViewEventHandler(this.messages_AfterSelect);
			// 
			// getMessages
			// 
			this.getMessages.Location = new System.Drawing.Point(64, 236);
			this.getMessages.Name = "getMessages";
			this.getMessages.Size = new System.Drawing.Size(112, 24);
			this.getMessages.TabIndex = 4;
			this.getMessages.Text = "Get Messages";
			this.getMessages.Click += new System.EventHandler(this.getMessages_Click);
			// 
			// groupBox2
			// 
			this.groupBox2.Controls.Add(this.msgview_erase);
			this.groupBox2.Controls.Add(this.msgview_content);
			this.groupBox2.Controls.Add(this.msgview_qos);
			this.groupBox2.Controls.Add(this.msgview_key);
			this.groupBox2.Controls.Add(this.label8);
			this.groupBox2.Controls.Add(this.label9);
			this.groupBox2.Location = new System.Drawing.Point(228, 48);
			this.groupBox2.Name = "groupBox2";
			this.groupBox2.Size = new System.Drawing.Size(328, 228);
			this.groupBox2.TabIndex = 5;
			this.groupBox2.TabStop = false;
			this.groupBox2.Text = " selected message ";
			// 
			// msgview_content
			// 
			this.msgview_content.AcceptsReturn = true;
			this.msgview_content.AcceptsTab = true;
			this.msgview_content.Location = new System.Drawing.Point(8, 104);
			this.msgview_content.Multiline = true;
			this.msgview_content.Name = "msgview_content";
			this.msgview_content.ReadOnly = true;
			this.msgview_content.ScrollBars = System.Windows.Forms.ScrollBars.Both;
			this.msgview_content.Size = new System.Drawing.Size(312, 88);
			this.msgview_content.TabIndex = 8;
			this.msgview_content.Text = "msgview_content";
			// 
			// msgview_qos
			// 
			this.msgview_qos.AcceptsReturn = true;
			this.msgview_qos.AcceptsTab = true;
			this.msgview_qos.Location = new System.Drawing.Point(32, 52);
			this.msgview_qos.Multiline = true;
			this.msgview_qos.Name = "msgview_qos";
			this.msgview_qos.ReadOnly = true;
			this.msgview_qos.ScrollBars = System.Windows.Forms.ScrollBars.Both;
			this.msgview_qos.Size = new System.Drawing.Size(288, 48);
			this.msgview_qos.TabIndex = 7;
			this.msgview_qos.Text = "msgview_qos";
			// 
			// msgview_key
			// 
			this.msgview_key.AcceptsReturn = true;
			this.msgview_key.AcceptsTab = true;
			this.msgview_key.Location = new System.Drawing.Point(32, 16);
			this.msgview_key.Multiline = true;
			this.msgview_key.Name = "msgview_key";
			this.msgview_key.ReadOnly = true;
			this.msgview_key.ScrollBars = System.Windows.Forms.ScrollBars.Both;
			this.msgview_key.Size = new System.Drawing.Size(288, 32);
			this.msgview_key.TabIndex = 6;
			this.msgview_key.Text = "msgview_key";
			this.message_toolTip.SetToolTip(this.msgview_key, "coucou");
			// 
			// label8
			// 
			this.label8.AutoSize = true;
			this.label8.Location = new System.Drawing.Point(8, 52);
			this.label8.Name = "label8";
			this.label8.Size = new System.Drawing.Size(22, 16);
			this.label8.TabIndex = 4;
			this.label8.Text = "qos";
			// 
			// label9
			// 
			this.label9.AutoSize = true;
			this.label9.Location = new System.Drawing.Point(8, 16);
			this.label9.Name = "label9";
			this.label9.Size = new System.Drawing.Size(22, 16);
			this.label9.TabIndex = 3;
			this.label9.Text = "key";
			// 
			// groupBox3
			// 
			this.groupBox3.Controls.Add(this.msgsend_copySelected);
			this.groupBox3.Controls.Add(this.msgsend_sendTo);
			this.groupBox3.Controls.Add(this.users);
			this.groupBox3.Controls.Add(this.msgsend_clear);
			this.groupBox3.Controls.Add(this.msgsend_send);
			this.groupBox3.Controls.Add(this.msgsend_content);
			this.groupBox3.Controls.Add(this.msgsend_qos);
			this.groupBox3.Controls.Add(this.msgsend_key);
			this.groupBox3.Controls.Add(this.label6);
			this.groupBox3.Controls.Add(this.label5);
			this.groupBox3.Controls.Add(this.label4);
			this.groupBox3.Location = new System.Drawing.Point(4, 276);
			this.groupBox3.Name = "groupBox3";
			this.groupBox3.Size = new System.Drawing.Size(552, 220);
			this.groupBox3.TabIndex = 6;
			this.groupBox3.TabStop = false;
			this.groupBox3.Text = " send a message ";
			// 
			// msgsend_copySelected
			// 
			this.msgsend_copySelected.Location = new System.Drawing.Point(100, 188);
			this.msgsend_copySelected.Name = "msgsend_copySelected";
			this.msgsend_copySelected.Size = new System.Drawing.Size(96, 24);
			this.msgsend_copySelected.TabIndex = 10;
			this.msgsend_copySelected.Text = "Copy Selected";
			this.msgsend_copySelected.Click += new System.EventHandler(this.msgsend_copySelected_Click);
			// 
			// msgsend_sendTo
			// 
			this.msgsend_sendTo.Location = new System.Drawing.Point(452, 188);
			this.msgsend_sendTo.Name = "msgsend_sendTo";
			this.msgsend_sendTo.Size = new System.Drawing.Size(68, 24);
			this.msgsend_sendTo.TabIndex = 9;
			this.msgsend_sendTo.Text = "Send To";
			// 
			// users
			// 
			this.users.Location = new System.Drawing.Point(436, 20);
			this.users.Name = "users";
			this.users.Size = new System.Drawing.Size(104, 160);
			this.users.TabIndex = 8;
			// 
			// msgsend_clear
			// 
			this.msgsend_clear.Location = new System.Drawing.Point(204, 188);
			this.msgsend_clear.Name = "msgsend_clear";
			this.msgsend_clear.Size = new System.Drawing.Size(68, 24);
			this.msgsend_clear.TabIndex = 7;
			this.msgsend_clear.Text = "Clear";
			// 
			// msgsend_send
			// 
			this.msgsend_send.Location = new System.Drawing.Point(360, 188);
			this.msgsend_send.Name = "msgsend_send";
			this.msgsend_send.Size = new System.Drawing.Size(68, 24);
			this.msgsend_send.TabIndex = 6;
			this.msgsend_send.Text = "Send";
			this.msgsend_send.Click += new System.EventHandler(this.msgsend_send_Click);
			// 
			// msgsend_content
			// 
			this.msgsend_content.AcceptsReturn = true;
			this.msgsend_content.AcceptsTab = true;
			this.msgsend_content.Location = new System.Drawing.Point(8, 92);
			this.msgsend_content.Multiline = true;
			this.msgsend_content.Name = "msgsend_content";
			this.msgsend_content.ScrollBars = System.Windows.Forms.ScrollBars.Both;
			this.msgsend_content.Size = new System.Drawing.Size(420, 88);
			this.msgsend_content.TabIndex = 5;
			this.msgsend_content.Text = "msgsend_content";
			// 
			// msgsend_qos
			// 
			this.msgsend_qos.AcceptsReturn = true;
			this.msgsend_qos.AcceptsTab = true;
			this.msgsend_qos.Location = new System.Drawing.Point(56, 56);
			this.msgsend_qos.Multiline = true;
			this.msgsend_qos.Name = "msgsend_qos";
			this.msgsend_qos.ScrollBars = System.Windows.Forms.ScrollBars.Both;
			this.msgsend_qos.Size = new System.Drawing.Size(372, 32);
			this.msgsend_qos.TabIndex = 4;
			this.msgsend_qos.Text = "msgsend_qos";
			// 
			// msgsend_key
			// 
			this.msgsend_key.AcceptsReturn = true;
			this.msgsend_key.AcceptsTab = true;
			this.msgsend_key.Location = new System.Drawing.Point(56, 20);
			this.msgsend_key.Multiline = true;
			this.msgsend_key.Name = "msgsend_key";
			this.msgsend_key.ScrollBars = System.Windows.Forms.ScrollBars.Both;
			this.msgsend_key.Size = new System.Drawing.Size(372, 32);
			this.msgsend_key.TabIndex = 3;
			this.msgsend_key.Text = "msgsend_key";
			// 
			// label6
			// 
			this.label6.AutoSize = true;
			this.label6.Location = new System.Drawing.Point(8, 76);
			this.label6.Name = "label6";
			this.label6.Size = new System.Drawing.Size(41, 16);
			this.label6.TabIndex = 2;
			this.label6.Text = "content";
			// 
			// label5
			// 
			this.label5.AutoSize = true;
			this.label5.Location = new System.Drawing.Point(28, 56);
			this.label5.Name = "label5";
			this.label5.Size = new System.Drawing.Size(22, 16);
			this.label5.TabIndex = 1;
			this.label5.Text = "qos";
			// 
			// label4
			// 
			this.label4.AutoSize = true;
			this.label4.Location = new System.Drawing.Point(28, 20);
			this.label4.Name = "label4";
			this.label4.Size = new System.Drawing.Size(22, 16);
			this.label4.TabIndex = 0;
			this.label4.Text = "key";
			// 
			// report
			// 
			this.report.CausesValidation = false;
			this.report.DetectUrls = false;
			this.report.Dock = System.Windows.Forms.DockStyle.Bottom;
			this.report.Location = new System.Drawing.Point(0, 501);
			this.report.Name = "report";
			this.report.ReadOnly = true;
			this.report.Size = new System.Drawing.Size(560, 52);
			this.report.TabIndex = 7;
			this.report.Text = "report";
			this.report.WordWrap = false;
			// 
			// message_toolTip
			// 
			this.message_toolTip.ShowAlways = true;
			// 
			// connectStatusLed
			// 
			this.connectStatusLed.BackColor = System.Drawing.Color.Red;
			this.connectStatusLed.BorderStyle = System.Windows.Forms.BorderStyle.Fixed3D;
			this.connectStatusLed.CausesValidation = false;
			this.connectStatusLed.Cursor = System.Windows.Forms.Cursors.WaitCursor;
			this.connectStatusLed.ForeColor = System.Drawing.Color.Black;
			this.connectStatusLed.Location = new System.Drawing.Point(504, 16);
			this.connectStatusLed.Name = "connectStatusLed";
			this.connectStatusLed.Size = new System.Drawing.Size(24, 12);
			this.connectStatusLed.TabIndex = 7;
			this.message_toolTip.SetToolTip(this.connectStatusLed, "connection LED");
			// 
			// connectStatusTimer
			// 
			this.connectStatusTimer.Enabled = true;
			this.connectStatusTimer.Interval = 2000;
			this.connectStatusTimer.Tick += new System.EventHandler(this.connectStatusTimer_Tick);
			// 
			// msgview_erase
			// 
			this.msgview_erase.Location = new System.Drawing.Point(120, 196);
			this.msgview_erase.Name = "msgview_erase";
			this.msgview_erase.Size = new System.Drawing.Size(72, 24);
			this.msgview_erase.TabIndex = 9;
			this.msgview_erase.Text = "Erase";
			this.msgview_erase.Click += new System.EventHandler(this.msgview_erase_Click);
			// 
			// XmlBlasterMessagesManagerFrm
			// 
			this.AutoScaleBaseSize = new System.Drawing.Size(5, 13);
			this.ClientSize = new System.Drawing.Size(560, 553);
			this.Controls.Add(this.report);
			this.Controls.Add(this.groupBox3);
			this.Controls.Add(this.groupBox2);
			this.Controls.Add(this.getMessages);
			this.Controls.Add(this.messages);
			this.Controls.Add(this.groupBox1);
			this.Name = "XmlBlasterMessagesManagerFrm";
			this.Text = "XmlBlaster Messages Manager";
			this.Closing += new System.ComponentModel.CancelEventHandler(this.XmlBlasterMessagesManagerFrm_Closing);
			this.Load += new System.EventHandler(this.XmlBlasterMessagesManagerFrm_Load);
			this.groupBox1.ResumeLayout(false);
			this.groupBox2.ResumeLayout(false);
			this.groupBox3.ResumeLayout(false);
			this.ResumeLayout(false);

		}
		#endregion

		[STAThread]
		static void Main() 
		{
			Application.Run(new XmlBlasterMessagesManagerFrm());
		}

		static readonly string CRLF = Environment.NewLine ;
		static readonly string cr = new string( '\r', 1 );
		static readonly string lf = new string( '\n', 1 );
		static readonly string crlf = new string( new char[] {'\r','\n'} );

		static SimpleLog logger = SimpleLogManager.GetLog( "Frm", LogLevel.Debug );

		XmlBlasterClient xmlBlaster ;
		int xmlBlaster_port = 8080 ;

		#region richtextbox print error

		[DllImport("user32.dll", EntryPoint="SendMessageA")]
		static extern uint SendMessage(System.IntPtr hwnd, uint wMsg, uint wParam, uint lParam);
		private const int WM_VSCROLL = 0x115;
		private const int SB_BOTTOM = 7;

		public void PrintReport( string msg )
		{
			this.report.AppendText( msg + CRLF );
		}

		/// <summary>
		/// Imprime un message d'erreur.
		/// La mise en couleur fonctionne qu'après un 1er AppendText (voir le Form Load)
		/// Et le scroll auto, faut il vraiment faire l'import et le send message ???
		/// </summary>
		/// <param name="msg"></param>
		public void PrintError( string msg )
		{
			// Change some text properties

			Color fc = this.report.SelectionColor ;
			Font currentFont = this.report.SelectionFont ;
			FontStyle newFontStyle;
			newFontStyle = FontStyle.Bold;
			this.report.SelectionColor = Color.Red ;
			this.report.SelectionFont = new Font(
				currentFont.FontFamily, 
				currentFont.Size, 
				newFontStyle
				);

			this.report.AppendText( "[ERROR] " );

			// restore text properties
			this.report.SelectionColor = fc ;
			this.report.SelectionFont = currentFont ;

			this.report.AppendText( msg + CRLF );

			// auto scroll
			SendMessage(this.report.Handle, WM_VSCROLL, SB_BOTTOM, 0);	
		}

		#endregion

		public XmlBlasterMessagesManagerFrm()
		{
			//
			// Requis pour la prise en charge du Concepteur Windows Forms
			//
			InitializeComponent();

		}

		private void XmlBlasterMessagesManagerFrm_Load(object sender, System.EventArgs e)
		{
			logger.errorCallback += new SimpleLogLib.SimpleLog.ErrorCallbackDelegate( this.PrintError );

			report.Text = "" ;
			report.AppendText( System.Windows.Forms.Application.ProductName
				+ " " + System.Windows.Forms.Application.ProductVersion
				+ CRLF );

			this.msgsend_key.Text = "";
			this.msgview_key.Text ="";
			this.msgsend_qos.Text = "";
			this.msgview_qos.Text ="";
			this.msgsend_content.Text = "";
			this.msgview_content.Text ="";
			this.messages.Nodes.Clear();
			this.users.Items.Clear();

			this.messagesFillDelegate += new MessagesFillDelegate(this.messagesFill);
		}


		protected override void Dispose( bool disposing )
		{
			if( disposing )
			{
				if (components != null) 
				{
					components.Dispose();
				}
			}
			base.Dispose( disposing );
		}

		private void XmlBlasterMessagesManagerFrm_Closing(object sender, System.ComponentModel.CancelEventArgs e)
		{
			try
			{
				this.xmlBlaster.Disconnect();
			}
			catch(Exception ex)
			{
				logger.Error("Failed to Disconnect. Ex: "+ex.Message );
			}
		}


		public void OnMessageArrived( XmlBlasterLib.MessageUnit mu )
		{
			logger.Debug( "OnMessageArrived()" );
			try
			{
				// Appel asynchrone pour repasser sur le thread du Form...
				IAsyncResult ar = this.BeginInvoke( this.messagesFillDelegate, new object[] { new MessageUnit[1] { mu } } );
			}
			catch(Exception ex)
			{
				logger.Debug("OnMessageArrived() Ex: "+ex.Message );
			}
		}


		private void connect_Click(object sender, System.EventArgs e)
		{
			string me="connect_Click()";

			try
			{
				string url = "http://"+this.server.Text+":"+this.xmlBlaster_port.ToString() ;

				if( this.xmlBlaster != null )
				{
					try
					{
						this.xmlBlaster.Disconnect();
						this.xmlBlaster = null ;
					}
					catch( Exception ex )
					{
						logger.Error( "{0} Failed to disconnect before connect. Ex: {1}", me, ex.Message );
					}
				}

				this.xmlBlaster = new XmlBlasterClient();

				// Check if we are already subscribed
				bool alreadySubscribed = false ;
				if( XmlBlasterCallback.messageArrived != null )
				{
					foreach( Delegate d in XmlBlasterCallback.messageArrived.GetInvocationList() )
					{
						if( d.Target == this )
						{
							alreadySubscribed = true ;
						}
					}
				}
				// not, subscribe to messageArrived.
				if( ! alreadySubscribed )
				{
					XmlBlasterCallback.messageArrived += new XmlBlasterCallback.MessageArrivedDelegate( this.OnMessageArrived );
				}
				//XmlBlasterCallback.pingArrived += new XmlBlasterCallback.PingArrivedDelegate( this.OnPingArrived );

				this.xmlBlaster.Connect( url, this.user.Text, this.password.Text );

				this.PrintReport( "OK, connected to url: "+url );

				MessagesViewsClear();

				string query = "/*" ;
				string key = "<key oid='' queryType='XPATH'>"+query+"</key>";

				string qos = "<qos>";
				qos += "<local>true</local>" ; // get our messages
				qos += "<history numEntries='1000' newestFirst='true'/>"; // get all messages for a topic
				qos += "<content>true</content>"; // get messages content
				qos += "</qos>";

				this.xmlBlaster.Subscribe( key, qos );

				this.PrintReport( "OK, subscribtion done with query: "+query );

			}
			catch(Exception ex)
			{
				logger.Error("Failed to Connect. Ex: "+ex.Message );
				this.xmlBlaster = null ;
			}
		}

		private void connectStatusTimer_Tick(object sender, System.EventArgs e)
		{
			switch( XmlBlasterCallback.XmlBlasterServerHealth )
			{
				case XmlBlasterCallback.XmlBlasterServerHealthStatus.VERYGOOD:
					this.connectStatusLed.BackColor = Color.LightGreen ;
					break;
				case XmlBlasterCallback.XmlBlasterServerHealthStatus.GOOD:
					this.connectStatusLed.BackColor = Color.Green ;
					break;
				case XmlBlasterCallback.XmlBlasterServerHealthStatus.BAD:
					this.connectStatusLed.BackColor = Color.Orange ;
					break;
				case XmlBlasterCallback.XmlBlasterServerHealthStatus.VERY_BAD:
					this.connectStatusLed.BackColor = Color.OrangeRed ;
					break;
				case XmlBlasterCallback.XmlBlasterServerHealthStatus.DEAD:
				default:
					this.connectStatusLed.BackColor = Color.Red ;
					break;
			}
		}


		private void msgsend_send_Click(object sender, System.EventArgs e)
		{
			try
			{
				this.xmlBlaster.Publish( this.msgsend_key.Text, this.msgsend_qos.Text, this.msgsend_content.Text );
			}
			catch(Exception ex )
			{
				logger.Error( "Failed to publish message. Ex: "+ex.Message );
			}
		}


		private void getMessages_Click(object sender, System.EventArgs e)
		{
			if( this.xmlBlaster == null )
			{
				MessageBox.Show( "You must connect before operate.", "Not Connected" );
				return ;
			}

			MessagesViewsClear();

			//MessageUnit[] msgs = this.xmlBlaster.Get( "/*", true );

			string query = "/*" ;

			// exemple: <key oid='' queryType='XPATH'> /xmlBlaster/key[starts-with(@oid,'radar.')] </key>
			string key = "<key oid='' queryType='XPATH'>"+query+"</key>";

			string qos = "<qos>";
			qos += "<history numEntries='1000' newestFirst='true'/>";
			qos += "<content>true</content>";
			qos += "</qos>";

			MessageUnit[] msgs = this.xmlBlaster.Get( key, qos );

			messagesFill( msgs );
		}


		private void msgview_erase_Click(object sender, System.EventArgs e)
		{
			MessageTreeNode node = null ;
			try 
			{
				node = (MessageTreeNode) this.messages.SelectedNode ;
			}
			catch{ return; }

			string key = null ;
			string theKey = node.msg.KeyOid ;
			if( theKey==null || theKey==string.Empty )
			{
				theKey = node.msg.KeyQuery ;
				if( theKey==null || theKey==string.Empty )
				{
					logger.Error( "Could not compute the OID for this message. Erase aborted." );
					return ;
				}
				key = "<key oid='' queryType='XPATH'>"+theKey+"</key>";
			}
			else
			{
				key = "<key oid=\""+theKey+"\" />\n" ;
			}

			QosErase qos = new QosErase();

			this.xmlBlaster.Erase( key, qos.ToString() );
		}


		public delegate void MessagesFillDelegate( MessageUnit[] mus );
		public MessagesFillDelegate messagesFillDelegate ;
		public void messagesFill( MessageUnit[] msgs )
		{
			string me="messagesFill()";
			logger.Debug(me);

			//System.Threading.Monitor.Enter(this.messages);
			//try 
			//{
			lock (this.messages) 
			{
				logger.Debug("{0} entering lock", me);
				try
				{
					//messages.BeginUpdate();
					foreach( MessageUnit msg in msgs )
					{
						MessageTreeNode mtn = new MessageTreeNode(msg) ;
						MessageTreeNode topicNode = SearchForLabel( mtn.Text );
						if( topicNode == null )
						{
							logger.Debug("messagesFill() new topic: "+mtn.Text);
							messages.Nodes.Add( mtn );
						}
						else
						{
							logger.Debug("messagesFill() add message to topic: "+mtn.Text );
							topicNode.Nodes.Add( mtn );
						}
					}
					//messages.EndUpdate();
				}
				catch( Exception ex )
				{
					logger.Error("{0} Failed: {1}",me, ex.Message );
				}
				logger.Debug("{0} releasing lock", me);
			}
			//}
			//finally 
			//{
				// Always use Finally to ensure that you exit the Monitor.
				// The following line creates another object containing 
				// the value of x, and throws SynchronizationLockException
				// because the two objects do not match.
			//	System.Threading.Monitor.Exit(this.messages);
			//}
		}

		MessageTreeNode SearchForLabel( string label )
		{
			if( messages.Nodes == null )
			{
				return null ;
			}
			// pas besoin de récurtion
			foreach( TreeNode node in messages.Nodes )
			{
				if( node.Text == label )
				{
					return (MessageTreeNode) node ;
				}
			}
			return null ;
		}


		void MessagesViewsClear()
		{
			// Clear messages and msgview
			this.messages.Nodes.Clear();
			this.msgview_key.Text = "" ;
			this.msgview_qos.Text = "" ;
			this.msgview_content.Text = "" ;
		}


		public static string FormatNiceXml( string xml )
		{
			StringBuilder sb = new StringBuilder( xml.Trim() );
			sb.Replace( crlf, CRLF );
			sb.Replace( lf, CRLF );
			sb.Replace( cr, CRLF );
			sb.Replace( "><", ">"+CRLF+"<" );
			return sb.ToString() ;
		}


		private void messages_AfterSelect(object sender, System.Windows.Forms.TreeViewEventArgs e)
		{
			logger.Debug("messages_AfterSelect() Node.Text: "+e.Node.Text );

			MessageUnit msg = ((MessageTreeNode) e.Node ).msg ;

			this.msgview_key.Text = FormatNiceXml(msg.KeyStr) ;
			this.message_toolTip.SetToolTip( this.msgview_key, this.msgview_key.Text );

			this.msgview_qos.Text = FormatNiceXml(msg.Qos.OuterXml) ;
			this.message_toolTip.SetToolTip( this.msgview_qos, this.msgview_qos.Text );

			this.msgview_content.Text = FormatNiceXml(msg.ContentStr) ;
			this.message_toolTip.SetToolTip( this.msgview_content, this.msgview_content.Text );
		}

		private void msgsend_copySelected_Click(object sender, System.EventArgs e)
		{
			this.msgsend_key.Text = this.msgview_key.Text ;
			this.msgsend_qos.Text = this.msgview_qos.Text ;
			this.msgsend_content.Text = this.msgview_content.Text ;
		}


		public class MessageTreeNode : TreeNode
		{
			public MessageUnit msg ;
			public static string ComputeLabel( string keyoid )
			{
				return keyoid==null?"Unknow":keyoid ;
			}
			public MessageTreeNode( MessageUnit msg )
				: base( ComputeLabel(msg.KeyOid) )
			{
				this.msg = msg ;
			}
		}

	}
}
