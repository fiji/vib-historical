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
import java.sql.*;

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

         - The second field is the name of an ImageJ command, such as
           'Whatever Plugin'.

         - The third field is the string of parameters that will be
           passed to the ImageJ command (augmented with a directory
           parameter.)

	 - The fourth field must be the relative output directory.
           (It is relative to IMAGEJ_SERVER_DATA_ROOT.)

	 - A fifth field is an estimated upper bound on the number of
           megabytes of memory that the command will require to run to
           completion.

         - The reply is either:

                  "started\t42\r\n"

           ... where 42 is a job ID or:

                  "failed\tError message\r\n"

           .... where 'Error message' is something more informative.

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
   some other error occurs, then "error\t<ERROR-MESSAGE>" is returned.

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

	private static Job_Server instance = null;

	static private final String allowedCommands[] = {
		"Scale, Merge and Unpack",
		"MIPDriver ",
		"Extract Image Properties"
	};

	private String logFilename = null;
	private String configurationFilename = null;

	Hashtable<Thread,Job> threadToJob;

	private int maxJobs = 2;

	/* We also use the jobQueue object to synchronize on; that
	   also protects currentlyRunningJobs... */

	private LinkedList<Job> jobQueue;
	private LinkedList<Job> currentlyRunningJobs;

	private int tcpPort = 2061;
	private String bindInterfaceIP = "127.0.0.1";

	public void startNextIfPossible() {
		log( "startNextIfPossible()" );
		synchronized(jobQueue) {
			// Calculate the amount of memory that's used by the currently running jobs:
			int maxMemoryUsedCurrentlyMiB = 0;
			for( int i = 0; i < currentlyRunningJobs.size(); ++i ) {
				Job j=(Job)currentlyRunningJobs.get(i);
				maxMemoryUsedCurrentlyMiB += j.getMemoryRequiredMiB();
			}
			/* Only start a job if there's something in
			   the job queue, we aren't already running
			   too many jobs, and there's going to be
			   enough memory. */
			if( (jobQueue.size() > 0) && (currentlyRunningJobs.size() < maxJobs) ) {
				log("Would start a new job, but checking the memory requirements...");
				// Just peek at the next in the queue to check the memory it requires:
				log(" queue length before is: "+jobQueue.size());
				Job firstInQueue = jobQueue.getFirst();
				log(" queue length after is: "+jobQueue.size());
				int maxMemoryPossibleMiB = (int)(runtime.maxMemory() / (1024 * 1024));
				int wouldPossiblyBeUsedMiB = maxMemoryUsedCurrentlyMiB + firstInQueue.getMemoryRequiredMiB();
				if( wouldPossiblyBeUsedMiB > maxMemoryPossibleMiB ) {
					log("Not running the job - currently using "+maxMemoryUsedCurrentlyMiB+
					    "MiB, maximum possibly available is "+maxMemoryPossibleMiB+
					    "MiB, amount that could be used if the job were started: "+wouldPossiblyBeUsedMiB);
				} else {
					Job jobToRun = jobQueue.removeFirst();
					log("Starting a new job "+jobToRun);
					jobToRun.start();
					currentlyRunningJobs.add(jobToRun);
					log("New job was started (now "+currentlyRunningJobs.size()+" running)");
				}
			}
		}
	}

	public void jobCompleted( Job j ) {
		synchronized(jobQueue) {
			// Find the index of that job by matching the ID:
			int jobIDToFind = j.getJobID();
			int foundIndex = -1;
			for( int i = 0; i < currentlyRunningJobs.size(); ++i ) {
				Job consideringJob = currentlyRunningJobs.get(i);
				if( jobIDToFind == consideringJob.getJobID() ) {
					foundIndex = i;
					break;
				}
			}
			if( foundIndex < 0 ) {
				System.out.println("Completed job didn't seem to be in the currentlyRunningJobs list!");
				System.exit(-1);
			}
			currentlyRunningJobs.remove(foundIndex);
			startNextIfPossible();
		}
	}

	private PrintStream logStream;

	public void log( Job job, Exception e ) {
		java.util.Date d = new java.util.Date();
		String message = d.toString() + "";
		if( job != null )
			message += " " + job;
		message += " []: " + e;
		synchronized(logStream) {
			logStream.println( message );
			logStream.flush();
		}
	}

	public void log( String s ) {
		log( null, s );
	}

	public void log( Job job, String s ) {
		java.util.Date d = new java.util.Date();
		String message = d.toString() + "";
		if( job != null )
			message += " " + job;
		message += " []: " + s;
		synchronized(logStream) {
			logStream.println( message );
			logStream.flush();
		}
	}

	public void logArguments( String [] arguments ) {
		log( "Arguments were: " );
		for( int i = 0; i < arguments.length; ++i )
			log( "arguments["+i+"] = '"+arguments[i]+"'" );
	}

	/** You can call this static method from your job's thread to
	    update the progress of the job reported by the job server.
	    This will only work if you're in the original thread that
	    was created when the job started...
	 */

	/* FIXME: this method is actually unusued: */

	/*
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
	*/
	public void exitInError( String errorMessage ) {
		exitInError( errorMessage, null );
	}

	public void exitInError( String errorMessage, Throwable exception ) {	
		log(errorMessage);
		System.out.println(errorMessage);
		if( exception != null ) {
			StackTraceElement ste[] = exception.getStackTrace();
			for( int i = 0; i < ste.length; ++i ) {
				log( "....."+ste );
			}
			exception.printStackTrace();
			System.out.println("Error was: "+exception);
		}
		System.exit(-1);
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

	Connection dbConnection = null;

	boolean updateJobsTable( ) {

		int versionToUpgradeTo = 1;

		int currentVersion = 0;
		String schemaInfoTable = "schema_information";

		try {
			// Check that the schema_information table exists:
			
			/* Following the description in "Understanding
			   JDBC Metadata" by Kyle Brown (turned up
			   randomly via Google)... */

			DatabaseMetaData dmd = dbConnection.getMetaData();
			String[] names = { "TABLE" };
			ResultSet tableNames = dmd.getTables( null, null, schemaInfoTable, names );
			boolean found = false;
			while (tableNames.next()) {
				String tableName = tableNames.getString("TABLE_NAME");
				if( tableName.equals(schemaInfoTable) ) {
					System.out.println("Found the table '"+schemaInfoTable+"'");
					found = true;
				}
			}
			if( ! found )
				System.out.println("Didn't find the table '"+schemaInfoTable+"', so current version is 0");
			if( found ) {				
				Statement statement = dbConnection.createStatement();
				ResultSet results = statement.executeQuery("SELECT version FROM "+schemaInfoTable);
				// There should only be one row:
				int rowsFetched = 0;
				int versionInTable = -1;
				while ( results.next() ) {
					++ rowsFetched;
					versionInTable = results.getInt("version");
				}
				if( rowsFetched != 1 ) {
					System.out.println("BUG: There should be exactly one row in "+schemaInfoTable);
					return false;
				}
				currentVersion = versionInTable;
				results.close();
			}

			if( currentVersion > versionToUpgradeTo ) {
				System.out.println("This code is earlier than the version in the table!");
				System.out.println("You must have used a newer version of the code at some point.");
				System.out.println("Can't downgrade in this code version as a result; exiting");
				return false;
			}

			/* So if we're here we should have an accurate
			   schema version which is <= the version we
			   can upgrade to: */

			while( currentVersion < versionToUpgradeTo ) {
				if( currentVersion == 0 ) {
					System.out.println("At version 0; creating the table to upgrade to version 1.");
					Statement statement = dbConnection.createStatement();
					statement.executeUpdate(
						"CREATE TABLE "+schemaInfoTable+" (version integer)" );
					statement.executeUpdate(
						"CREATE TABLE jobs ("+
						"id serial primary key, "+
						"status integer, "+
						"error_message text, "+
						"proportion_done float, "+
						"started_at timestamp, "+
						"ended_at timestamp, "+
						"imagej_command text, "+
						"imagej_command_options text, "+
						"memory_required_mib integer, "+
						"data_root text, "+
						"relative_directory text"+
						")" );
					int rowCount = statement.executeUpdate(
						"INSERT INTO "+schemaInfoTable+" (version) VALUES (1)" );
					if( rowCount != 1 ) {
						System.out.println("Inserting the version number failed");
					}
					// We don't want IDs to clash with ones from the old system:
					statement.executeQuery( "SELECT setval('jobs_id_seq', 1000)" );
					currentVersion = 1;
				}
			}
			return true;
		} catch( SQLException se ) {
			System.out.println("Got an SQLException while trying to upgrade the table:");
			se.printStackTrace();
			return false;
		}
	}

	private void reply( PrintStream out, String reply ) {
		log("Sending reply: "+reply);
		out.print(reply);
	}

	Runtime runtime;

	public void run(String ignore) {

		// Make sure there's only one Job_Server running in this JVM:
		synchronized( Job_Server.class ) {
			if( instance == null ) {
				instance = this;
			} else {
				System.out.println("There already seems to be a Job_Server running in this JVM");
				System.exit(-1);
			}
		}

		// Open the log file:
		logFilename = System.getProperty("logfilename");
		if( logFilename == null )
			logFilename = "/tmp/job-server.log";
		System.out.println("Using the log file: "+logFilename);

		logStream = null;
		try {
			logStream = new PrintStream( new FileOutputStream( logFilename, true ) );
		} catch( IOException e ) {
			System.out.println("Couldn't open log file.");
			System.exit(-1);
		}

		// Make sure that the PostgreSQL JDBC driver is loaded:
		try {
			Class.forName("org.postgresql.Driver");
		} catch( ClassNotFoundException e ) {
			exitInError( "Couldn't find the PostgreSQL JDBC driver - is it installed?", e );
		}

		// Open and parse the configuration file:
		configurationFilename = System.getProperty("configurationfilename");
		if( configurationFilename == null )
			configurationFilename = "/etc/default/imagej-job-server";
		log("Using the configuration file: "+configurationFilename);

		Hashtable configurationHash = parseConfigurationFile( configurationFilename );
		if( configurationHash == null )
			exitInError( "Parsing the configuration file failed" );

		String dbUser = (String)configurationHash.get("DB_USER");
		String dbPassword = (String)configurationHash.get("DB_PASSWORD");
		String dbName = (String)configurationHash.get("DB_NAME");

		if( dbUser == null || dbPassword == null )
			exitInError( "Either the DB_USER or DB_PASSWORD parameters were not set in "+configurationFilename );

		// Try opening a connection to the database:
		dbConnection = null;
		try {
			dbConnection = DriverManager.getConnection(
				"jdbc:postgresql:"+dbName,
				dbUser,
				dbPassword
				);
		} catch( SQLException se ) {

			String seString = se.toString();

			/* This isn't a very robust way of spotting
			   this error, but it's worth trying for a
			   slightly better error message */

			if( seString.indexOf("database \"fijijobs\" does not exist") >= 0 )
				exitInError( "You need to create the fijijobs database, see README" );
			else
				exitInError( "Got an SQLException while trying to connect to the fijijobs database" );
		}

		// See what version of the schema is there, and
		// upgrade it if necessary:
		if( ! updateJobsTable() )
			exitInError( "Updating the table failed" );

		runtime = Runtime.getRuntime();
		int processors = runtime.availableProcessors();
		maxJobs = processors + 1;

		log( "This system has "+processors+" processors." );

		/* Now we need to reload any queue from the database: */

		log( "Loading queued, working and unknown jobs back into the queue." );

		jobQueue = new LinkedList<Job>();
		currentlyRunningJobs = new LinkedList<Job>();
		try {
			PreparedStatement ps = dbConnection.prepareStatement(
				"SELECT * FROM jobs WHERE status = ? OR status = ? OR status < 0 ORDER by started_at" );
			ps.setInt( 1, Job.WORKING );
			ps.setInt( 2, Job.QUEUED );
			ResultSet resultSet = ps.executeQuery();
			while( resultSet.next() ) {
				Job newJob = Job.recreateFromDBResult( this, resultSet );
				log("Got newJob: "+newJob);
				newJob.setStatus( Job.QUEUED );
				newJob.setProportionDone( 0 );
				synchronized( jobQueue ) {
					jobQueue.addLast(newJob);
				}
			}
		} catch( SQLException se ) {
			exitInError( "There was an SQLException while reloading job queue from the DB" );
		}

		for( int i = 0; i < jobQueue.size(); ++i ) {
			log("jobQueue["+i+"]: "+jobQueue.get(i));
		}

		/* We'll need this prepared statement every time we
		   look for a job with a particular ID: */
		PreparedStatement psJobFromID = null;
		try {
			psJobFromID = dbConnection.prepareStatement(
				"SELECT * FROM jobs WHERE id = ?" );
		} catch( SQLException se ) {
			exitInError( "There was an SQLException while preparing the job query statement" );
		}

		InetAddress bindAddress = null;

		try {
			bindAddress = InetAddress.getByName(bindInterfaceIP);
		} catch( UnknownHostException e ) {
			exitInError( "Unknown host: "+bindInterfaceIP );
		}
		ServerSocket serverSocket = null;

		try {
			serverSocket = new ServerSocket(tcpPort,32,bindAddress);
		} catch (IOException e) {
			exitInError( "Could not listen on port: "+tcpPort );
		}

		String dataRoot = (String)configurationHash.get("IMAGEJ_SERVER_DATA_ROOT");
		if( dataRoot == null )
			exitInError("IMAGEJ_SERVER_DATA_ROOT didn't seem to be defined in the configuration file.");

		File dataRootFile = new File(dataRoot);
		if( ! dataRootFile.isDirectory() )
			exitInError("The defined IMAGEJ_SERVER_DATA_ROOT ("+dataRoot+") doesn't seem to be a directory");

		String sharedSecret = (String)configurationHash.get("IMAGEJ_SERVER_SECRET");
		if( sharedSecret == null )
			exitInError("Couldn't find the shared secret (key IMAGEJ_SERVER_SECRET) in the configuration file");

		startNextIfPossible();

		for(;;) {

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

				reply(out,"hello\t"+challenge+"\r\n");

				String nextLine = in.readLine();
				if( nextLine == null ) {
					log("nextLine was null when waiting for response to challenge from client");
					clientSocket.close();
					continue;
				}

				String [] arguments = nextLine.split("\\t");
				logArguments( arguments );

				if( arguments.length != 2 ) {
					log("Wrong number of fields ('"+arguments.length+"') in response to challenge - full line was: '"+nextLine+"'");
					reply(out,"denied\r\n");
					clientSocket.close();
					continue;
				}

				if( ! arguments[0].equals("auth") ) {
					log("Should have got an 'auth' in response to challenge - full line was: '"+nextLine+"'");
					reply(out,"denied\r\n");
					clientSocket.close();
					continue;
				}

				if( ! validChallengeResponse(challenge,sharedSecret,arguments[1]) ) {
					log("Crypted secret failed to match: '"+nextLine+"'");
					reply(out,"denied\r\n");
					clientSocket.close();
					continue;
				}

				log("Authentication succeeded, continuing...");
				reply(out,"success\r\n");

				nextLine = in.readLine();
				if( nextLine == null ) {
					log("nextLine was null when waiting for command from the client");
					clientSocket.close();
					continue;
				}

				arguments = nextLine.split("\\t");
				logArguments( arguments );

				if( arguments.length >= 2 ) {

					if( "start".equals(arguments[0]) ) {

						String imageJCommand = arguments[1];
						boolean allowed = false;
						for( int i = 0; i < allowedCommands.length; ++i )
							if( allowedCommands[i].equals(allowedCommands[i]) )
								allowed = true;
						if( ! allowed ) {
							String errorMessage = "Unsupported ImageJ command requested: '"+
								imageJCommand+"'";
							log(errorMessage);
							reply(out,"failed\t"+errorMessage+"\r\n");
							continue;
						}
						String imageJCommandOptions = arguments[2];
						String relativeOutputDirectory = arguments[3];
						// Check that there are not ".." elements in the path:
						if( relativeOutputDirectory.indexOf("..") >= 0 ) {
							String errorMessage = "The output directory may attempt to move up: '"+
								relativeOutputDirectory+"'";
							log(errorMessage);
							reply(out,"failed\t"+errorMessage+"\r\n");
							continue;
						}
						String absoluteOutputDirectory = dataRoot + File.separator + relativeOutputDirectory;
						File absoluteOutputDirectoryFile = new File(absoluteOutputDirectory);
						if( ! absoluteOutputDirectoryFile.isDirectory() ) {
							String errorMessage = "The formed absolute output directory didn't seem to be a directory: '"+absoluteOutputDirectory+"'";
							log(errorMessage);
							reply(out,"failed\t"+errorMessage+"\r\n");
							continue;
						}
						String estimatedMaxMemoryMiBString = arguments[4];
						int estimatedMaxMemoryMiB = -1;
						try {
							estimatedMaxMemoryMiB = Integer.parseInt(estimatedMaxMemoryMiBString);
						} catch( NumberFormatException e ) {
							String errorMessage = "Malformed memory requirement: '"+
								estimatedMaxMemoryMiBString+"'";
							log(errorMessage);
							reply(out,"failed\t"+errorMessage+"\r\n");
							continue;
						}
						/* Add a fudge factor of 5% for object headers, etc. + 2 MiB
						   for the hedge */
						int bumpedEstimatedMaxMemoryMiB = (int)Math.ceil( (float)estimatedMaxMemoryMiB * 1.05f + 2 );
						log("Bumped estimated memory from "+estimatedMaxMemoryMiB+"MiB to "+bumpedEstimatedMaxMemoryMiB+"MiB");

						long runtimeMaxMemoryMiB = runtime.maxMemory() / (1024 * 1024);
						if( bumpedEstimatedMaxMemoryMiB >runtimeMaxMemoryMiB ) {
							String errorMessage = "Too little memory to run this job: "+
								bumpedEstimatedMaxMemoryMiB+"MiB is greater than the"+
								" JVM's max memory ("+runtimeMaxMemoryMiB+"MiB)";
							log(errorMessage);
							reply(out,"failed\t"+errorMessage+"\r\n");
							continue;
						}

						imageJCommandOptions += " directory=[" + absoluteOutputDirectory + "]";
						imageJCommandOptions += " dataroot=[" + dataRoot + "]";

						Job newJob = null;
						try {
							newJob = new Job( this,
									  imageJCommand,
									  imageJCommandOptions,
									  bumpedEstimatedMaxMemoryMiB,
									  dataRoot,
									  relativeOutputDirectory );

							newJob.setStatus(Job.QUEUED);

						} catch( SQLException se ) {
							String errorMessage = "There was an SQL exception while creating the new job";
							log(errorMessage);
							log("SQLException was: "+se);
							se.printStackTrace();
							reply(out,"failed\t"+errorMessage+"\r\n");
							continue;
						} 

						System.out.println("Going to get jobID");						
						int jobID = newJob.getJobID();
						System.out.println("Got jobID: "+jobID);

						synchronized( jobQueue ) {
							jobQueue.addLast(newJob);
							startNextIfPossible( );
						}

						log("Java job ID will be: "+jobID);
						reply(out,"started\t"+jobID+"\r\n");

					} else if( "query".equals(arguments[0]) ) {

						String jobIDString = arguments[1];
						int jobID = -1;

						try {
							jobID = Integer.parseInt(jobIDString);
						} catch( NumberFormatException nfe ) {
							log("Malformed jobIDString: "+jobIDString);
							reply(out,"unknown\tJob ID '"+jobIDString+"' not found\r\n");
							clientSocket.close();
							continue;
						}

						Job foundJob = null;

						try {
							psJobFromID.setInt( 1, jobID );
							ResultSet resultSet = psJobFromID.executeQuery();
							int rowsFound = 0;
							resultSet.next();
							foundJob = Job.recreateFromDBResult( this, resultSet );

						} catch( SQLException se ) {
							System.out.println("Got an SQLException while trying to find an exiting job:");
							se.printStackTrace();
							System.out.println("Error was: "+se);
							reply(out,"unknown\tError while looking for job ID "+jobIDString+"\r\n");
							clientSocket.close();
							continue;
						}

						int status=foundJob.getStatus();
						if( status == Job.WORKING ) {
							String p="";
							float proportionDone = foundJob.getProportionDone();
							if( proportionDone >= 0 ) {
								p = "" + proportionDone;
							}
							reply(out,"working\t"+p+"\r\n");
						} else if( status == Job.FAILED ) {
							String errorMessage = foundJob.getErrorMessage();
							if( errorMessage == null )
								errorMessage = "";
							reply(out,"failed\t"+errorMessage+"\r\n");
						} else if( status == Job.FINISHED ) {
							reply(out,"finished\r\n");
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
							reply(out,"queued\t"+placeInQueue+"\r\n");
						} else {
							reply(out,"error\tUnknown job status found: "+status+"\r\n");
						}

					} else {
						reply(out,"error\tUnknown action "+arguments[0]+"\r\n");
					}

				} else {
					if( arguments.length == 1 ) {
						reply(out,"error\tNot enough arguments (only "+arguments.length+": first was '"+arguments[0]+"')\r\n");	
					} else {
						reply(out,"error\tNot enough arguments (only "+arguments.length+")\r\n");
					}
				}

				clientSocket.close();

			} catch( IOException e ) {
				System.out.println("There was an IOException with connection "+clientSocket+ ": "+e);
				continue;
			}
		}
	}
}
