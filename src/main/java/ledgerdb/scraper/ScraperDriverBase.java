package ledgerdb.scraper;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import ledgerdb.scraper.dto.StatementDTO;
import ledgerdb.scraper.util.Sleeper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

public abstract class ScraperDriverBase implements Runnable, AutoCloseable {

    private static final Logger logger = LogManager.getLogger();
    
    protected static final Sleeper SLEEPER = new Sleeper();
    
    protected SiteInfo siteInfo;
    protected RemoteWebDriver driver;
    
    private InstanceInfo instanceInfo;
    protected ServerSession serverSession;
    
    void setSiteInfo(SiteInfo siteInfo) {
        this.siteInfo = siteInfo;
    }
    
    void setInstanceInfo(InstanceInfo instanceInfo) {
        this.instanceInfo = instanceInfo;
    }
    
    void init() {
        this.driver = new FirefoxDriver();
        this.serverSession = new ServerSession(instanceInfo, siteInfo.institution);
    }
    
    @Override
    public void close() throws Exception {
        System.out.println();
        logger.info(String.format("%d processed, %d inserted",
                serverSession.getCountProcessed(),
                serverSession.getCountInserted()));
        if (driver != null) {
            driver.quit();
            driver = null;
            logger.debug("WebDriver has been closed");
        }
    }
}