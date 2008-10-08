/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

package server;

import java.lang.reflect.Method;
import java.util.Date;
import java.io.*;
import java.sql.*;

import ij.IJ;

/*
     If the macro launched via this class completes without throwing
     an exception, it is considered to have succeeded:
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

	/* This private constructor is only used by the static methods for
	   creating jobs */

	private Job( ) { }

	/* If we want to create a job and add it to the database, we
	   use this constructor.  If we want to create an object
	   representing a job that is already in the database, we use
	   one of the static methods below. */

	Job( Job_Server jobServer,
	     String imageJCommand,
	     String imageJCommandOptions,
	     int memoryRequiredMiB,
	     String dataRoot,
	     String relativeOutputDirectory ) throws SQLException {

		setImageJParameters( imageJCommand, imageJCommandOptions );
		jobServer.log("In constructor of job to run macro expression: "+macroExpression);
		this.jobServer = jobServer;
		this.jobID = -1;
	        setImageJParameters( imageJCommand, imageJCommandOptions );
		this.macroExpression = macroExpression;
		this.status = -1;
		this.dataRoot = dataRoot;
		this.relativeOutputDirectory = relativeOutputDirectory;
		int insertedID = -1;
		// On creating a new job, we should add it to the
		// database and find the ID:
		synchronized (jobServer) {
			jobServer.dbConnection.setAutoCommit(false);
			try {
				PreparedStatement ps = jobServer.dbConnection.prepareStatement(
					"INSERT INTO jobs ("+
					"status, "+
					"proportion_done, "+
					"started_at, "+
					"imagej_command, "+
					"imagej_command_options, "+
					"memory_required_mib, "+
					"data_root, "+
					"relative_directory) VALUES (?,?,CURRENT_TIMESTAMP,?,?,?,?,?)" );
				ps.setInt    ( 1, -1 );
				ps.setFloat  ( 2, -1 );
				ps.setString ( 3, imageJCommand );
				ps.setString ( 4, imageJCommandOptions );
				ps.setInt    ( 5, memoryRequiredMiB );
				ps.setString ( 6, dataRoot );
				ps.setString ( 7, relativeOutputDirectory );
				ps.executeUpdate();
				Statement s = jobServer.dbConnection.createStatement();
				ResultSet resultSet = s.executeQuery( "SELECT currval('jobs_id_seq')" );
				resultSet.next();
				insertedID = resultSet.getInt(1);
				jobServer.dbConnection.commit();
			} catch( SQLException se ) {
				jobServer.dbConnection.rollback();
			} finally {
				jobServer.dbConnection.setAutoCommit(true);
			}
		}
		this.jobID = insertedID;
	}

	static Job recreateFromDBResult( Job_Server jobServer, ResultSet resultSet ) throws SQLException {

		Job newJob = new Job();

		newJob.jobServer = jobServer;

		newJob.jobID = resultSet.getInt( "id" );
		newJob.status = resultSet.getInt( "status" );
		newJob.errorMessage = resultSet.getString( "error_message" );
		newJob.proportionDone = resultSet.getFloat( "proportion_done" );
		newJob.startedAt = resultSet.getDate( "started_at" );
		newJob.endedAt = resultSet.getDate( "ended_at" );
		newJob.setImageJParameters( resultSet.getString( "imagej_command" ),
					    resultSet.getString( "imagej_command_options" ) );
		newJob.memoryRequiredMiB = resultSet.getInt( "memory_required_mib" );
		newJob.dataRoot = resultSet.getString( "data_root" );
		newJob.relativeOutputDirectory = resultSet.getString( "relative_directory" );

		return newJob;
	}

	public void setImageJParameters( String imageJCommand, String imageJCommandOptions ) {
		this.imageJCommand = imageJCommand;
		this.imageJCommandOptions = imageJCommandOptions;
		this.macroExpression = "run('"+imageJCommand+"','"+imageJCommandOptions+"');";
	}

	public int getJobID( ) {
		return jobID;
	}

	public void setJobID( int jobID ) {
		this.jobID = jobID;
	}

	public int getMemoryRequiredMiB( ) {
		return memoryRequiredMiB;
	}

	public int getStatus( ) {
		return status;
	}

	public void setStatus( int status ) throws SQLException {
		PreparedStatement ps = jobServer.dbConnection.prepareStatement(
			"UPDATE jobs SET status = ? WHERE id = ?" );
		ps.setInt( 1, status );
		ps.setInt( 2, this.jobID );
		ps.executeUpdate();
		this.status = status;
	}

	public String getErrorMessage( ) {
		return errorMessage;
	}

	public void setErrorMessage( String e ) throws SQLException {
		jobServer.log("---------------------------------------------------");
		jobServer.log("Setting error message to:");
		jobServer.log(e);
		PreparedStatement ps = jobServer.dbConnection.prepareStatement(
			"UPDATE jobs SET error_message = ? WHERE id = ?" );
		ps.setString ( 1, e );
		ps.setInt    ( 2, this.jobID );
		ps.executeUpdate();
		this.errorMessage = e;
		jobServer.log("---------------------------------------------------");
	}

	public float getProportionDone( ) {
		return proportionDone;
	}

	public void setProportionDone( float proportionDone ) throws SQLException {
		PreparedStatement ps = jobServer.dbConnection.prepareStatement(
			"UPDATE jobs SET proportion_done = ? WHERE id = ?" );
		ps.setFloat ( 1, proportionDone );
		ps.setInt   ( 2, this.jobID );
		ps.executeUpdate();
		this.proportionDone = proportionDone;
	}

	private int jobID;
	private String macroExpression;
	private String imageJCommand;
	private String imageJCommandOptions;
	private int memoryRequiredMiB;
	private String dataRoot;
	private String relativeOutputDirectory;
	private Job_Server jobServer;

	// Call this when you actually want the process to run...

	public void run( ) {

		try {

			jobServer.log(this,"About to run: "+macroExpression);
			startedAt = new Date();
			setStatus(WORKING);

			try{
				/* Allocate a 2 MiB hedge, just in case
				   that helps in case of OOME */
				byte hedge[] = new byte[2*1024*1024];
				IJ.runMacro( macroExpression );
			} catch( Throwable t ) {
				/* FIXME: Now we're checkpointing
				   status to disk, we don't have to
				   recover from OOME, but should still
				   record the failure... */
				final StringWriter w = new StringWriter();
				final PrintWriter pw = new PrintWriter(w);
				t.printStackTrace(pw);
				pw.close();
				errorMessage = t.toString();
				jobServer.log(this,"IJ.runMacro(\" "+macroExpression+"\"); failed with: "+t);
				jobServer.log(this,"Stack Trace: "+w.toString());
				setErrorMessage(""+t);
				setStatus(FAILED);
				jobServer.jobCompleted(this);
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
