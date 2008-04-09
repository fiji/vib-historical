package server;

public interface JobProgressCallback {

    public void recordStartTime( );

    public void reportProgress( float proportionDone );

}
