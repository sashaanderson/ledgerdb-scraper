package ledgerdb.scraper;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;

public abstract class ScraperDriverBase implements Runnable, AutoCloseable {

    private static final Logger logger = LogManager.getLogger();
    
    protected SiteInfo siteInfo;
    protected WebDriver driver;
    
    private InstanceInfo instanceInfo;
    private Client client;
    
    private int countProcessed = 0, countInserted = 0;
    private List<StatementDTO> processedStatements = new ArrayList<>();
    
    void setSiteInfo(SiteInfo siteInfo) {
        this.siteInfo = siteInfo;
    }
    
    void setInstanceInfo(InstanceInfo instanceInfo) {
        this.instanceInfo = instanceInfo;
    }
    
    void init() {
        driver = new FirefoxDriver();
        
        client = ClientBuilder.newClient();
        HttpAuthenticationFeature feature
                = HttpAuthenticationFeature.basic(
                        instanceInfo.username,
                        instanceInfo.password);
        client.register(feature);
    }
    
    protected int getAccountId(String reference) {
        WebTarget target = client.target(instanceInfo.url)
                .path("institution_link")
                .path(siteInfo.institution)
                .path(reference);
        Response r = target.request(MediaType.APPLICATION_JSON_TYPE)
                .get();
        checkStatus(r);
        
        InstitutionLinkDTO i = r.readEntity(InstitutionLinkDTO.class);
        return i.accountId;
    }
    
    protected void merge(StatementDTO s) {
        s.sequence = (int)processedStatements.stream()
                .filter(si2 -> si2.equals(s))
                .count()
                + 1;

        WebTarget target = client.target(instanceInfo.url).path("statement");
        
        Response r = target.request(MediaType.TEXT_PLAIN)
                .post(Entity.entity(s, MediaType.APPLICATION_JSON_TYPE));
        checkStatus(r);
        
        String body = r.readEntity(String.class);
        logger.debug("Server response: " + body);
        Preconditions.checkState(body.matches("^\\d+$"));
        
        processedStatements.add(s);
        countProcessed++;
        if (!body.equals("0"))
            countInserted++;
    }
    
    private void checkStatus(Response r) {
        String message = r.getStatus() + " " + r.getStatusInfo().getReasonPhrase();
        if (r.getStatus() == 200) { // 200 OK
            logger.debug(message);
        } else {
            logger.error(message);
            try {
                String body = r.readEntity(String.class);
                throw new IllegalStateException("Server request failed: " + body);
            } catch (Exception e) {
                throw new IllegalStateException("Server request failed");
            }
        }
    }
    
    @Override
    public void close() throws Exception {
        logger.info(String.format("%d processed, %d inserted", countProcessed, countInserted));
        if (driver != null) {
            driver.quit();
            driver = null;
            logger.debug("WebDriver has been closed");
        }
    }
    
    protected static class Sleeper {
        private Sleeper() {}
        
        public static void sleepBetween(int minUnit, int maxUnit, TimeUnit unit) {
            long minTime = TimeUnit.MILLISECONDS.convert(minUnit, unit);
            long maxTime = TimeUnit.MILLISECONDS.convert(minUnit, unit);
            long time = minTime + (long)(Math.random() * (maxTime - minTime));
            try {
                Thread.sleep(time);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    protected class StatementDTO {
        public StatementDTO() {}
        
        @JsonProperty("statementDate")
        public String date;
        
        @JsonProperty("accountId")
        public int accountId;
        
        public BigDecimal amount;
        public String description;
        
        public final String source = ""; //TODO - remove column from db
        
        int sequence;
        public int getSequence() { return sequence; }
        
        public boolean equals(StatementDTO si) {
            return Arrays.stream(getClass().getFields())
                    .filter(field -> !"sequence".equals(field.getName()))
                    .allMatch(field -> {
                        Object o1, o2;
                        try {
                            o1 = field.get(this);
                            o2 = field.get(si);
                        } catch (IllegalAccessException e) {
                            throw new IllegalStateException(e); // should not happen
                        }
                        return Objects.equal(o1, o2);
                    });
        }
    }
    
    private static class InstitutionLinkDTO {
        
        public String institution;
        
        public String reference;
        
        public int accountId;
        
    }
}