/*////////////////////////////////////////////////////////////////////////////////////////////// 
   XMLRPC.Method Version 0.2 (for ActionScript 2.0)

Change by cyrille@giquello.com
	09 nov 2005
	see XMLRPC.Method.CleanUp()

Matt Shaw <matt@dopelogik.com>
   Last Modified: 11-19-2004
   First Modified: 01-30-2004
   
   :::::::::
   
   Contact Information:
   Matt Shaw <matt@dopelogik.com>

//////////////////////////////////////////////////////////////////////////////////////////////*/

import XMLRPC.common;


dynamic class XMLRPC.Method extends XML {
	private var _VERSION:String = "0.2";
	private var _PRODUCT:String = "XMLRPC.Method";
	private var _TRACE_LEVEL:Number = 3;	
	private var _parameters:Array;
	private var _method_name:String;
	
	/*///////////////////////////////////////////////////////
	Constructor
	///////////////////////////////////////////////////////*/
	public function Method(){
		this.xmlDecl = "<?xml version=\"1.0\"?>";
		this.contentType = "text/xml";

		this.ClearParameters();

		DTrace("Object instance created. (v" + _VERSION + ")",1);
	}
	
	
	/*///////////////////////////////////////////////////////
	Render()
	?:	
	IN:	
	OUT:	
 	///////////////////////////////////////////////////////*/
	public function Render():Boolean {
		DTrace("Render()",1);
		var ParentNode:XMLNode = new XMLNode();
		var ChildNode:XMLNode = new XMLNode();
		
		// Create the <methodCall>...</methodCall> root node
		ParentNode = this.createElement("methodCall");
		this.appendChild(ParentNode);
		
		// Create the <methodName>...</methodName> node
		ChildNode = this.createElement("methodName");
		ChildNode.appendChild(this.createTextNode(this._method_name));
		ParentNode.appendChild(ChildNode);
		
		// Create the <params>...</params> node
		ChildNode = this.createElement("params");
		ParentNode.appendChild(ChildNode);
		ParentNode = ChildNode;
		
		// build nodes that hold all the params
		DTrace("Render(): Creating the params node.",2);
		for (var i =0; i<this._parameters.length;i++) {
			DTrace("PARAM: " + this._parameters[i],3);
			ChildNode = this.createElement("param");
			ChildNode.appendChild(this.CreateParameterNode(this._parameters[i]));
			ParentNode.appendChild(ChildNode);
		}
		DTrace("Render(): Resulting XML document:",2);
		DTrace("Render(): "+this.toString(),2);
		
		return true;
	}
	
		
	/*///////////////////////////////////////////////////////////////////////
	CreateParameterNode()
	?: 	 This method creates <value>...</value> tag pairs with the 
		 correct data types for each value. This method is recursively
		 called for each <struct> and <array> that is defined.
	IN:  A Parameter (?)
	OUT: A node representing the entire <params> tree 
	///////////////////////////////////////////////////////////////////////*/		   
	private function CreateParameterNode(parameter:Object):XMLNode {
		DTrace("CreateParameterNode()",2);
		var Node:XMLNode = this.createElement("value");
		var TypeNode:XMLNode;

		if (typeof (parameter) == "object") {

			// Handle Explicit Simple Objects
			if ( common.isSimpleType(parameter.type) ) {
			    //cdata is really a string type with a cdata wrapper, so don't really make a 'cdata' tag
			    paramter = this.fixCDATAParameter(parameter);
			    
				DTrace("CreateParameterNode(): Creating object '"+parameter.value+"' as type "+parameter.type,3);
				TypeNode = this.createElement(parameter.type);
				TypeNode.appendChild(this.createTextNode(parameter.value));
				Node.appendChild(TypeNode);
				return Node;
			}
			// Handle Array Objects
			if (parameter.type == XMLRPC.types.ARRAY) {
				var DataNode;
				DTrace("CreateParameterNode(): >> Begin Array",3);
				TypeNode = this.createElement("array");
				DataNode = this.createElement("data");
				for (var i in parameter.value) {
					DataNode.appendChild(this.CreateParameterNode(parameter.value[i]));
				}
				TypeNode.appendChild(DataNode);
				DTrace("CreateParameterNode(): << End Array",3);
				Node.appendChild(TypeNode);
				return Node;
			}
			// Handle Struct Objects
			if (parameter.type == XMLRPC.types.STRUCT) {
				DTrace("CreateParameterNode(): >> Begin struct",3);
				TypeNode = this.createElement("struct");
				for (var i in parameter.value) {
					var MemberNode = this.createElement("member");

					// add name node
					MemberNode.appendChild(this.createElement("name"));
					MemberNode.lastChild.appendChild(this.createTextNode(i));

					// add value node
					MemberNode.appendChild(this.createElement("value"));
					MemberNode.lastChild.appendChild(this.createTextNode(parameter.value[i]));
					
					TypeNode.appendChild(MemberNode);
				}
				DTrace("CreateParameterNode(): << End struct",3);
				Node.appendChild(TypeNode);
				return Node;
			}
		}
	}
	
	
	/*///////////////////////////////////////////////////////
	setMethod()
	?:	 Sets the remote method name to be called
	IN:	 Method name
	OUT: Voie
 	///////////////////////////////////////////////////////*/
	public function setMethod(method_name:String):Void {
		this._method_name=method_name;
	}
	
	/*///////////////////////////////////////////////////////
	AddParameter()
	?:	    Adds a parameter aka argument for the method called
	IN:	    Object holding type and value, not sure I like it one variable-
            not good for self documenting code.
	OUT:	Boolean-Not sure why right now.
 	///////////////////////////////////////////////////////*/
	public function AddParameter(param_value:Object,param_type:String):Boolean {
		DTrace("AddParameter()",2);
		this._parameters.push({type:param_type,value:param_value});
		return true;
	}
	
	/*///////////////////////////////////////////////////////
	ClearParameters()
	?:      Clears Parameters
	IN:	    Void
	OUT:	Void
 	///////////////////////////////////////////////////////*/
	public function ClearParameters():Void{
		this._parameters=new Array();
	}

    /*///////////////////////////////////////////////////////
	fixCDATAParameter()
	?:      Turns a cdata parameter into a string parameter with 
	        CDATA wrapper
	IN:	    Possible CDATA parameter
	OUT:	Same parameter, CDATA'ed is necessary
 	///////////////////////////////////////////////////////*/
    private function fixCDATAParameter(parameter:Object){
        if (parameter.type==XMLRPC.types.CDATA){
            parameter.type=XMLRPC.types.STRING;
            parameter.value='<![CDATA['+parameter.value+']]>';  
        }
        return parameter
    }
    
    
	/*///////////////////////////////////////////////////////////////////////
	CleanUp()
	?: 	    traces to Output and (If available) the RemoteTrace Tool for debugging
	IN: 	A message
	OUT:    Void
	///////////////////////////////////////////////////////////////////////*/
	public function CleanUp():Void {
		this.ClearParameters();
		// 09 nov 2005 cyrille@giquello.com
		// This 'null' then appears in the sent xml.
		// Server's parser are not happy to see a text element where it does not waiting for !
		//this.parseXML(null);
		this.parseXML("");
	}
	
	/*///////////////////////////////////////////////////////////////////////
	Quiet: Setter
	?: 	
	///////////////////////////////////////////////////////////////////////*/
	
	public function get Quiet(){
		if (this._TRACE_LEVEL==0) return true;
		return false;
	}
	
	public function set Quiet(a:Boolean){
		if (a)
			this._TRACE_LEVEL=0
		else
			this._TRACE_LEVEL=3
	}	
	
	
	/*///////////////////////////////////////////////////////////////////////
	DTrace()
	?: 	    traces to Output
	IN: 	A message, level of verboseness (higher=more)
	OUT:    Void
	///////////////////////////////////////////////////////////////////////*/
	private function DTrace(a,traceLevel:Number):Void {
		if (this._TRACE_LEVEL >= traceLevel){
			trace(this._PRODUCT + " -> " + a);
		}
	}
	
}