package ledgerdb.scraper.institution.simplii;

import static com.google.common.base.Preconditions.checkState;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import ledgerdb.scraper.ScraperDriverBase;
import ledgerdb.scraper.ServerSession;
import ledgerdb.scraper.dto.StatementDTO;
import ledgerdb.scraper.util.Sleeper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.Select;

public class SimpliiScraperDriver extends ScraperDriverBase {
    
    private static final String INSTITUTION = "simplii";
    
    private static final Logger logger = LogManager.getLogger();
    
    private final RemoteWebDriver driver;
    private final ServerSession serverSession;
    
    @Inject
    public SimpliiScraperDriver(
            RemoteWebDriver driver,
            ServerSession serverSession) {
        this.driver = driver;
        this.serverSession = serverSession;
    }
    
    @Override
    public void scrape() {
        scrapeTable("DEPOSIT");
        scrapeTable("NON_REGISTERED_INVESTMENT");
        scrapeTable("REGISTERED_INVESTMENT");
        scrapeTable("CREDIT");
    }
    
    private void scrapeTable(String className) {
        WebElement e1, e2;
        
        e1 = driver.findElement(By.xpath("//a[text()='Account Summary']"));
        click(e1);
        
        logger.debug("Scraping table " + className);
        e1 = driver.findElement(By.xpath("//table[@class='" + className + "']"));
        List<WebElement> accountList = e1.findElements(By.xpath("tbody/tr"));
        
        if (accountList.size() == 1
                && accountList.get(0).getText().contains("You don't have any")) {
            logger.debug("You don't have any");
            return;
        }
        
        checkState(accountList.size() > 0);
        logger.debug("Got " + accountList.size() + " accounts in " + className);
        
        for (int i = 0; i < accountList.size(); i++) {
            logger.debug("Processing account " + (i + 1) + " out of " + accountList.size());
            
            if (i > 0) {
                e1 = driver.findElement(By.xpath("//a[text()='Account Summary']"));
                click(e1);

                e1 = driver.findElement(By.xpath("//table[@class='" + className + "']"));
                accountList = e1.findElements(By.xpath("tbody/tr"));
            }
            if (i >= accountList.size()) break;
        
            e2 = accountList.get(i).findElement(By.xpath(".//a"));
            click(e2);
            // LOADING

            // Account Details
            
            driver.findElement(By.xpath("//header/h1[text()='Account Details']"));
            logger.debug("Account Details");
            
            e1 = driver.findElement(By.xpath("//div[@class='account-selector']//select"));
            Select sel = new Select(e1);
            String accountName = sel.getFirstSelectedOption().getText();
            logger.debug("Account name: " + accountName);
            
            String reference = accountName.replaceFirst(".*\\(([0-9]+)\\).*", "$1");
            checkState(reference.matches("^[0-9]+$"));
            logger.debug("Reference: " + reference);
            
            List<WebElement> uiAlertList = driver.findElements(By.xpath("//ui-alert/div[@class='ui-text']"));
            if (uiAlertList.size() > 0) {
                String text = uiAlertList.get(0).getText();
                Level level;
                if (text.startsWith("There are no transactions found that meet your request")) {
                    level = Level.DEBUG;
                } else {
                    level = Level.WARN;
                }
                logger.log(level, "Alert says, \"" + text + "\"");
                logger.log(level, "Skipping account " + reference + " because of the alert");
                continue;
            }
            
            int accountId = serverSession.getAccountId(INSTITUTION, reference);

            // Past Transactions
            e1 = driver.findElement(By.xpath("//section[contains(@class,'transaction-list')]//table"));
            List<WebElement> trList = e1.findElements(By.xpath(".//tr"));
            checkState(trList.size() >= 2);
            checkState("Transaction List".equals(trList.get(0).getText()));
            checkState("Date Transactions Funds out Funds in Running Balance".equals(trList.get(1).getText()));
            logger.debug("Got " + (trList.size() - 2) + " transactions");

            for (int j = 2; j < trList.size(); j++) {
                WebElement tr = trList.get(j);
                logger.debug("Parsing transaction " + (j - 1) + " out of " + (trList.size() - 2));
                
                List<WebElement> tdList = tr.findElements(By.xpath("./td"));
                checkState(tdList.size() == 5);
                checkState(tdList.get(0).getAttribute("class").equals("date"));
                checkState(tdList.get(1).getAttribute("class").equals("transactions"));
                checkState(tdList.get(2).getAttribute("class").equals("debit"));
                checkState(tdList.get(3).getAttribute("class").equals("credit"));
                checkState(tdList.get(4).getAttribute("class").equals("balance"));

                StatementDTO s = new StatementDTO();
                s.setAccountId(accountId);

                s.setDate(tdList.get(0).getText(), "MMM d, yyyy");

                s.setDescription(tdList.get(1).getText());

                String dr = tdList.get(2).getText();
                String cr = tdList.get(3).getText();
                checkState(dr.equals("") != cr.equals(""));

                String amount;
                int sign;
                if (cr.equals("")) {
                    // Funds out
                    sign = -1;
                    amount = dr;
                } else {
                    // Funds in
                    sign = +1;
                    amount = cr;
                }

                checkState(amount.matches("^\\$[\\d,]+(\\.\\d\\d)?$"));
                amount = amount.replaceAll("[^\\d.]", "");
                if (sign < 0)
                    amount = "-" + amount;
                s.setAmount(new BigDecimal(amount));

                serverSession.merge(s);
                
                logger.debug("Done merged transaction " + j);
            } // for
        } // for
    }
    
    @Override
    protected void logIn(String logon, String password) {
        List<WebElement> buttons = driver.findElements(By.xpath("//button"));
        WebElement e = buttons.stream()
                .filter(button -> button.getText().equals("Sign on"))
                .findFirst().get();
        e.click();
        
        e = driver.findElement(By.xpath("//input[@id='card-number-']"));
        e.sendKeys(logon);
        
        e = driver.findElement(By.xpath("//input[@id='password-']"));
        e.sendKeys(password);
        Sleeper.sleepBetween(2, 5, TimeUnit.SECONDS);
        e.sendKeys(Keys.ENTER);
        logger.debug("Logging in...");
        
        // Account Summary
        // Welcome, MR XXX XX. Customer number: 0001234567.
        // Last visit: MMM dd, yyyy at HH:mm p.m. ET.
        
        driver.findElement(By.xpath("//header/h1[text()='Account Summary']"));
        driver.findElement(By.xpath("//section[@class='user-info']"));
        checkState(driver.getTitle().startsWith("Account Summary - "));
        checkState(driver.getTitle().endsWith("- Online Banking"));
        logger.debug("Account Summary");
        
        this.loggedIn = true;
    }
    
    @Override
    protected void logOut() {
        logger.debug("Logging out...");
        WebElement e1 = driver.findElement(By.xpath("//button[text()='sign out']/.."));
        click(e1);

        driver.findElement(By.xpath("//h1[text()='You have signed off']"));
        this.loggedIn = false;
    }
    
    private void click(WebElement e) {
        Sleeper.sleepBetween(10, 20, TimeUnit.SECONDS);
        
        driver.executeScript("arguments[0].scrollIntoView()", e);
        
        Actions actions = new Actions(driver);
        actions.moveToElement(e).click().build().perform();
    }
}
