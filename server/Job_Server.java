/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

package server;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Hashtable;
import java.net.*;
import java.io.*;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import ij.ImageJ;
import ij.plugin.PlugIn;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* This is a class that provides a TCP server for queuing and starting
 * jobs in an instance of ImageJ.  An objection to this might be that
 * ImageJ already binds to localhost and starts commands in a similar
 * fashion.  However, ImageJ has no facility for controlling the
 * number of jobs that may run concurrently or querying the progress
 * of jobs. */

/* Each message from and to the server consists of a single line of
 * tab-separated fields followed by \r\n.  When any client connects to
 * the server, the server begins the conversation by sending two
 * fields:

     "hello" followed by a challenge, which is a string of letters and
     numbers with a strong random component.

 * The client should reply with two fields:

     The first field should be "auth"

     The second field should be the SHA1 digest of the shared secret
     word concatenated with the challenge provided by the server.
     e.g. if the server greets the client with
     "hello\t10a7e01bf81d2302.1ae834af0e9\r\n" and the shared secret
     word is "hopeless", then it should return the SHA1 sum of
     "hopeless10a7e01bf81d2302.1ae834af0e9". This means returning the
     message:

     "auth\tfa9768025aadab388fb5508a4ef1a680fbff233e\r\n"


 * If this is incorrect then the server replies:

     "denied\r\n"

   ... and closes the connection.  If the reply is correct then the
   reply is:

     "success\r\n"

 * Then the client should send a line with a command.  The first field
   in the command indicates the action:

     "start":

	 - The second field must be a macro expression, typically
	   "run('Watever Plugin','example=foo');"

	 - A single line is returned which contains "started" in the
	   first, and in the second field the job ID specific to this
	   server.

     "query":

	 - The second field must a job ID

	 - A single line is returned.  This describes the status of
	   the job.  The first field might be:

	     "finished":
		  - There are no more fields: the job finished
		    successfully.

	     "failed":
		  - The job failed.  There will be one extra field,
		    with a short error message.

	     "working":
		  - The job is still in progress.  The first field
		    will be the ISO 8601 timestamp when it started.
		    The second field will be either empty or contain a
		    string representation of a floating point number
		    describing the proportion of the way through the
		    operation that we're at.  The third fild, if not
		    empty, will be an estimate of the time at which
		    the job will finish.

	     "queued":
		  - The jobs is still queued, and hasn't been started
		    yet.  The second field incidates the number of
		    jobs ahead of this one in the queue, or -1 in the
		    case where it has been started very recently.

	     "unknown":
		  - The job ID was unknown

   If the command is unknown, has the wrong number of arguments or
   some other error occurs, then "error\t<ERROR-MESSAGE" is returned.

*/

/* Start the server with a command like:

       xvfb-run java -Xmx1024m ../ImageJ/ij.jar -eval 'run("Job Server","");'

   ... where the parameter to -Xmx is some sensible amount of memory
   given the real memory contraints of your computer.

   Alternatively, using fiji's --headless option, you can do:

      ./fiji --headless server.txt

   ... where server.txt just containst the line:

      run("Job Server","");

 */

public class Job_Server implements PlugIn {

	static private String logFilename = null;
	static private String configurationFilename = null;

	Hashtable<Thread,Job> threadToJob;

	public static final int maxJobs = 2;

	/* We also use the jobQueue object to synchronize on; that
	   also protects currentlyRunningJobs... */

	private static LinkedList<Job> jobQueue;
	private int currentlyRunningJobs = 0;

	/* The allJobs object just records all jobs ever submitted to this
	   server. */
	private static ArrayList<Job> allJobs;

	public final static int tcpPort = 2061;
	public final static String bindInterfaceIP = "127.0.0.1";

	public void startNextIfPossible() {
		log( "startNextIfPossible()" );
		synchronized(jobQueue) {
			if( (jobQueue.size() > 0) && (currentlyRunningJobs < maxJobs) ) {
				Job jobToRun = jobQueue.removeFirst();
				log("Starting a new job "+jobToRun);
				jobToRun.start();
				++ currentlyRunningJobs;
				log("New job was started (now "+currentlyRunningJobs+" running)");
			}
		}
	}

	public void jobCompleted( Job j ) {
		synchronized(jobQueue) {
			-- currentlyRunningJobs;
			startNextIfPossible();
		}
	}

	private PrintStream logStream;

	public void log( Job job, Exception e ) {
		String message = "";
		if( job != null )
			message += job;
		message += "[]: " + e;
		synchronized(logStream) {
			logStream.println( message );
			logStream.flush();
		}
	}

	public void log( String s ) {
		log( null, s );
	}

	/** You can call this static method from your job's thread to
	    update the progress of the job reported by the job server.
	    This will only work if you're in the original thread that
	    was created when the job started...
	 */
	public static boolean setJobProportionDone( float proportionDone ) {

		Thread currentThread = Thread.currentThread();
		Job job = null;
		try {
			job = (Job)currentThread;
		} catch( ClassCastException e ) {
			return false; // The current thread isn't one of our jobs...
		}
		job.setProportionDone( proportionDone );
		return true;
	}

	public void log( Job job, String s ) {
		String message = "";
		if( job != null )
			message += job;
		message += "[]: " + s;
		synchronized(logStream) {
			logStream.println( message );
			logStream.flush();
		}
	}

	public static void finishDirectory( String directory ) {
		File d = new File(directory);
		if( ! d.isDirectory() ) {
			throw new RuntimeException("finishDirectory() called with a non-directory: '"+directory+"'");
		}
		String completedFile = d.getAbsolutePath() + File.separator + ".completed";
		String generatingFile = d.getAbsolutePath () + File.separator + ".generating";
		try {
			FileOutputStream fos = new FileOutputStream(completedFile);
			fos.close();
			File f = new File(generatingFile);
			f.delete();
		} catch( IOException e ) {
			final StringWriter w = new StringWriter();
			final PrintWriter pw = new PrintWriter(w);
			e.printStackTrace(pw);
			pw.close();
			throw new RuntimeException(w.toString());
		}
	}

	public static void updateProgressInDirectory( String directory, float proportionDone ) {
		File d = new File(directory);
		if( ! d.isDirectory() ) {
			throw new RuntimeException("updateProgressInDirectory() called with a non-directory: '"+directory+"'");
		}
		String progressFile = d.getAbsolutePath() + File.separator + ".progress";
		try {
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(progressFile),"UTF-8"));
			pw.print(""+proportionDone);
			pw.close();
		} catch( IOException e ) {
			final StringWriter w = new StringWriter();
			final PrintWriter pw = new PrintWriter(w);
			e.printStackTrace(pw);
			pw.close();
			throw new RuntimeException(w.toString());
		}
	}

	static StringBuffer byteArrayToStringBuffer( byte [] b ) {
		char [] hexDigits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
		StringBuffer result = new StringBuffer();
		for( int i = 0; i < b.length; ++i ) {
			int value = b[i] & 0xFF;
			result.append( hexDigits[(value & 0xF0) >> 4] );
			result.append( hexDigits[(value & 0x0F)] );
		}
		return result;
	}

	SecureRandom secureRandom = new SecureRandom();
	Calendar calendar = Calendar.getInstance();
	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

	private String makeChallenge() {
		StringBuffer sb = new StringBuffer();
		sb.append( sdf.format(calendar.getTime()) );
		sb.append( "." );
		sb.append( System.currentTimeMillis() );
		sb.append( "." );
		byte bytes[] = new byte[64];
		secureRandom.nextBytes(bytes);
		sb.append( byteArrayToStringBuffer( bytes ) );
		return sb.toString();
	}

	boolean validChallengeResponse( String challenge, String sharedSecret, String suppliedResponse ) {
		boolean result;
		try {
			String stringToHash = sharedSecret + challenge;
			MessageDigest sha = MessageDigest.getInstance("SHA-1");
			byte [] bytesToHash = stringToHash.getBytes("UTF-8");
			byte [] bytesSHA1sum = sha.digest(bytesToHash);
			String stringSHA1sum = byteArrayToStringBuffer( bytesSHA1sum ).toString();
			log( "We think the response should be: "+stringSHA1sum );
			log( "Comparing it to the supplied: "+suppliedResponse );
		        result = stringSHA1sum.equals(suppliedResponse);
			log( "The result is: "+result);
		} catch( java.security.NoSuchAlgorithmException e ) {
			log( "Missing the SHA-1 algorithm (!)" );
			return false;
		} catch( java.io.UnsupportedEncodingException e ) {
			log( "Missing UTF-8 encoding (!)" );
			return false;
		}
		return result;
	}

	public Hashtable parseConfigurationFile( String configurationFilename ) {

		Hashtable result = new Hashtable();

		try {
			BufferedReader f = new BufferedReader( new FileReader(configurationFilename) );
			Pattern p = Pattern.compile("^[ \t]*([^ \t=]+)[ \t]*=[ \t]*([^ \t=]+)([ \t]*$|[ \t]+#)");
			Pattern empty = Pattern.compile("^[ \t]*($|#)");

			String line = null;
			while ( (line = f.readLine()) != null ) {
				System.out.println("Got line: "+line);
				Matcher m = empty.matcher( line );
				if( m.find() )
					continue;
			        m = p.matcher( line );
				if( m.find() ) {
					result.put(m.group(1),m.group(2));
				} else {
					log( "Malformed line found in configuration file: " );
				}
			}
		} catch( IOException e ) {
			log( "Couldn't open the configuration file: "+configurationFilename );
			return null;
		}

		return result;
	}

	public void run(String ignore) {

		logFilename = System.getProperty("logfilename");
		if( logFilename == null )
			logFilename = "/tmp/job-server.log";
		System.out.println("Using the log file: "+logFilename);

		configurationFilename = System.getProperty("configurationfilename");
		if( configurationFilename == null )
			configurationFilename = "/etc/default/imagej-job-server";
		System.out.println("Using the configuration file: "+configurationFilename);

		logStream = null;
		try {
			logStream = new PrintStream(logFilename);
		} catch( IOException e ) {
			System.out.println("Couldn't open log file.");
			System.exit(-1);
		}

		Hashtable configurationHash = parseConfigurationFile( configurationFilename );
		if( configurationHash == null ) {
			System.out.println("Parsing the configuration file failed");
			System.exit(-1);
		}

		log( "Finished parsing configuration file" );

		Runtime runtime = Runtime.getRuntime();
		int processors = runtime.availableProcessors();

		log( "This system has "+processors+" processors." );

		System.out.println("Number of processors available to the Java Virtual Machine: " + processors);

		jobQueue = new LinkedList<Job>();
		allJobs = new ArrayList<Job>();

		InetAddress bindAddress = null;

		try {
			bindAddress = InetAddress.getByName(bindInterfaceIP);
		} catch( UnknownHostException e ) {
			System.out.println("Unknown host: "+bindInterfaceIP);
			System.exit(-1);
		}
		ServerSocket serverSocket = null;

		try {
			serverSocket = new ServerSocket(tcpPort,32,bindAddress);
		} catch (IOException e) {
			System.out.println("Could not listen on port: "+tcpPort);
			System.exit(-1);
		}

		for(;;) {

			String sharedSecret = (String)configurationHash.get("IMAGEJ_SERVER_SECRET");
			if( sharedSecret == null ) {
				System.out.println("Couldn't find the shared secret (key IMAGEJ_SERVER_SECRET) in the configuration file");
				System.exit(-1);
			}

			Socket clientSocket = null;
			BufferedReader in = null;
			PrintStream out = null;

			try {

				clientSocket = serverSocket.accept();

				log( "Accepted connection "+clientSocket );

				in = new BufferedReader(
					new InputStreamReader(
						clientSocket.getInputStream()));

				out = new PrintStream( clientSocket.getOutputStream() );

				String challenge = makeChallenge();
				log( "Going to send challenge: "+challenge );

				out.print("hello\t"+challenge+"\r\n");

				String nextLine = in.readLine();
				if( nextLine == null ) {
					log("nextLine was null when waiting for response to challenge from client");
					clientSocket.close();
					continue;
				}

				String [] arguments = nextLine.split("\\t");

				if( arguments.length != 2 ) {
					log("Wrong number of fields ('"+arguments.length+"') in response to challenge - full line was: '"+nextLine+"'");
					out.print("denied\r\n");
					clientSocket.close();
					continue;
				}

				if( ! arguments[0].equals("auth") ) {
					log("Should have got an 'auth' in response to challenge - full line was: '"+nextLine+"'");
					out.print("denied\r\n");
					clientSocket.close();
					continue;
				}

				if( ! validChallengeResponse(challenge,sharedSecret,arguments[1]) ) {
					log("Crypted secret failed to match: '"+nextLine+"'");
					out.print("denied\r\n");
					clientSocket.close();
					continue;
				}

				log("Authentication succeeded, continuing...");
				out.print("success\r\n");

				nextLine = in.readLine();
				if( nextLine == null ) {
					log("nextLine was null when waiting for command from the client");
					clientSocket.close();
					continue;
				}

				arguments = nextLine.split("\\t");

				if( arguments.length >= 2 ) {

					if( "start".equals(arguments[0]) ) {

						String macroExpression = arguments[1];

						log("Going to create a job to run macro expression: "+macroExpression);

						Job newJob = new Job( this,
								      macroExpression );

						newJob.setStatus(Job.QUEUED);

						int jobID = -1;
						synchronized ( allJobs ) {
							jobID = allJobs.size( );
							allJobs.add( newJob );
						}

						newJob.setJobID( jobID );

						synchronized( jobQueue ) {
							jobQueue.addLast(newJob);
							startNextIfPossible( );
						}

						log("Java job ID will be: "+jobID);
						out.print("started\t"+jobID+"\r\n");

					} else if( "query".equals(arguments[0]) ) {

						String jobIDString = arguments[1];
						int jobID = -1;

						try {
							jobID = Integer.parseInt(jobIDString);
						} catch( NumberFormatException nfe ) {
							log("Malformed jobIDString: "+jobIDString);
							out.print("unknown\tJob ID '"+jobIDString+"' not found\r\n");
							clientSocket.close();
							continue;
						}

						Job j=null;

						try {
							j = allJobs.get(jobID);
						} catch( IndexOutOfBoundsException e ) {
							log("Couldn't find job ID: "+jobID);
							out.print("unknown\tJob ID '"+jobIDString+"' not found\r\n");
							clientSocket.close();
							continue;
						}

						int status=j.getStatus();
						if( status == Job.WORKING ) {
							String p="";
							float proportionDone = j.getProportionDone();
							if( proportionDone >= 0 ) {
								p = "" + proportionDone;
							}
							out.print("working\t"+p+"\r\n");
						} else if( status == Job.FAILED ) {
							String errorMessage = j.getErrorMessage();
							if( errorMessage == null )
								errorMessage = "";
							out.print("failed\t"+errorMessage+"\r\n");
						} else if( status == Job.FINISHED ) {
							out.print("finished\r\n");
						} else if( status == Job.QUEUED ) {
							int placeInQueue = -1;
							// FIXME: this is potentially a performance bottleneck:
							synchronized (jobQueue) {
								for( int i = 0; i < jobQueue.size(); ++i ) {
									Job queueJob=(Job)jobQueue.get(i);
									if(queueJob.getJobID()== jobID)
										placeInQueue = i;
								}
							}
							out.print("queued\t"+placeInQueue+"\r\n");
						} else {
							out.print("error\tUnknown job status found: "+status+"\r\n");
						}


					} else {
						out.print("error\tUnknown action "+arguments[0]+"\r\n");
					}

				} else {
					out.print("error\tNot enough arguments (only "+arguments.length+")\r\n");
				}

				clientSocket.close();

			} catch( IOException e ) {
				System.out.println("There was an IOException with connection "+clientSocket+ ": "+e);
				continue;
			}


		}

	}

}
