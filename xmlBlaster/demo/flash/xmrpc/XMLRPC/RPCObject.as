dynamic class XMLRPC.RPCObject {
	private var type:String;
	private var value:Array;
	
	public function RPCObject( Type ) {
	   this.type  = Type;
	
	   if ((this.Type == 'struct') ||
	       (this.type == 'array')) {
	
	      this.value = new Array();
	   }
	   else
	   {
	      this.value = null;
	   }
	}
	
	public function AddMember ( Arg1:String, Arg2, Arg3:String ){
	   if (this.type == 'struct') {
	
	      var TempObject = new Object();
	
	      TempObject.name  = Arg1;
	      TempObject.value = Arg2;
	      TempObject.type  = Arg3;
	
	      this.value.push(TempObject);
	      return true;
	   }
	
	   if (this.type == 'array') {
	
	      this.value.push(Arg1);
	      return true;
	   }
	
	   return false;
	}
	
	public function GetMember ( Arg1 ){
	   if (this.type == 'struct') {
	
	      for (var i in this.value) {
	
		  if (this.value[i].name == Arg1)
		     return this.value[i].value.value;
	
	      }
	
	      return null;
	
	   }
	
	   if (this.type == 'array') {
	
	      return this.value[Arg1].value;
	
	   }
	
	   return null;
	}
	
	public function SetValue ( Value ){
	   if ((this.type == 'string')  ||
	       (this.type == 'boolean') ||
	       (this.type == 'base64')  ||
	       (this.type == 'double')  ||
	       (this.type == 'dateTime.iso8601') ||
	       (this.type == 'int') ||
	       (this.type == 'i4')) {
	
	      this.value = Value;
	      return true;
	   }
	
	   return false;
	}
	
	public function GetValue(){
	   if ((this.type == 'string')  ||
	       (this.type == 'boolean') ||
	       (this.type == 'base64')  ||
	       (this.type == 'double')  ||
	       (this.type == 'dateTime.iso8601') ||
	       (this.type == 'int') ||
	       (this.type == 'i4')) {
	
	      return this.value;
	   }
	
	   return null;
	}
	
	function get length():Number {
	  return this.value.length;
	}
	
	function set length(a:Number) {
		trace("RPCObject.length is read-only!");
	}
	

}