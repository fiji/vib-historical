/*
 *
 */

package server;

public class TestJob {
    
    public static void meaninglessJob( JobProgressCallback progressCallback, String [] a ) {

        String nonsenseWord = a[0];

        int wordsToPrint=4;
        
        for(int i=0;i<wordsToPrint;++i) {
            progressCallback.reportProgress(i/(float)wordsToPrint);
            System.out.println(nonsenseWord+i);
            try {
                Thread.sleep(3*1000);
            } catch( InterruptedException e ) { }
        }
        
        progressCallback.reportProgress(1.0f);
        
    }

    public static void extractDataToTSV( JobProgressCallback progressCallback, String [] a ) {




    }



}
