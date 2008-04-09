package server;

public interface JobInterface {

    public void start( JobProgressCallback progressCallback,
                       String [] arguments );

}
