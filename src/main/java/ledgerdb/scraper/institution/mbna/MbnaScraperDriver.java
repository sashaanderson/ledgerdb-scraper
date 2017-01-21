package ledgerdb.scraper.institution.mbna;

import static com.google.common.base.Preconditions.checkState;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ledgerdb.scraper.ScraperDriverBase;
import ledgerdb.scraper.dto.StatementDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class MbnaScraperDriver extends ScraperDriverBase {

    private static final Logger logger = LogManager.getLogger();
    
    @Override
    public void run() {
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
        
        logger.debug("Connecting to " + siteInfo.url);
        driver.get(siteInfo.url);
        SLEEPER.sleepBetween(4, 7, TimeUnit.SECONDS);
        
        logIn();
        
        List<WebElement> a = driver.findElements(By.xpath("//a[@title='Link to Account Snapshot']"));
        checkState(a.size() == 2);
        checkState(a.get(0).isDisplayed() == false);
        checkState(a.get(1).isDisplayed() == true);
        
        String reference = a.get(1).getText();
        Pattern pattern = Pattern.compile("ending in (\\d+)");
        Matcher matcher = pattern.matcher(reference);
        checkState(matcher.find());
        reference = matcher.group(1);
        logger.debug("Reference: " + reference);
        
        int accountId = super.getAccountId(reference);
        
        a.get(1).click();
        
        // Snapshot
        
        driver.findElement(By.xpath("//h3[@id='recentActivitySummary']"));
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
            
            a = rows.get(i).findElements(By.xpath("./td"));
            checkState(a.size() == 5);
            
            StatementDTO s = new StatementDTO();
            s.setAccountId(accountId);
            
            if (a.get(1).getText().isEmpty() && "TEMP".equals(a.get(3).getText())) {
                logger.debug("Skipped TEMP transaction at row " + i);
                continue;
            }
            s.setDate(a.get(1).getText(), "MM/dd/yyyy");
            
            s.setDescription(a.get(2).getText());
            
            String amount = a.get(4).getText();
            checkState(amount.matches("^\\$[\\d,]+(\\.\\d\\d)?$"));
            //TODO what if refund?
            amount = amount.replaceAll("[^\\d.]", "");
            amount = "-" + amount; // negate
            s.setAmount(new BigDecimal(amount));
            
            merge(s);

            logger.debug("Done merged transaction " + i);
        }
        
        logOut();
    }
    
    private void logIn() {
        WebElement input;
        input = driver.findElement(By.xpath("//input[@id='usernameInput']"));
        for (int i = 1; i <= 5; i++) {
            if (input.isDisplayed()) break;
            SLEEPER.sleepBetween(1, 2, TimeUnit.SECONDS);
            input = driver.findElement(By.xpath("//input[@id='usernameInput']")); //XXX
        }
        input.sendKeys(siteInfo.logon);
        SLEEPER.sleepBetween(1, 2, TimeUnit.SECONDS);
        input = driver.findElement(By.xpath("//input[@id='passwordInput']"));
        input.sendKeys(siteInfo.password);
        SLEEPER.sleepBetween(1, 2, TimeUnit.SECONDS);
        
        input = driver.findElement(By.xpath("//input[@id='login' and @value='Login' and @type='submit']"));
        input.click();
        logger.debug("Logging in...");
        
        String marker = "//div[@id='myAccounts-heading']";
        driver.findElements(By.xpath(marker));
        
        List<WebElement> a = driver.findElements(By.xpath("//div[@id='errorMessage']"));
        if (a.size() > 0) {
            logger.error("Login failed: " + a.get(0).getText());
            throw new IllegalStateException("Login failed");
        }
        
        driver.findElement(By.xpath(marker));
        logger.debug("Logged in successfully");
    }
    
    private void logOut() {
        logger.debug("Logging out...");
        WebElement a = driver.findElement(By.xpath("//a[text()='Logout']"));
        a.click();
        driver.findElement(By.xpath("//p/strong[text()='You have successfully logged out!']"));
        logger.debug("Logged out");
    }
}
