package ledgerdb.scraper.institution.pcfinancial;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import ledgerdb.scraper.ScraperDriverBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.Select;

public class ScraperDriver extends ScraperDriverBase {

    private static final Logger logger = LogManager.getLogger();
    
    @Override
    public void run() {
        FirefoxDriver driver = new FirefoxDriver();
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
        
        String url = "https://www.txn.banking.pcfinancial.ca/";
        logger.debug("Connecting to " + url);
        driver.get(url);        
        
        // Online Banking Sign In
        
        driver.findElement(By.xpath("//h1[text()='Online Banking Sign In']"));
        logger.debug("Online Banking Sign In");
        
        WebElement e1, e2;
        e1 = driver.findElement(By.xpath("//label[contains(.,'Card Number:')]"));
        assert "Card Number:".equals(e1.getText());
        
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
        assert driver.getTitle().startsWith("Account Summary - ");
        assert driver.getTitle().endsWith("- Online Banking");
        logger.debug("Account Summary");
        
        // Deposit Accounts
        e1 = driver.findElement(By.xpath("//table[@class='DEPOSIT']"));
        List<WebElement> accountList = e1.findElements(By.xpath("tbody/tr"));
        assert accountList.size() > 0;
        logger.debug("Got " + accountList.size() + " deposit accounts");
        
        for (int i = 0; i < accountList.size(); i++) {
            logger.debug("Processing account " + i + " out of " + accountList.size());
            
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
            logger.info("Account name: " + accountName);
            
            String reference = accountName.replaceFirst(".*\\(([0-9]+)\\).*", "$1");
            assert reference.matches("^[0-9]+$");
            logger.info("Reference: " + reference);

            List<WebElement> uiAlertList = driver.findElements(By.xpath("//ui-alert/div[@class='ui-text']"));
            if (uiAlertList.size() > 0) {
                // "There are no transactions found that meet your request."
                logger.info("Alert: " + uiAlertList.get(0).getText());
                logger.info("Skipping account " + reference + " because of the alert");
                continue;
            }
            // Past Transactions
            e1 = driver.findElement(By.xpath("//section[contains(@class,'transaction-list')]//table"));
            List<WebElement> trList = e1.findElements(By.xpath(".//tr"));
            assert trList.size() > 0;
            assert "Date Transactions Funds out Funds in Running Balance".equals(trList.get(0).getText());
            logger.debug("Got " + trList.size() + " transactions");

            List<StatementInfo> siList = new ArrayList<>(trList.size());

            final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MMM d, yyyy");
            trList.stream().skip(1).forEach(tr -> {
                int j = siList.size() + 1;
                logger.debug("Parsing transaction " + j + " out of " + (trList.size() - 1));
                
                List<WebElement> tdList = tr.findElements(By.xpath("./td"));
                assert tdList.size() == 5;
                assert tdList.get(0).getAttribute("class").equals("date");
                assert tdList.get(1).getAttribute("class").equals("transactions");
                assert tdList.get(2).getAttribute("class").equals("debit");
                assert tdList.get(3).getAttribute("class").equals("credit");
                assert tdList.get(4).getAttribute("class").equals("balance");

                StatementInfo si = new StatementInfo();
                si.reference = reference;

                LocalDate date = LocalDate.parse(tdList.get(0).getText(), dtf);
                si.date = date.format(DateTimeFormatter.ISO_LOCAL_DATE);

                si.description = tdList.get(1).getText();

                String dr = tdList.get(2).getText();
                String cr = tdList.get(3).getText();
                assert dr.equals("") != cr.equals("");

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

                assert amount.matches("^\\$[\\d,]+(\\.\\d\\d)?$");
                si.amount = new BigDecimal(amount.replaceAll("[^\\d.]", ""));
                if (sign < 0)
                    si.amount = si.amount.negate();

                si.source = "";

                si.sequence = (int)siList.stream()
                        .filter(si2 -> si2.equals(si))
                        .count()
                        + 1;

                merge(si);
                siList.add(si);
                
                logger.debug("Done merged transaction " + j);
            }); // forEach
        } // for

        logger.debug("Logging out...");
        e1 = driver.findElement(By.xpath("//button[text()='sign out']/.."));
        e1.click();

        driver.findElement(By.xpath("//h1[text()='You have signed off']"));
        
        driver.quit();
        logger.debug("Done");
    }

}
