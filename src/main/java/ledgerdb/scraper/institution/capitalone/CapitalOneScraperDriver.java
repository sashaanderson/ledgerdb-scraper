package ledgerdb.scraper.institution.capitalone;

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
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.Select;

public class CapitalOneScraperDriver extends ScraperDriverBase {

    private static final String INSTITUTION = "capitalone";
    
    private static final Logger logger = LogManager.getLogger();
    
    private final RemoteWebDriver driver;
    private final ServerSession serverSession;
    
    @Inject
    public CapitalOneScraperDriver(
            RemoteWebDriver driver,
            ServerSession serverSession) {
        this.driver = driver;
        this.serverSession = serverSession;
    }
    
    @Override
    public void scrape() {
        Sleeper.sleepBetween(10, 20, TimeUnit.SECONDS);
        
        WebElement e = driver.findElement(By.xpath("//span[@id='acct0_number']"));
        String ref = e.getText();
        checkState(ref.startsWith("..."));
        ref = ref.substring(3);
        checkState(ref.matches("^[0-9]+$"));
        
        int accountId = serverSession.getAccountId(INSTITUTION, ref);
        
        // Transactions & Details
        
        e = driver.findElement(By.xpath("//a[@id='transactions_link']"));
        e.click();
        Sleeper.sleepBetween(10, 20, TimeUnit.SECONDS);
        driver.findElement(By.xpath("//h1[.='Transactions & Details']"));
        logger.debug("Transactions & Details");
        
        WebElement table = driver.findElement(By.xpath("//div[@id='postedTransactionTable']"));
        processTable(table, accountId);
    }
    
    private void processTable(WebElement table, int accountId) {
        List<WebElement> rows = table.findElements(By.xpath("./div[@role='row']"));
        logger.debug("Found " + rows.size() + " rows in Posted Transactions Table.");
        
        for (int i = 0; i < rows.size(); i++) {
            WebElement row = rows.get(i);
            List<WebElement> cells = row.findElements(By.xpath("./div[@role='gridcell']"));
            checkState(cells.size() == 5);
            
            WebElement e = cells.get(0).findElement(By.xpath("./a[.='Open Drawer']"));
            click(e);
            e = findElement(row, "./following-sibling::div//div[starts-with(@class,'info')]//span[@class='appears_as']");
            
            StatementDTO s = new StatementDTO();
            s.setAccountId(accountId);
            
            String date = cells.get(0).getText();
            date = date.replaceAll("\\s", "");
            date = date.replaceFirst("^[A-Za-z]+", ""); // Open Drawer, Close Drawer
            s.setDate(date, "M/dd/yy");
            
            //s.setDescription(cells.get(2).getText());
            s.setDescription(e.getText());
            
            //TODO - set "accountable" user id -> cells.get(3).getText()
            
            String amount = cells.get(4).getText();
            checkState(amount.matches("^-?\\$[\\d,]+\\.\\d\\d$"));
            amount = amount.replaceAll("[^-\\d.]", "");
            s.setAmount(new BigDecimal(amount).negate());

            serverSession.merge(s);
            logger.debug("Done merged transaction " + (i + 1) + " out of " + rows.size());
        }
    }
    
    @Override
    protected void logIn(String logon, String password) {
        Sleeper.sleepBetween(10, 20, TimeUnit.SECONDS);
        driver.findElement(By.xpath("//label[.='Sign In']"));
        logger.debug("Sign In");
        
        WebElement input;
        
        input = driver.findElement(By.xpath("//input[@id='username']"));
        input.sendKeys(logon);
        input = driver.findElement(By.xpath("//input[@id='password']"));
        input.sendKeys(password);
        
        input = driver.findElement(By.xpath("//button[.='Sign In']"));
        input.click();
        logger.debug("Logging in...");
        
        //TODO: check error message, if login failed
        driver.findElement(By.xpath("//h1/span[starts-with(text(),'Welcome')]"));
        
        logger.debug("Logged in");
        super.logIn();
    }
    
    @Override
    protected void logOut() {
        logger.debug("Logging out...");
        
        WebElement link = driver.findElement(By.xpath("//a[.='Sign Out']"));
        link.click();
        driver.findElement(By.xpath("//h1[.=\"You've logged out of online banking.\"]"));
        
        logger.debug("Logged out");
        super.logOut();
    }
    
    private void click(WebElement e) {
        driver.executeScript("window.scrollTo(0, arguments[0].getBoundingClientRect().top + window.pageYOffset - (window.innerHeight / 2))", e);
        e.click();
    }
    
    private WebElement findElement(WebElement parent, String xpath) {
        for (int i = 0; i < 10; i++) {
            List<WebElement> a = parent.findElements(By.xpath(xpath));
            if (!a.isEmpty()) break;
            Sleeper.sleepBetween(1, 1, TimeUnit.SECONDS);
        }
        return parent.findElement(By.xpath(xpath));
    }
}
