package ledgerdb.scraper.institution.mbna;

import static com.google.common.base.Preconditions.checkState;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import ledgerdb.scraper.ScraperDriverBase;
import ledgerdb.scraper.ServerSession;
import ledgerdb.scraper.dto.StatementDTO;
import ledgerdb.scraper.util.Sleeper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

public class MbnaScraperDriver extends ScraperDriverBase {

    private static final String INSTITUTION = "mbna";
    
    private static final Logger logger = LogManager.getLogger();
    
    private final RemoteWebDriver driver;
    private final ServerSession serverSession;
    
    @Inject
    public MbnaScraperDriver(
            RemoteWebDriver driver,
            ServerSession serverSession) {
        this.driver = driver;
        this.serverSession = serverSession;
    }
    
    @Override
    public void scrape() {
        List<WebElement> a = driver.findElements(By.xpath("//a[@title='Link to Account Snapshot']"));
        WebElement link = null;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).isDisplayed()) {
                link = a.get(i);
                break;
            }
        }
        checkState(link != null);
        
        String reference = link.getText();
        Pattern pattern = Pattern.compile("ending in (\\d+)");
        Matcher matcher = pattern.matcher(reference);
        checkState(matcher.find());
        reference = matcher.group(1);
        logger.debug("Reference: " + reference);
        
        int accountId = serverSession.getAccountId(INSTITUTION, reference);
        
        link.click();
        
        // Snapshot
        
        logger.debug("Snapshot");
        driver.findElements(By.xpath("//h1[normalize-space(.)='Snapshot']"));
        driver.findElement(By.xpath("//h3[@id='recentActivitySummary']"));
        scrapeTransactionTable(accountId);
        
        // Statements
        
        logger.debug("Statements");
        WebElement e = driver.findElement(By.xpath("//li[@id='tab-statements']"));
        e.click();
        driver.findElement(By.xpath("//h1[normalize-space(.)='Statements']"));
        driver.findElement(By.xpath("//h3[normalize-space(.)='Statement activity']"));
        e = driver.findElement(By.xpath("//div[normalize-space(./strong)='Statement closing date:']"));
        e = e.findElement(By.xpath("./following-sibling::div"));
        logger.debug("Statement closing date: " + e.getText());
        scrapeTransactionTable(accountId);
    }
    
    @Override
    protected void logIn(String logon, String password) {
        Sleeper.sleepBetween(4, 7, TimeUnit.SECONDS);
        
        WebElement input;
        input = driver.findElement(By.xpath("//input[@id='usernameInput']"));
        for (int i = 1; i <= 5; i++) {
            if (input.isDisplayed()) break;
            driver.navigate().refresh(); // XXX
            input = driver.findElement(By.xpath("//input[@id='usernameInput']")); //XXX
        }
        input.sendKeys(logon);
        Sleeper.sleepBetween(1, 2, TimeUnit.SECONDS);
        input = driver.findElement(By.xpath("//input[@id='passwordInput']"));
        input.sendKeys(password);
        Sleeper.sleepBetween(1, 2, TimeUnit.SECONDS);
        
        input = driver.findElement(By.xpath("//input[@id='login' and @value='Login' and @type='submit']"));
        input.click();
        logger.debug("Logging in...");
        
        // //p/strong[normalize-space(.)='Identity not recognized']
        // //p starts-with "Please enter the answer to your challenge question"
        // <label id="MFAChallengeForm:question" for="MFAChallengeForm:answer">
        // What is your favourite bakery?</label>
        // <input id="MFAChallengeForm:answer" type="password" ...
        String marker = "//h1[normalize-space(.)='My Accounts']";
        driver.findElements(By.xpath(marker));
        
        List<WebElement> a = driver.findElements(By.xpath("//div[@id='errorMessage']"));
        if (a.size() > 0) {
            logger.error("Login failed: " + a.get(0).getText());
            throw new IllegalStateException("Login failed");
        }
        
        driver.findElement(By.xpath(marker));
        
        logger.debug("Logged in successfully");
        super.logIn();
    }
    
    @Override
    protected void logOut() {
        logger.debug("Logging out...");
        WebElement a = driver.findElement(By.xpath("//a[text()='Logout']"));
        a.click();
        driver.findElement(By.xpath("//p/strong[text()='You have successfully logged out!']"));
        logger.debug("Logged out");
        super.logOut();
    }
    
    private void scrapeTransactionTable(int accountId) {
        WebElement e = driver.findElement(By.xpath("//table[@id='transactionTable']"));
        
        List<WebElement> rows = e.findElements(By.xpath(".//tr"));
        checkState(rows.size() > 0);
        logger.debug("Got " + (rows.size() - 1) + " transactions");
        
        List<WebElement> head = rows.get(0).findElements(By.xpath(".//th"));
        checkState(head.size() == 5);
        checkState(head.get(0).getText().replaceAll("\\s+", " ").startsWith("Transaction date"));
        checkState(head.get(1).getText().replaceAll("\\s+", " ").startsWith("Posting date"));
        checkState(head.get(2).getText().startsWith("Description"));
        checkState(head.get(3).getText().replaceAll("\\s+", " ").startsWith("Reference number"));
        checkState(head.get(4).getText().startsWith("Amount"));

        for (int i = 1; i < rows.size(); i++) {
            logger.debug("Parsing transaction " + i + " out of " + (rows.size() - 1));
            
            List<WebElement> a = rows.get(i).findElements(By.xpath("./td"));
            checkState(a.size() == 5);
            
            StatementDTO s = new StatementDTO();
            s.setAccountId(accountId);
            
            if (a.get(1).getText().isEmpty() && "TEMP".equals(a.get(3).getText())) {
                logger.debug("Skipped TEMP transaction at row " + i);
                continue;
            }
            s.setDate(a.get(1).getText(), "MM/dd/yyyy");
            
            s.setDescription(a.get(2).getText().trim());
            
            String amount = a.get(4).getText();
            checkState(amount.matches("^-?\\$[\\d,]+(\\.\\d\\d)?$"));
            amount = amount.replaceAll("[^-\\d.]", "");
            s.setAmount(new BigDecimal(amount).negate());
            
            serverSession.merge(s);

            logger.debug("Done merged transaction " + i);
        }
    }
}
