#
# Exception
#
#      is meant to provide an easy-to-use try / catch
#      mechanism in order to improve error checking.
#
#	mad@ktaland.com
#	yann@ktaland.com
#
package Exception;

use Exporter ;
use Data::Dumper ; # for sub dump()
use strict;
use vars qw /@ISA @EXPORT $AUTOLOAD/;

@ISA    = qw /Exporter/;
@EXPORT = qw /try catch throw/;


##
# __PACKAGE__->new (@_);
# ----------------------
#   Constructs a new Exception object, which is probably
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
    # which does not belong to the Exception package.
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
    my ($try, $catch) = (shift, shift);

    $@ = undef;
    eval { &$try };
    if ($@)
    {
	unless (ref $@ and $@->isa ('Exception'))
	{
	    $@ = new Exception ( code => 'RUNTIME_ERROR',
				      info => $@ );
	}
	defined $catch or throw $@;
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
#   wraps it in a Exception object and throws it away.
##
sub throw (@)
{
    my $exception = shift;
    unless (ref $exception and $exception->isa ('Exception'))
    {
	$exception = new Exception ( type => 'runtime_error',
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

sub dump (@)
{
	my $ex = shift;

	my( $code, $info );
	$code = $ex->{'code'} if( exists $ex->{'code'} );
	$info = $ex->{'info'} if( exists $ex->{'info'} );
	print STDERR '='x40, "\n" ;
	if( $code && $info ){
		print STDERR "\nERROR with Exception :\n";
		print STDERR "code = $code \n";
		print STDERR "info = $info \n";
	}else{
		print STDERR 'ERROR with unknow Exception =', '='x40, Dumper( $ex ), "\n";
	}
	print STDERR '='x40, "\n" ;

}#dump

sub AUTOLOAD
{
    my $self = shift;
    my $name = $AUTOLOAD =~ /.*::(.*)/;
    if (@_ == 0) { return $self->{$name} }
    else         { $self->{$name} = shift }
}

1;
