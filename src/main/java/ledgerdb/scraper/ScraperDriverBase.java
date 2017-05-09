package ledgerdb.scraper;

public abstract class ScraperDriverBase implements Runnable, AutoCloseable {

    protected boolean loggedIn = false;
    
    protected void logIn() { this.loggedIn = true; }
    protected void logOut() { this.loggedIn = false; }
    
    @Override
    public final void close() throws Exception {
        if (loggedIn)
            logOut();
    }
}