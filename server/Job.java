/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

package server;

import java.lang.reflect.Method;
import java.util.Date;
import java.io.*;

import ij.IJ;

/*
   You must be careful with the type of macro expression that you run
   via this mechanism.  If it completes without throwing an exception,
   it is considered to have succeeded.  The 

 */

public class Job extends Thread {

	public final static int WORKING   = 0;
	public final static int FAILED    = 1;
	public final static int FINISHED  = 2;
	public final static int QUEUED    = 3;	

	private int status;
	private String errorMessage = null;
	private float proportionDone = -1;

	Date startedAt = null;
	Date endedAt = null;

	Job( Job_Server jobServer, String macroExpression ) {
		this.jobServer = jobServer;
		this.jobID = -1;
		this.macroExpression = macroExpression;
		this.status = -1;
	}

	public int getJobID( ) {
		return jobID;
	}

	public void setJobID( int jobID ) {
		this.jobID = jobID;
	}

	public int getStatus( ) {
		return status;
	}

	public void setStatus( int status ) {
		this.status = status;
	}
	
	public String getErrorMessage( ) {
		return errorMessage;
	}

	public void setErrorMessage( String e ) {
		this.errorMessage = e;
	}

	public float getProportionDone( ) {
		return proportionDone;
	}

	public void setProportionDone( float proportionDone ) {
		this.proportionDone = proportionDone;
	}

	private int jobID;
	private String macroExpression;
	private Job_Server jobServer;
	
	// Call this when you actually want the process to run...
	
	public void run( ) {
		
		try {            
			
			jobServer.log(this,"About to run: "+macroExpression);
			startedAt = new Date();
			setStatus(WORKING);

			try{
				IJ.runMacro( macroExpression );
			} catch( Throwable t ) {
				final StringWriter w = new StringWriter();
				final PrintWriter pw = new PrintWriter(w);
				t.printStackTrace(pw);
				pw.close();
				errorMessage = t.toString();
				jobServer.log(this,"IJ.runMacro(\" "+macroExpression+"\"); failed with: "+t);
				jobServer.log(this,"Stack Trace: "+w.toString());
				setStatus(FAILED);
				return;
			}
			endedAt = new Date();
			setStatus(FINISHED);
			jobServer.jobCompleted(this);
			jobServer.log(this,"IJ.runMacro(\" "+macroExpression+"\"); finished.");

		} catch( Exception e ) {
			jobServer.log(this,e);
		}
		
	}
	
	public String toString( ) {
		return "[" + jobID + "] " + macroExpression;
	}
	
}
