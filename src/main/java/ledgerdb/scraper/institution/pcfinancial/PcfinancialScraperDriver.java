package ledgerdb.scraper.institution.pcfinancial;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import ledgerdb.scraper.ScraperDriverBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Preconditions.checkState;

public class PcfinancialScraperDriver extends ScraperDriverBase {

    private static final Logger logger = LogManager.getLogger();
    
    private boolean loggedIn = false;
    
    @Override
    public void run() {
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
        
        logger.debug("Connecting to " + siteInfo.url);
        driver.get(siteInfo.url);        
        
        logIn();
        
        WebElement e1, e2;
        
        // Deposit Accounts
        e1 = driver.findElement(By.xpath("//table[@class='DEPOSIT']"));
        List<WebElement> accountList = e1.findElements(By.xpath("tbody/tr"));
        checkState(accountList.size() > 0);
        logger.debug("Got " + accountList.size() + " deposit accounts");
        
        for (int i = 0; i < accountList.size(); i++) {
            logger.debug("Processing account " + (i + 1) + " out of " + accountList.size());
            
            if (i > 0) {
                e1 = driver.findElement(By.xpath("//a[text()='Account Summary']"));
                //Sleeper.sleepBetween(10, 20, TimeUnit.SECONDS); //XXX
                e1.click();

                e1 = driver.findElement(By.xpath("//table[@class='DEPOSIT']"));
                accountList = e1.findElements(By.xpath("tbody/tr"));
            }
            if (i >= accountList.size()) break;
        
            e2 = accountList.get(i).findElement(By.xpath(".//a"));
            Sleeper.sleepBetween(2, 5, TimeUnit.SECONDS);
            e2.click();
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
                // "There are no transactions found that meet your request."
                logger.info("Alert: " + uiAlertList.get(0).getText());
                logger.info("Skipping account " + reference + " because of the alert");
                continue;
            }
            
            int accountId = getAccountId(reference);

            // Past Transactions
            e1 = driver.findElement(By.xpath("//section[contains(@class,'transaction-list')]//table"));
            List<WebElement> trList = e1.findElements(By.xpath(".//tr"));
            checkState(trList.size() > 0);
            checkState("Date Transactions Funds out Funds in Running Balance".equals(trList.get(0).getText()));
            logger.debug("Got " + trList.size() + " transactions");

            final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MMM d, yyyy");
            for (int j = 1; j < trList.size(); j++) {
                WebElement tr = trList.get(j);
                logger.debug("Parsing transaction " + j + " out of " + (trList.size() - 1));
                
                List<WebElement> tdList = tr.findElements(By.xpath("./td"));
                checkState(tdList.size() == 5);
                checkState(tdList.get(0).getAttribute("class").equals("date"));
                checkState(tdList.get(1).getAttribute("class").equals("transactions"));
                checkState(tdList.get(2).getAttribute("class").equals("debit"));
                checkState(tdList.get(3).getAttribute("class").equals("credit"));
                checkState(tdList.get(4).getAttribute("class").equals("balance"));

                StatementDTO s = new StatementDTO();
                //s.reference = reference;
                s.accountId = accountId;

                LocalDate date = LocalDate.parse(tdList.get(0).getText(), dtf);
                s.date = date.format(DateTimeFormatter.ISO_LOCAL_DATE);

                s.description = tdList.get(1).getText();

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
                s.amount = new BigDecimal(amount.replaceAll("[^\\d.]", ""));
                if (sign < 0)
                    s.amount = s.amount.negate();

                merge(s);
                
                logger.debug("Done merged transaction " + j);
            } // for
        } // for

        logOut();
    }
    
    private void logIn() {
        // Online Banking Sign In
        
        driver.findElement(By.xpath("//h1[text()='Online Banking Sign In']"));
        logger.debug("Online Banking Sign In");
        
        WebElement e1, e2;
        e1 = driver.findElement(By.xpath("//label[contains(.,'Card Number:')]"));
        checkState("Card Number:".equals(e1.getText()));
        
        e2 = e1.findElement(By.xpath("following::input"));
        e2.sendKeys(siteInfo.logon);
        
        e1 = driver.findElement(By.xpath("//label[contains(.,'Password:')]"));
        e2 = e1.findElement(By.xpath("following::input"));
        e2.sendKeys(siteInfo.password);
        Sleeper.sleepBetween(2, 5, TimeUnit.SECONDS);
        e2.sendKeys(Keys.ENTER);
        logger.debug("Logging in...");
        // LOADING...
        
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
    
    private void logOut() {
        logger.debug("Logging out...");
        WebElement e1 = driver.findElement(By.xpath("//button[text()='sign out']/.."));
        e1.click();

        driver.findElement(By.xpath("//h1[text()='You have signed off']"));
        this.loggedIn = false;
    }

    @Override
    public void close() throws Exception {
        if (loggedIn) logOut();
        super.close();
    }

}
