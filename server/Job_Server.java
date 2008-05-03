/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

package server;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Hashtable;
import java.net.*;
import java.io.*;

import ij.ImageJ;
import ij.plugin.PlugIn;

/* This is a class that provides a TCP server for queuing and starting
 * jobs in an instance of ImageJ.  An objection to this might be that
 * ImageJ already binds to localhost and starts commands in a similar
 * fashion.  However, it has no facility for controlling the number of
 * jobs that may run concurrently or querying the progress of jobs. */

/* To send a command to the server, you write a tab separated line
 * terminated by \r\n.  The server will send back a tab separated line
 * terminated by \r\n.

 * The first field in the command indicates the action:

     "start":

         - The second field must be a macro expression, typically
           "run('Watever Plugin','example=foo');"

         - A single line is returned which contains "started" in the
           first, and in the second field the job ID specific to this
           server.

     "query"

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

 */

public class Job_Server implements PlugIn {
	
	static private final String logFilename = "/tmp/job-server.log";    
	
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
	
	public void run(String ignore) {
		
		logStream = null;
		try {
			logStream = new PrintStream(logFilename);
		} catch( IOException e ) {
			System.out.println("Couldn't open log file.");
			System.exit(-1);
		}
		
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
			
				String nextLine = in.readLine();
			
				String [] arguments = nextLine.split("\\t");
				
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
						
						out.println("started\t"+jobID);
					
					} else if( "query".equals(arguments[0]) ) {
						
						String jobIDString = arguments[1];
						int jobID = -1;
						
						try {
							jobID = Integer.parseInt(jobIDString);
						} catch( NumberFormatException nfe ) {
							out.println("unknown\tJob ID '"+jobIDString+"' not found");
						}
						
						Job j=null;
						
						try {
							j = allJobs.get(jobID);
						} catch( IndexOutOfBoundsException e ) {
							out.println("unknown\tJob ID '"+jobIDString+"' not found");
							clientSocket.close();
						}						
						
						int status=j.getStatus();
						if( status == Job.WORKING ) {
							String p="";
							float proportionDone = j.getProportionDone();
							if( proportionDone >= 0 ) {
								p = "" + proportionDone;
							}
							out.println("unknown\t"+p+"\r\n");
						} else if( status == Job.FAILED ) {
							String errorMessage = j.getErrorMessage();
							if( errorMessage == null )
								errorMessage = "";
							out.println("failed\t"+errorMessage+"\r\n");
						} else if( status == Job.FINISHED ) {
							out.println("finished\r\n");
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
							out.println("queued\t"+placeInQueue+"\r\n");
						} else {
							out.println("error\tUnknown job status found: "+status);
						}
						
						
					} else {                        
						out.println("error\tUnknown action "+arguments[0]+"\r\n");                        
					}
					
				} else {
					out.println("error\tNot enough arguments (only "+arguments.length+")\r\n");
				}
			
				clientSocket.close();

			} catch( IOException e ) {
				System.out.println("There was an IOException with connection "+clientSocket+ ": "+e);
				continue;
			}
						
			
		}
		
	}
	
}
