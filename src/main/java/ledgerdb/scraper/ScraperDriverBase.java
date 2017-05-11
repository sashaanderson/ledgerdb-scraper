package ledgerdb.scraper;

public abstract class ScraperDriverBase implements AutoCloseable {

    protected boolean loggedIn = false;
    
    protected void logIn() { this.loggedIn = true; }
    protected void logOut() { this.loggedIn = false; }
    
    protected abstract void logIn(String logon, String password);
    protected abstract void scrape();
    
    @Override
    public final void close() throws Exception {
        if (loggedIn)
            logOut();
    }
}