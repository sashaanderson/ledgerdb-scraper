package ledgerdb.scraper;

import java.util.concurrent.TimeUnit;
import ledgerdb.scraper.util.Sleeper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

public abstract class ScraperDriverBase implements Runnable, AutoCloseable {

    private static final Logger logger = LogManager.getLogger();
    
    protected static final Sleeper SLEEPER = new Sleeper();
    
    protected SiteInfo siteInfo;
    protected RemoteWebDriver driver;
    
    private InstanceInfo instanceInfo;
    protected ServerSession serverSession;
    
    protected boolean loggedIn = false;
    
    void setSiteInfo(SiteInfo siteInfo) {
        this.siteInfo = siteInfo;
    }
    
    void setInstanceInfo(InstanceInfo instanceInfo) {
        this.instanceInfo = instanceInfo;
    }
    
    void init() {
        serverSession = new ServerSession(instanceInfo, siteInfo.institution);
        
        driver = new FirefoxDriver();
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
    }
    
    protected void logIn() { this.loggedIn = true; }
    protected void logOut() { this.loggedIn = false; }
    
    @Override
    public final void close() throws Exception {
        System.out.println(); // TODO - move println to ServerSession.close(?)
        logger.info(String.format("%d processed, %d inserted",
                serverSession.getCountProcessed(),
                serverSession.getCountInserted()));
        
        if (loggedIn)
            logOut();
        
        if (driver != null) {
            driver.quit();
            driver = null;
            logger.debug("WebDriver has been closed");
        }
    }
}