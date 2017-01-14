package ledgerdb.scraper.institution.mbna;

import java.util.concurrent.TimeUnit;
import ledgerdb.scraper.ScraperDriverBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import static com.google.common.base.Preconditions.checkState;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScraperDriver extends ScraperDriverBase {

    private static final Logger logger = LogManager.getLogger();
    
    @Override
    public void run() {
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
        
        logger.info("Connecting to " + siteInfo.url);
        driver.get(siteInfo.url);
        
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
        logger.info("Reference: " + reference);
        
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

        //TODO: extract sequence counter to ScraperDriverBase
        List<StatementDTO> ss = new ArrayList<>(rows.size());
        
        final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        for (int i = 1; i < rows.size(); i++) {
            logger.debug("Parsing transaction " + i + " out of " + (rows.size() - 1));
            
            a = rows.get(i).findElements(By.xpath("./td"));
            checkState(a.size() == 5);
            
            StatementDTO s = new StatementDTO();
            s.accountId = accountId;
            
            if (a.get(1).getText().isEmpty() && "TEMP".equals(a.get(3).getText())) {
                logger.debug("Skipped TEMP transaction at row " + i);
                continue;
            }
            LocalDate date = LocalDate.parse(a.get(1).getText(), dtf);
            s.date = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            
            s.description = a.get(2).getText();
            
            String amount = a.get(4).getText();
            checkState(amount.matches("^\\$[\\d,]+(\\.\\d\\d)?$"));
            amount = amount.replaceAll("[^\\d.]", "");
            s.amount = new BigDecimal(amount);
            s.amount = s.amount.negate();
            
            s.sequence = (int)ss.stream()
                    .filter(si2 -> si2.equals(s))
                    .count()
                    + 1;
            merge(s);
            ss.add(s);

            logger.debug("Done merged transaction " + i);
        }
        
        logOut();
    }
    
    private void logIn() {
        WebElement input;
        input = driver.findElement(By.xpath("//input[@id='usernameInput']"));
        input.sendKeys(siteInfo.logon);
        input = driver.findElement(By.xpath("//input[@id='passwordInput']"));
        input.sendKeys(siteInfo.password);
        Sleeper.sleepBetween(2, 5, TimeUnit.SECONDS);
        input.sendKeys(Keys.ENTER);
        logger.debug("Logging in...");
    }
    
    private void logOut() {
        logger.debug("Logging out...");
        WebElement a = driver.findElement(By.xpath("//a[text()='Logout']"));
        a.click();
        driver.findElement(By.xpath("//p/strong[text()='You have successfully logged out!']"));
        logger.debug("Logged out");
    }
}
