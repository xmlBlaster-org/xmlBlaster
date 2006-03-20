# Exception
# -------------------------------------------------------------------------------------
# Description:
#	A simple class that is meant to provide an easy-to-use try / catch
#	mechanism in order to improve the libraries error checking.
#
# 05/03/02 17:37 mad@ktaland.com - add sub dump()
# 19/11/2001 yann@ktaland.com - Integration at KBuilderV2
# Author: Jean-Michel Hiver
# -------------------------------------------------------------------------------------

package xmlBlaster::Exception ;

use Exporter;
use strict;
use vars qw /@ISA @EXPORT $AUTOLOAD/;

@ISA    = qw /Exporter/;
@EXPORT = qw /try catch throw/;


##
# __PACKAGE__->new (@_);
# ----------------------
#   Constructs a new xmlBlaster::Exception object, which is probably
#   going to be thrown somewhere. Anything in @_ is converted
#   into a hash that is blessed in __PACKAGE__.
##
sub new
{
    my $class = shift;
    $class = ref $class || $class;

    my $self = bless { @_ }, $class;

    my $i = 0;
    my $found = 0;

    # in order to provide useful information, we must rewind the stack trace
    # till we find the throw method. From then, we stop at the first method
    # which does not belong to the xmlBlaster::Exception package.
    while (my @info = caller ($i++))
    {
	if ($found)
	{
	    if ( $info[3] =~ /^.*::try$/   or
		 $info[3] =~ /^.*::catch$/ or
		 $info[3] =~ /^.*::throw$/ or
		 $info[3] eq '(eval)'      or
		 $info[3] =~ /.*::__ANON__$/ )
	    {
		next;
	    }
	    else
	    {
		$self->{package}    = $info[0];
		$self->{filename}   = $info[1];
		$self->{line}       = $info[2];
		$self->{subroutine} = $info[3];
		$self->{hasargs}    = $info[4];
		$self->{wantarray}  = $info[5];
		$self->{evaltext}   = $info[6];
		$self->{is_require} = $info[7];
		last;
	    }
	}
	else
	{
	    if ($info[3] =~ /^.*::throw$/) { $found = 1 }
	}
    }

    return $self;
}


##
# try BLOCK;
# ----------
#   Same as eval BLOCK. See perldoc -f eval.
#
# try BLOCK catch BLOCK;
# ----------------------
#   Executes the code in the try BLOCKED. if
#   an exception is raised, executes the
#   catch block and passes the exception as
#   an argument.
##
sub try (&@)
{
    my( $try, $catch )=( shift, shift );

    $@ = undef;
    eval { &$try };
    if ($@)
    {

		if( !ref $@ and $@ =~/^Fault returned from XML RPC Server/ ){
			$@ = new xmlBlaster::Exception (
				code => 'XMLBLASTER_ERROR',
				info => $@
			);

		}else{
			unless( ref $@ and $@->isa( 'xmlBlaster::Exception' ) )
			{
				$@ = new xmlBlaster::Exception (
					code => 'RUNTIME_ERROR',
					info => $@
				);
			}
		}
		defined $catch or throw $@ ;
		$catch->($@);
    }
    $@ = undef;

}


# doesn't do much but provides a nice syntaxic sugar.
sub catch (&) { return shift }


##
# throw ($exception)
# ------------------
#   Throws $exception away. if $exception is not an object,
#   wraps it in a KBuilderV2::Exception object and throws it away.
##
sub throw (@)
{
    my $exception = shift;
    unless (ref $exception and $exception->isa ('xmlBlaster::Exception'))
    {
	$exception = new xmlBlaster::Exception (
						type => 'runtime_error',
						info => $exception );
    }
    die $exception;
}


##
# $obj->stack_trace;
# ------------------
#   Returns the stack trace string.
##
sub stack_trace
{
    my $i = 0;
    while (my @info = caller ($i++))
    {
	print join "\t", @info;
	print "\n";
    }
}

##
# 05/03/02 17:37 mad@ktaland.com
#	Returns a string that describing the Exception
##
sub dump {

    my $self = shift ;

	my $str = ('='x40) . "\nA Exception occured :\n" ;
	$str .= 'code : '.$self->{'code'}."\n" ;
	$str .= 'info : ' ;
	if( ref($self->{'info'}) eq 'ARRAY' ){
		foreach( @{$self->{'info'}} ){
			$str .= "$_\n";
		}
	}else{
		$str .= $self->{'info'}."\n";
	}
	$str .= ('='x40)."\n" ;

	return $str ;
}


sub AUTOLOAD
{
    my $self = shift;
    my $name = $AUTOLOAD =~ /.*::(.*)/;
    if (@_ == 0) { return $self->{$name} }
    else         { $self->{$name} = shift }
}

1;
