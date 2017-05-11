package ledgerdb.scraper.institution.capitalone;

import static com.google.common.base.Preconditions.checkState;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import ledgerdb.scraper.ScraperDriverBase;
import ledgerdb.scraper.ServerSession;
import ledgerdb.scraper.dto.StatementDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
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
        WebElement e = driver.findElement(By.xpath("//div[@id='user_info']"));
        String ref = e.getText();
        Pattern pattern = Pattern.compile("Account Ending In: (\\d+)");
        Matcher matcher = pattern.matcher(ref);
        checkState(matcher.find());
        ref = matcher.group(1);
        checkState(ref.matches("^[0-9]+$"));

        int accountId = serverSession.getAccountId(INSTITUTION, ref);
        
        // Recent Transactions
        
        e = driver.findElement(By.xpath("//a[normalize-space(.)='View Recent Transactions']"));
        e.click();
        driver.findElement(By.xpath("//h1[.='Recent Transactions']"));
        logger.debug("Recent Transactions");
        
        WebElement table = driver.findElement(By.xpath("//table[caption='ACTIVITY SINCE LAST STATEMENT']"));
        processTable(table, accountId);
        
        // Last Statement Summary

        WebElement label = driver.findElement(By.xpath("//label[.='View Activity:']"));
        String id = label.getAttribute("for"); // View
        checkState(id.matches("^\\w+$"));
        e = driver.findElement(By.id(id));
        Select select = new Select(e);
        select.selectByVisibleText("Last Statement Summary");
        WebElement input = e.findElement(By.xpath("./parent::td/parent::tr//input[@type='button' and @value='Go']"));
        input.click();
        
        table = driver.findElement(By.xpath("//table[caption='STATEMENT SUMMARY']"));
        table = driver.findElement(By.xpath("//table[caption='TRANSACTION SUMMARY']"));
        processTable(table, accountId);
    }
    
    private void processTable(WebElement table, int accountId) {
        List<WebElement> rows = table.findElements(By.xpath(".//tr"));
        checkState(rows.size() >= 1);
        
        WebElement row = rows.get(0);
        List<WebElement> cells = row.findElements(By.xpath("./child::th"));
        checkState(cells.size() == 4);
        checkState(cells.get(0).getText().trim().equals("TRANS DATE"));
        checkState(cells.get(1).getText().trim().equals("Post Date"));
        checkState(cells.get(2).getText().trim().equals("TRANSACTION DESCRIPTION"));
        checkState(cells.get(3).getText().trim().equals("AMOUNT"));
        
        for (int i = 1; i < rows.size(); i++) {
            row = rows.get(i);
            if (rows.size() == 2 &&
                    row.getText().startsWith("No transactions available")) {
                logger.debug(row.getText());
                break;
            }
            
            cells = row.findElements(By.xpath("./child::td"));
            checkState(cells.size() == 4);
            
            StatementDTO s = new StatementDTO();
            s.setAccountId(accountId);
            s.setDate(cells.get(0).getText(), "dd/MM/yyyy");
            s.setDescription(cells.get(2).getText());
            
            String amount = cells.get(3).getText();
            checkState(amount.matches("^-?\\$[\\d,]+\\.\\d\\d$"));
            amount = amount.replaceAll("[^-\\d.]", "");
            s.setAmount(new BigDecimal(amount).negate());

            serverSession.merge(s);
            logger.debug("Done merged transaction " + i + " out of " + (rows.size() - 1));
        }
    }
    
    @Override
    protected void logIn(String logon, String password) {
        driver.findElement(By.xpath("//h1[.='Please Login']"));
        logger.debug("Please Login");
        
        WebElement input;
        String id;
        
        input = driver.findElement(By.xpath("//label[.='Login ID']"));
        id = input.getAttribute("for");
        input = driver.findElement(By.xpath("//input[@id='" + id + "']"));
        input.sendKeys(logon);
        
        input = driver.findElement(By.xpath("//label[.='Password']"));
        id = input.getAttribute("for");
        input = driver.findElement(By.xpath("//input[@id='" + id + "']"));
        input.sendKeys(password);
        
        input = driver.findElement(By.xpath("//input[@type='button' and @value='Login']"));
        input.click();
        logger.debug("Logging in...");
        
        driver.findElement(By.xpath("//h1[.='Current Account Status']"));
        //TODO: check error message, if login failed
        
        logger.debug("Logged in");
        super.logIn();
    }
    
    @Override
    protected void logOut() {
        logger.debug("Logging out...");
        
        WebElement link = driver.findElement(By.xpath("//img[@alt='logoff']/parent::a"));
        link.click();
        driver.findElement(By.xpath("//h1[.=\"You're logged out\"]"));
        
        logger.debug("Logged out");
        super.logOut();
    }
}
